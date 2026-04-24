package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.BidirectionalMappingEngine;
import com.syntaric.openfhir.mapping.custommappings.CustomMapping;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.metrics.MappingMetricsLogger;
import com.syntaric.openfhir.metrics.MappingTimer;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Enumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.syntaric.openfhir.fc.FhirConnectConst.*;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX_ESCAPED;

@Slf4j
@Component
public class ToOpenEhrMappingEngine extends BidirectionalMappingEngine {

    final private FhirPathR4 fhirPath;
    final private OpenFhirStringUtils stringUtils;
    final private OpenEhrPopulator openEhrPopulator;
    final private OpenFhirMapperUtils openFhirMapperUtils;
    final private ToOpenEhrNullFlavour toOpenEhrNullFlavour;
    final private CustomMappingRegistry customMappingRegistry;
    final private MappingMetricsLogger metricsLogger;

    @Autowired
    public ToOpenEhrMappingEngine(final FhirPathR4 fhirPath,
                                  final OpenFhirStringUtils stringUtils,
                                  final OpenEhrPopulator openEhrPopulator,
                                  final OpenFhirMapperUtils openFhirMapperUtils,
                                  final ToOpenEhrNullFlavour toOpenEhrNullFlavour,
                                  final CustomMappingRegistry customMappingRegistry,
                                  final MappingMetricsLogger metricsLogger) {
        super(fhirPath);
        this.fhirPath = fhirPath;
        this.stringUtils = stringUtils;
        this.openEhrPopulator = openEhrPopulator;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.toOpenEhrNullFlavour = toOpenEhrNullFlavour;
        this.customMappingRegistry = customMappingRegistry;
        this.metricsLogger = metricsLogger;
    }

    public JsonObject mapToOpenEhr(final List<MappingHelper> mappingHelpers,
                                   final JsonObject finalFlat,
                                   final Base dataPoint,
                                   final boolean firstWalkOverModelMapping,
                                   final Map<String, Integer> indexByHierarchyPath) {

        final String openEhrHierarchySplitFlatPath = mappingHelpers.get(0).getOpenEhrHierarchySplitFlatPath();
        int relevantIndex = indexByHierarchyPath.getOrDefault(openEhrHierarchySplitFlatPath, 0);

        if (firstWalkOverModelMapping && !fhirPreconditionPasses(mappingHelpers.get(0).getPreprocessorFhirConditions(),
                dataPoint)) {
            return finalFlat;
        }

        final MappingTimer helpersTimer = MappingTimer.start();

        boolean somethingWasAdded = false;
        for (final MappingHelper helper : mappingHelpers) {
            if (!shouldProcessMapping(helper, UNIDIRECTIONAL_TOOPENEHR)) {
                continue;
            }
            if (helper.getFullOpenEhrFlatPath() == null) {
                continue;
            }


            if (!fhirEmptyNotEmptyPasses(helper, helper.getFhirConditions())) {
                continue;
            }

            final MappingHelper clonedHelper = helper.cloneWithFhirResourceAndRootIntact();

            final String path = setIndexAccordingToHierarchy(clonedHelper, relevantIndex);

            final String replaced = stringUtils.replacePattern(clonedHelper.getFullOpenEhrFlatPath(), path);
            clonedHelper.setFullOpenEhrFlatPath(replaced);

            fixAllChildrenRecurringElements(clonedHelper, path);

            int previousFinalFlatSize = finalFlat.size();

            doMapping(clonedHelper, finalFlat, dataPoint, indexByHierarchyPath);

            somethingWasAdded = somethingWasAdded || (finalFlat.size() > previousFinalFlatSize);
        }

        metricsLogger.record("mapToOpenEhr.helpers", openEhrHierarchySplitFlatPath, helpersTimer.elapsedMs());

        if (somethingWasAdded && firstWalkOverModelMapping) {
            indexByHierarchyPath.put(openEhrHierarchySplitFlatPath, relevantIndex + 1);
        } else {
            log.warn(
                    "Even though a Resource matched criteria, nothing was added to the openEHR composition from it: {}",
                    dataPoint.getIdBase());
        }

        return finalFlat;
    }


    private boolean fhirEmptyNotEmptyPasses(final MappingHelper mappingHelper,
                                            final List<Condition> fhirConditions) {
        if (fhirConditions == null || fhirConditions.isEmpty()) {
            return true;
        }
        boolean passes = true;
        for (final Condition fhirCondition : fhirConditions) {
            boolean mustBeEmpty = FhirConnectConst.CONDITION_OPERATOR_EMPTY.equals(fhirCondition.getOperator());
            boolean mustBePresent = FhirConnectConst.CONDITION_OPERATOR_NOT_EMPTY.equals(fhirCondition.getOperator());
            if (!mustBeEmpty && !mustBePresent) {
                continue; // not to be evaluated here
            }
            final List<String> attributes = fhirCondition.getTargetAttributes();
            for (final String attribute : attributes) {
                final String fhirPath = String.format("%s.%s", fhirCondition.getTargetRoot(), attribute);
                final Optional<Base> exists = this.fhirPath.evaluateFirst(mappingHelper.getGeneratingFhirResource(),
                        fhirPath,
                        Base.class);
                if (mustBeEmpty && exists.isPresent()) {
                    return false; // immediately return, no need to look further
                }
                if (mustBePresent && exists.isEmpty()) {
                    passes = false; // loop further, perhaps any other passes
                }
            }
            if (!passes) {
                return passes;
            }
        }
        return passes;
    }

    boolean fhirPreconditionPasses(final List<Condition> condition,
                                   final Base resource) {
        if (condition == null) {
            return true;
        }
        final String limitingCriteriaBasedOnCoverCondition = getLimitingCriteria(condition);
        boolean negate = FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(condition.get(0).getOperator());

        // apply limiting factor
        final boolean exists = fhirPath.evaluateFirst(resource, limitingCriteriaBasedOnCoverCondition,
                Base.class).isPresent();
        return negate != exists;
    }

    private String getLimitingCriteria(final List<Condition> preConditions) {
        final StringJoiner andJoiner = new StringJoiner(" and ");
        for (final Condition preCondition : preConditions) {
            final StringJoiner orJoiner = new StringJoiner(" and ");
            for (final String criteria : preCondition.getCriterias()) {
                orJoiner.add(String.format("where((%s).toString().contains('%s'))",
                        preCondition.getTargetAttribute(),
                        criteria));
            }
            andJoiner.add(orJoiner.toString());
        }
        return andJoiner.toString();
    }

    /**
     * Resolves the concrete flat path for one occurrence of the hierarchy-split element.
     *
     * <p>Three cases:
     * <ol>
     *   <li><b>No split path</b> – replace the last {@code [n]} in {@code fullOpenEhrFlatPath}
     *       with {@code :i} (or return it unchanged if there is no {@code [n]}).</li>
     *   <li><b>Split path set, path is outside its hierarchy</b> – the full path does not share
     *       the split path's static prefix, so it belongs to a different repeating group; fall back
     *       to replacing the last {@code [n]} with {@code :i}.</li>
     *   <li><b>Split path set, path is inside its hierarchy</b> – replace the last {@code [n]}
     *       inside the split-path segment with {@code :i} and collapse any remaining {@code [n]}
     *       markers to {@code :0}.</li>
     * </ol>
     */
    String setIndexAccordingToHierarchy(final MappingHelper mappingHelper, final int i) {
        final String splitPath = mappingHelper.getOpenEhrHierarchySplitFlatPath();
        final String fullPath = mappingHelper.getFullOpenEhrFlatPath();

        // Case 1 – no split path configured
        if (splitPath == null) {
            return fullPath;
        }

        // Static prefix of the split path (everything before the first [n])
        final String splitPrefix = splitPath.split(RECURRING_SYNTAX_ESCAPED)[0];

        // Case 2 – path is outside the hierarchy (doesn't share the static prefix)
        if (!fullPath.contains(splitPrefix)) {
            return replaceLastRecurring(fullPath, i);
        }

        // Case 3 – path is inside the hierarchy: index the last [n] of the split-path
        // segment, then collapse any outer [n] markers to :0
        final String indexed = stringUtils.replaceLastIndexOf(splitPath, RECURRING_SYNTAX, ":" + i);
        return indexed.replaceAll(RECURRING_SYNTAX_ESCAPED, ":0");
    }

    private String replaceLastRecurring(final String path, final int i) {
        if (!path.contains(RECURRING_SYNTAX)) {
            return path;
        }
        return stringUtils.replaceLastIndexOf(path, RECURRING_SYNTAX, ":" + i);
    }

    boolean doMapping(final MappingHelper helper, final JsonObject flatComposition, final Base iteratingBase,
                      final Map<String, Integer> indexByHierarchyPath) {
        final MappingTimer mappingTimer = MappingTimer.start();

        final String fhirPath =
                StringUtils.isEmpty(helper.getFhirWithCondition()) ? helper.getFhir() : helper.getFhirWithCondition();

        final Base toResolveOn = getToResolveOn(iteratingBase, helper);

        final List<Base> results = resolveFhirResults(helper, fhirPath, toResolveOn);
        if (results == null) {
            metricsLogger.record("doMapping",
                    "mapping=" + helper.getMappingName() + " model=" + helper.getModelMetadataName(),
                    mappingTimer.elapsedMs());
            return false; // evaluation error already logged
        }

        final boolean result;
        if (results.isEmpty() || resultsRepresentMissingPrimitiveValues(results)) {
            result = handleMissingResults(helper, flatComposition, toResolveOn, fhirPath);
        } else {
            populateOpenEhrForEachResult(helper, flatComposition, toResolveOn, results, fhirPath, indexByHierarchyPath);
            result = true;
        }

        metricsLogger.record("doMapping",
                "mapping=" + helper.getMappingName() + " model=" + helper.getModelMetadataName(),
                mappingTimer.elapsedMs());
        return result;
    }

    private Base getToResolveOn(final Base iteratingBase,
                                final MappingHelper helper) {
        if(helper.getOriginalFhirPath() == null) {
            return iteratingBase;
        }
        return helper.getOriginalFhirPath().startsWith(FHIR_RESOURCE_FC) ? helper.getGeneratingFhirResource() : iteratingBase;
    }

    /**
     * Resolves FHIR results for the given path and base resource.
     * Returns {@code null} when a path evaluation error occurs (already logged).
     */
    private List<Base> resolveFhirResults(final MappingHelper helper, final String fhirPath,
                                          final Base toResolveOn) {
        final boolean isReference = helper.getOriginalOpenEhrPath() != null && helper.getOriginalOpenEhrPath().startsWith(FhirConnectConst.REFERENCE);

        if (isReference) {
            return resolveReference(toResolveOn, helper);
        }

        if (StringUtils.isEmpty(fhirPath)
                || FhirConnectConst.FHIR_ROOT_FC.equals(helper.getOriginalFhirPath())
                || helper.isUseParentRoot()) {
            return resolveAsParentRoot(helper, fhirPath, toResolveOn);
        }

        return evaluateFhirPath(fhirPath, toResolveOn);
    }

    private List<Base> resolveAsParentRoot(final MappingHelper helper, final String fhirPath,
                                           final Base toResolveOn) {
        log.debug("Taking Base itself as fhirPath is {}", fhirPath);
        if (helper.getFhirConditions() == null) {
            return Collections.singletonList(toResolveOn);
        }
        // condition present — verify it still passes before accepting the parent root
        final Optional<Base> conditionPasses = this.fhirPath.evaluateFirst(toResolveOn, fhirPath, Base.class);
        return conditionPasses.isPresent()
                ? Collections.singletonList(toResolveOn)
                : Collections.emptyList();
    }

    private List<Base> evaluateFhirPath(final String fhirPath, final Base toResolveOn) {
        try {
            return this.fhirPath.evaluate(toResolveOn,
                    fhirPath.replace(".as(Enumeration)", ""),
                    // casting to enumeration only works when doing toFhir, else it complains it's not a valid fhir type
                    Base.class);
        } catch (final Exception e) {
            // if resolve() can't find the referenced resource, fail gracefully
            log.error("Error trying to evaluate path {}", fhirPath);
            return null;
        }
    }

    private boolean handleMissingResults(final MappingHelper helper, final JsonObject flatComposition,
                                         final Base toResolveOn, final String fhirPath) {
        final boolean handledNullFlavour = toOpenEhrNullFlavour.handleDataAbsentReasonWhenNoResult(
                helper, flatComposition, toResolveOn);
        if (handledNullFlavour) {
            return true;
        }
        log.warn("No results found for FHIRPath {}, evaluating on type: {}", fhirPath, toResolveOn.getClass());
        return false;
    }

    private void populateOpenEhrForEachResult(final MappingHelper helper, final JsonObject flatComposition,
                                              final Base toResolveOn, final List<Base> results,
                                              final String fhirPath,
                                              final Map<String, Integer> indexByHierarchyPath) {
        final String fullOpenEhrFlatPath = helper.getFullOpenEhrFlatPath();
        for (int i = 0; i < results.size(); i++) {
            final MappingHelper clonedHelper = helper.cloneWithFhirResourceAndRootIntact();
            final Base result = results.get(i);

            final String thePath = resolveIndexedPath(fullOpenEhrFlatPath, i);
            log.debug("Setting value taken with fhirPath {} from object type {}", fhirPath, toResolveOn.getClass());

            fixAllChildrenRecurringElements(clonedHelper, thePath);

            if (!OPENEHR_TYPE_NONE.equals(clonedHelper.getHardcodedType())) {
                final List<String> possibleRmTypes = clonedHelper.getPossibleRmTypes();
                if (possibleRmTypes != null && !possibleRmTypes.isEmpty()) {
                    final String deducedRmType = deduceRmType(result, possibleRmTypes); // if we can deduce RM type based on what result is and what possibleRmTypes are, we do that
                    final String openEhrPathToPopulateTo =
                            openFhirMapperUtils.removeAqlSuffix(thePath, deducedRmType)
                                    + clonedHelper.getFlatPathPipeSuffix();
                    populateValue(helper, clonedHelper, result, thePath, openEhrPathToPopulateTo, flatComposition, deducedRmType);
                } else if (StringUtils.isNotEmpty(clonedHelper.getProgrammedMapping())) {
                    // still invoke the programmed one
                    populateValue(helper, clonedHelper, result, thePath, thePath, flatComposition, null);
                }
            }

            recurseIntoChildren(clonedHelper, result, helper, flatComposition, indexByHierarchyPath);
        }
    }

    private String deduceRmType(final Base result, final List<String> possibleRmTypes) {
        if (possibleRmTypes != null && possibleRmTypes.size() == 1) {
            return possibleRmTypes.get(0);
        }
        if (result instanceof CodeableConcept) {
            if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
            if (possibleRmTypes.contains(DV_TEXT)) return DV_TEXT;
        }
        if (result instanceof Enumeration<?>) {
            if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
            if (possibleRmTypes.contains(DV_TEXT)) return DV_TEXT;
        } else if (result instanceof Coding) {
            if (possibleRmTypes.contains(CODE_PHRASE)) return CODE_PHRASE;
            if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
        } else if (result instanceof Quantity) {
            if (possibleRmTypes.contains(DV_QUANTITY)) return DV_QUANTITY;
            if (possibleRmTypes.contains(DV_COUNT)) return DV_COUNT;
            if (possibleRmTypes.contains(DV_PROPORTION)) return DV_PROPORTION;
            if (possibleRmTypes.contains(DV_ORDINAL)) return DV_ORDINAL;
        } else if (result instanceof IntegerType) {
            if (possibleRmTypes.contains(DV_COUNT)) return DV_COUNT;
            if (possibleRmTypes.contains(DV_ORDINAL)) return DV_ORDINAL;
        } else if (result instanceof DateTimeType || result instanceof InstantType) {
            if (possibleRmTypes.contains(DV_DATE_TIME)) return DV_DATE_TIME;
        } else if (result instanceof DateType) {
            if (possibleRmTypes.contains(DV_DATE)) return DV_DATE;
            if (possibleRmTypes.contains(DV_DATE_TIME)) return DV_DATE_TIME;
        } else if (result instanceof TimeType) {
            if (possibleRmTypes.contains(DV_TIME)) return DV_TIME;
        } else if (result instanceof BooleanType) {
            if (possibleRmTypes.contains(DV_BOOL)) return DV_BOOL;
        } else if (result instanceof StringType) {
            if (possibleRmTypes.contains(DV_TEXT)) return DV_TEXT;
            if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
            if (possibleRmTypes.contains(DV_DATE_TIME)) return DV_DATE_TIME;
            if (possibleRmTypes.contains(DV_DURATION)) return DV_DURATION;
        } else if (result instanceof Period) {
            if (possibleRmTypes.contains(DV_INTERVAL)) return DV_INTERVAL;
        } else if (result instanceof Range) {
            if (possibleRmTypes.contains(DV_INTERVAL)) return DV_INTERVAL;
        } else if (result instanceof Attachment) {
            if (possibleRmTypes.contains(DV_MULTIMEDIA)) return DV_MULTIMEDIA;
        } else if (result instanceof Identifier) {
            if (possibleRmTypes.contains(DV_IDENTIFIER)) return DV_IDENTIFIER;
        } else if (result instanceof Ratio) {
            if (possibleRmTypes.contains(DV_PROPORTION)) return DV_PROPORTION;
            if (possibleRmTypes.contains(DV_QUANTITY)) return DV_QUANTITY;
        }
        return null;
    }

    private String resolveIndexedPath(final String fullOpenEhrFlatPath, final int i) {
        if (fullOpenEhrFlatPath == null || !fullOpenEhrFlatPath.contains(RECURRING_SYNTAX)) {
            return fullOpenEhrFlatPath;
        }
        return stringUtils.replaceLastIndexOf(fullOpenEhrFlatPath, RECURRING_SYNTAX, ":" + i);
    }

    private void populateValue(final MappingHelper helper, final MappingHelper clonedHelper, final Base result,
                               final String thePath, final String openEhrPathToPopulateTo,
                               final JsonObject flatComposition, final String rmType) {

        long possibleRmTypes = helper.getPossibleRmTypes().size();
        if (StringUtils.isNotEmpty(clonedHelper.getManualOpenEhrValue())) {
            log.debug("Hardcoding value {} to path: {}", clonedHelper.getManualOpenEhrValue(), openEhrPathToPopulateTo);
            // is it ok we use string type here? could it be something else? probably it could be..
            openEhrPopulator.setOpenEhrValue(helper, openEhrPathToPopulateTo,
                    new StringType(clonedHelper.getManualOpenEhrValue()),
                    rmType, possibleRmTypes > 1, flatComposition, helper.getTerminology(),
                    helper.getAvailableCodings());
        } else if (StringUtils.isNotEmpty(helper.getProgrammedMapping())) {
            invokeProgrammedMapping(helper, flatComposition, result);
        } else {
            final boolean handledEventTime = applyEventTypeMappingIfNeeded(helper, result, thePath, flatComposition);
            if (!handledEventTime) {
                openEhrPopulator.setOpenEhrValue(helper, openEhrPathToPopulateTo, result, rmType,
                        possibleRmTypes > 1,
                        flatComposition, helper.getTerminology(), helper.getAvailableCodings());
            }
        }
    }

    private void recurseIntoChildren(final MappingHelper clonedHelper, final Base result,
                                     final MappingHelper parentHelper, final JsonObject flatComposition,
                                     final Map<String, Integer> indexByHierarchyPath) {
        if (clonedHelper.getChildren().isEmpty()) {
            return;
        }
        clonedHelper.getChildren().forEach(c -> {
            c.setGeneratingFhirRoot(result);
            if(c.isFollowedBy()) {
                c.setGeneratingFhirResource(parentHelper.getGeneratingFhirResource());
            } else if(clonedHelper.isHasSlot()) {
                c.setGeneratingFhirResource(result);
            } else {
                c.setGeneratingFhirResource(parentHelper.getGeneratingFhirResource());
            }
        });
        mapToOpenEhr(clonedHelper.getChildren(), flatComposition, result,
                clonedHelper.isHasSlot(), indexByHierarchyPath);
    }

    private void invokeProgrammedMapping(final MappingHelper mappingHelper,
                                         final JsonObject flatComposition,
                                         final Base result) {
        log.info("Using mapping code: {}", mappingHelper.getProgrammedMapping());

        try {
            boolean success = false;
            CustomMapping customMapping = customMappingRegistry.find(mappingHelper.getProgrammedMapping()).orElse(null);
            if (customMapping != null) {
                success = customMapping.applyFhirToOpenEhrMapping(
                        mappingHelper,
                        result,
                        mappingHelper.getPossibleRmTypes(),
                        flatComposition,
                        openEhrPopulator,
                        openFhirMapperUtils,
                        stringUtils
                );
            } else {
                // fallback to plugin extensions if present
//                PluginManager pluginManager = SpringContext.getBean(PluginManager.class);
//                List<FormatConverter> converters = pluginManager.getExtensions(FormatConverter.class);
//                if (converters.isEmpty()) {
//                    log.warn("No CustomMapping or FormatConverter found for mapping code: {}",
//                             helper.getMappingCode());
//                } else {
//                    FormatConverter converter = converters.get(0);
//                    success = converter.applyFhirToOpenEhrMapping(
//                            helper.getMappingCode(),
//                            thePath,
//                            result,
//                            helper.getOpenEhrType(),
//                            flatComposition
//                    );
//                }
            }

            if (!success) {
                log.warn("Mapping failed for code: {}", mappingHelper.getProgrammedMapping());
            }
        } catch (Exception e) {
            log.error("Error applying mapping: {}", e.getMessage(), e);
        }
    }

    private boolean applyEventTypeMappingIfNeeded(final MappingHelper helper,
                                                  final Base result,
                                                  final String openEhrPath,
                                                  final JsonObject flatComposition) {
        if (result == null || openEhrPath == null) {
            return false;
        }
        final List<String> openEhrTypes = helper.getPossibleRmTypes();
        for (final String openEhrType : openEhrTypes) {
            if (!isEventRmType(openEhrType)) {
                continue;
            }
            if (result instanceof final Period period) {
                final Date start = period.getStart();
                final Date end = period.getEnd();
                final Date time = start != null ? start : end;
                if (time != null) {
                    openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/time", new DateTimeType(time),
                            FhirConnectConst.DV_DATE_TIME, false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                }
                openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/math_function",
                        new Coding("openehr", "640", "actual"),
                        FhirConnectConst.DV_CODED_TEXT, false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                if (start != null && end != null) {
                    final java.time.Duration duration = java.time.Duration.between(
                            start.toInstant(), end.toInstant());
                    if (!duration.isNegative() && !duration.isZero()) {
                        openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/width",
                                new StringType(duration.toString()), FhirConnectConst.DV_DURATION,
                                false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                    }
                } else if (start != null) {
                    // Represent missing end as empty width for interval events
                    openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/width",
                            new StringType(""), FhirConnectConst.DV_DURATION, false, flatComposition,
                            helper.getTerminology(), helper.getAvailableCodings());
                }
                return true;
            } else if (result instanceof DateTimeType
                    || result instanceof InstantType
                    || result instanceof DateType) {
                openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/time", result,
                        FhirConnectConst.DV_DATE_TIME, false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                return true;
            } else if (result instanceof TimeType) {
                openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/time", result,
                        FhirConnectConst.DV_TIME, false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                return true;
            }
        }
        return false;
    }

    private boolean isEventRmType(final String rmType) {
        return DV_EVENT.equals(rmType) || POINT_EVENT.equals(rmType) || INTERVAL_EVENT.equals(rmType);
    }

    private List<Base> resolveReference(final Base toResolveOn,
                                        final MappingHelper mappingHelper) {
        if (toResolveOn instanceof BundleEntryComponent bundleEntryComponent) {
            return List.of(bundleEntryComponent.getResource());
        }
        if (!(toResolveOn instanceof Reference)) {
            return this.fhirPath.evaluate(toResolveOn, mappingHelper.getFhir(), Base.class);
        }
        if (mappingHelper.getOriginalOpenEhrPath().equals(FhirConnectConst.REFERENCE)) {
            return this.fhirPath.evaluate(toResolveOn, "resolve()", Base.class);
        } else {
            return null;
        }
    }

    private boolean resultsRepresentMissingPrimitiveValues(final List<Base> results) {
        if (results == null || results.isEmpty()) {
            return false;
        }
        return results.stream().allMatch(result -> {
            if (!(result instanceof PrimitiveType<?> primitiveType)) {
                return false;
            }
            return StringUtils.isBlank(primitiveType.getValueAsString());
        });
    }

    /**
     * Adds proper recurring index to all child elements if parent is the recurring one
     */
    void fixAllChildrenRecurringElements(final MappingHelper helper, final String newOne) {
        if (helper.getFullOpenEhrFlatPath() != null) {
            final boolean hasParentRecurring = stringUtils.childHasParentRecurring(helper.getFullOpenEhrFlatPath(),
                    newOne);
            if (hasParentRecurring) {
                final String replaced = stringUtils.replacePattern(helper.getFullOpenEhrFlatPath(), newOne);
                helper.setFullOpenEhrFlatPath(replaced);
            }
        }

        if (helper.getChildren() == null) {
            return;
        }
        for (MappingHelper childHelper : helper.getChildren()) {
            fixAllChildrenRecurringElements(childHelper, newOne);
        }
    }
}
