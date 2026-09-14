package com.syntaric.openfhir.mapping.toopenehr;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.BidirectionalMappingEngine;
import com.syntaric.openfhir.mapping.MappingContext;
import com.syntaric.openfhir.mapping.custommappings.CustomMapping;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.metrics.MappingMetricsLogger;
import com.syntaric.openfhir.metrics.MappingTimer;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.FhirConditionEvaluator;
import com.syntaric.openfhir.util.FhirPathEvaluationException;
import com.syntaric.openfhir.util.MappingExecutionException;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseEnumeration;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;


import static com.syntaric.openfhir.fc.FhirConnectConst.*;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX_ESCAPED;

@Slf4j
@Component
public class ToOpenEhrMappingEngine extends BidirectionalMappingEngine {

    final private FhirContextRegistry fhirContextRegistry;
    final private OpenFhirStringUtils stringUtils;
    final private OpenEhrPopulator openEhrPopulator;
    final private OpenFhirMapperUtils openFhirMapperUtils;
    final private ToOpenEhrNullFlavour toOpenEhrNullFlavour;
    final private CustomMappingRegistry customMappingRegistry;
    final private MappingMetricsLogger metricsLogger;
    final private FhirConditionEvaluator fhirConditionEvaluator;

    @Autowired
    public ToOpenEhrMappingEngine(final FhirContextRegistry fhirContextRegistry,
                                  final OpenFhirStringUtils stringUtils,
                                  final OpenEhrPopulator openEhrPopulator,
                                  final OpenFhirMapperUtils openFhirMapperUtils,
                                  final ToOpenEhrNullFlavour toOpenEhrNullFlavour,
                                  final CustomMappingRegistry customMappingRegistry,
                                  final MappingMetricsLogger metricsLogger,
                                  final FhirConditionEvaluator fhirConditionEvaluator) {
        super(fhirContextRegistry);
        this.fhirContextRegistry = fhirContextRegistry;
        this.stringUtils = stringUtils;
        this.openEhrPopulator = openEhrPopulator;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.toOpenEhrNullFlavour = toOpenEhrNullFlavour;
        this.customMappingRegistry = customMappingRegistry;
        this.metricsLogger = metricsLogger;
        this.fhirConditionEvaluator = fhirConditionEvaluator;
    }

    /**
     * Same as {@link #mapToOpenEhr(List, JsonObject, IBase, boolean, Map, Spec.Version, MappingIssueCollector)}
     * with a {@link MappingIssueCollector#failFast() fail-fast} collector: nobody reads the issues back, so the
     * first failed mapping is thrown as a {@link MappingExecutionException}.
     */
    public JsonObject mapToOpenEhr(final List<MappingHelper> mappingHelpers,
                                   final JsonObject finalFlat,
                                   final IBase dataPoint,
                                   final boolean firstWalkOverModelMapping,
                                   final Map<String, Integer> indexByHierarchyPath,
                                   final Spec.Version fhirVersion) {
        return mapToOpenEhr(mappingHelpers, finalFlat, dataPoint, firstWalkOverModelMapping, indexByHierarchyPath,
                fhirVersion, MappingIssueCollector.failFast());
    }

    public JsonObject mapToOpenEhr(final List<MappingHelper> mappingHelpers,
                                   final JsonObject finalFlat,
                                   final IBase dataPoint,
                                   final boolean firstWalkOverModelMapping,
                                   final Map<String, Integer> indexByHierarchyPath,
                                   final Spec.Version fhirVersion,
                                   final MappingIssueCollector issueCollector) {
        final Class<? extends IBase> baseClass = resolveBaseClass(fhirVersion);
        walk(mappingHelpers, finalFlat, dataPoint, firstWalkOverModelMapping, indexByHierarchyPath,
                baseClass, fhirVersion, issueCollector);
        return finalFlat;
    }

    /**
     * Walks one list of mappings (all of the same model mapper) against one FHIR element.
     *
     * <p>Every mapping ends in a {@link MappingOutcome}. When nothing was written by this walk and at least one
     * mapping ended in {@link MappingOutcome#NO_DATA} or {@link MappingOutcome#NOTHING_WRITTEN}, one warning is
     * recorded naming those mappings, their FHIR paths and the element they were evaluated on. Mappings a gate
     * rejected, mappings with nothing to write in this direction and problems already reported as their own issue
     * stay silent — that is how a Bundle fanned out over slot mappings does not produce a warning per slot that
     * was never meant to match.
     *
     * @return {@link MappingOutcome#MAPPED} when this walk wrote something, otherwise
     * {@link MappingOutcome#NOT_APPLICABLE}: anything worth reporting has been reported here, so the parent walk
     * has nothing to add for this element
     */
    private MappingOutcome walk(final List<MappingHelper> mappingHelpers,
                                final JsonObject finalFlat,
                                final IBase dataPoint,
                                final boolean firstWalkOverModelMapping,
                                final Map<String, Integer> indexByHierarchyPath,
                                final Class<? extends IBase> baseClass,
                                final Spec.Version fhirVersion,
                                final MappingIssueCollector issueCollector) {

        if (mappingHelpers == null || mappingHelpers.isEmpty()) {
            return MappingOutcome.NOT_APPLICABLE;
        }

        final String openEhrHierarchySplitFlatPath = mappingHelpers.get(0).getOpenEhrHierarchySplitFlatPath();
        int relevantIndex = indexByHierarchyPath.getOrDefault(openEhrHierarchySplitFlatPath, 0);
        final IFhirPath versionedFhirPath = fhirContextRegistry.getFhirPath(fhirVersion);

        if (firstWalkOverModelMapping && !fhirPreconditionPasses(mappingHelpers.get(0).getPreprocessorFhirConditions(),
                dataPoint, versionedFhirPath, baseClass)) {
            return MappingOutcome.NOT_APPLICABLE;
        }

        final MappingTimer helpersTimer = MappingTimer.start();

        boolean somethingWasAdded = false;
        final Map<MappingHelper, MappingOutcome> unmapped = new LinkedHashMap<>();
        for (final MappingHelper helper : mappingHelpers) {
            if (helper.getFullOpenEhrFlatPath() == null) {
                continue;
            }
            final int previousFinalFlatSize = finalFlat.size();

            // A runtime failure inside this mapping is reported with its context and the loop moves on (or, with a
            // fail-fast collector, thrown); the clone carries the hierarchy-indexed flat path, so it is the better
            // context once it exists.
            MappingHelper clonedHelper = null;
            MappingOutcome outcome = MappingOutcome.NOT_APPLICABLE;
            try {
                if (!shouldProcessMapping(helper, UNIDIRECTIONAL_TOOPENEHR, fhirVersion)) {
                    continue;
                }
                if (!fhirEmptyNotEmptyPasses(helper, helper.getFhirConditions(), versionedFhirPath, baseClass)) {
                    continue;
                }

                clonedHelper = helper.cloneWithFhirResourceAndRootIntact();

                final String path = setIndexAccordingToHierarchy(clonedHelper, relevantIndex);

                final String replaced = stringUtils.replacePattern(clonedHelper.getFullOpenEhrFlatPath(), path);
                clonedHelper.setFullOpenEhrFlatPath(replaced);

                fixAllChildrenRecurringElements(clonedHelper, path);

                outcome = doMapping(clonedHelper, finalFlat, dataPoint, indexByHierarchyPath, baseClass, fhirVersion,
                        issueCollector);
            } catch (final MappingExecutionException e) {
                // a nested loop already reported this with the innermost (most specific) context
                throw e;
            } catch (final RuntimeException e) {
                reportMappingFailure(e, clonedHelper != null ? clonedHelper : helper, UNIDIRECTIONAL_TOOPENEHR,
                        issueCollector);
                outcome = MappingOutcome.NOT_APPLICABLE; // reported as an error issue, nothing to add
            } finally {
                if (finalFlat.size() > previousFinalFlatSize) {
                    somethingWasAdded = true;
                    outcome = MappingOutcome.MAPPED;
                }
            }
            if (outcome.needsAttention() && !hasNothingToWriteToOpenEhr(helper)) {
                unmapped.put(helper, outcome);
            }
        }

        metricsLogger.record("mapToOpenEhr.helpers", openEhrHierarchySplitFlatPath, helpersTimer.elapsedMs());

        if (somethingWasAdded) {
            if (firstWalkOverModelMapping) {
                indexByHierarchyPath.put(openEhrHierarchySplitFlatPath, relevantIndex + 1);
            }
            return MappingOutcome.MAPPED;
        }
        if (unmapped.isEmpty()) {
            log.debug("None of the mappings of model mapper {} applied to the {} element; nothing to report.",
                    mappingHelpers.get(0).getModelMetadataName(), dataPoint.fhirType());
            return MappingOutcome.NOT_APPLICABLE;
        }
        final String diagnostics = describeNothingMapped(dataPoint, mappingHelpers.get(0), unmapped, fhirVersion);
        log.warn(diagnostics);
        issueCollector.addWarning(diagnostics);
        return MappingOutcome.NOT_APPLICABLE;
    }

    /**
     * A mapping that only carries a manual FHIR value (and neither a manual openEHR value nor mapping code) is a
     * constant emitted towards FHIR; in this direction it has nothing to write, so its miss is not a finding.
     */
    private static boolean hasNothingToWriteToOpenEhr(final MappingHelper helper) {
        return helper.getManualFhirValue() != null
                && StringUtils.isEmpty(helper.getManualOpenEhrValue())
                && StringUtils.isEmpty(helper.getProgrammedMapping());
    }

    /**
     * One warning per walk that ended without a value: names the model mapper, the mappings that found no data or
     * produced no value (each with its FHIR path) and the element they were evaluated on, so the reader can tell a
     * mapper bug from an element that simply holds nothing for those mappings.
     */
    private String describeNothingMapped(final IBase dataPoint, final MappingHelper first,
                                         final Map<MappingHelper, MappingOutcome> unmapped,
                                         final Spec.Version fhirVersion) {
        final String noData = mappingsWithOutcome(unmapped, MappingOutcome.NO_DATA);
        final String nothingWritten = mappingsWithOutcome(unmapped, MappingOutcome.NOTHING_WRITTEN);
        final StringBuilder reasons = new StringBuilder();
        if (!noData.isEmpty()) {
            reasons.append("Mappings that found no data at their FHIR path: ").append(noData);
        }
        if (!nothingWritten.isEmpty()) {
            if (reasons.length() > 0) {
                reasons.append("; ");
            }
            reasons.append("mappings whose data produced no openEHR value: ").append(nothingWritten);
        }
        return String.format(
                "A %s resource matched the mapping criteria but nothing could be mapped from it to the openEHR composition by model mapper '%s' (archetype '%s'). %s; evaluated on: %s",
                dataPoint.fhirType(), first.getModelMetadataName(), first.getArchetype(), reasons,
                describeDataPoint(dataPoint, fhirVersion));
    }

    private static String mappingsWithOutcome(final Map<MappingHelper, MappingOutcome> unmapped,
                                              final MappingOutcome outcome) {
        return unmapped.entrySet().stream()
                .filter(entry -> entry.getValue() == outcome)
                .map(entry -> MappingContext.of(entry.getKey(), UNIDIRECTIONAL_TOOPENEHR))
                .map(context -> String.format("'%s' (FHIR '%s')", context.mappingName(), context.fhirPath()))
                .collect(Collectors.joining(", "));
    }


    /**
     * Upper bound on the JSON of an evaluated element quoted in a warning, so one large resource cannot blow up
     * the OperationOutcome.
     */
    static final int DATA_POINT_JSON_MAX_LENGTH = 2000;

    /**
     * The element the mappings were evaluated on, as FHIR JSON (a resource or a plain element such as a
     * {@code Coding}), truncated to {@link #DATA_POINT_JSON_MAX_LENGTH}. Falls back to the FHIR type when the
     * element cannot be encoded, since this only decorates a warning.
     */
    private String describeDataPoint(final IBase dataPoint, final Spec.Version fhirVersion) {
        try {
            final String json = fhirContextRegistry.getContext(fhirVersion).newJsonParser().encodeToString(dataPoint);
            if (json.length() <= DATA_POINT_JSON_MAX_LENGTH) {
                return json;
            }
            return json.substring(0, DATA_POINT_JSON_MAX_LENGTH) + "… (truncated, " + json.length() + " characters)";
        } catch (final RuntimeException e) {
            log.debug("Could not encode {} for the nothing-mapped warning", dataPoint.fhirType(), e);
            return "<" + dataPoint.fhirType() + ">";
        }
    }

    private boolean fhirEmptyNotEmptyPasses(final MappingHelper mappingHelper,
                                            final List<Condition> fhirConditions,
                                            final IFhirPath versionedFhirPath,
                                            final Class<? extends IBase> baseClass) {
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
                final Optional<? extends IBase> exists = versionedFhirPath.evaluateFirst(
                        mappingHelper.getGeneratingFhirResource(), fhirPath, baseClass);
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

    /**
     * Preprocessor condition gate: every criteria of every condition must be contained (raw,
     * substring semantics) in the value of ANY of the condition's targetAttributes, evaluated
     * relative to the resource itself. Negation is driven by the first condition's operator only —
     * quirks deliberately preserved from the deprecated string-built variant.
     */
    boolean fhirPreconditionPasses(final List<Condition> conditions,
                                   final IBase resource,
                                   final IFhirPath versionedFhirPath,
                                   final Class<? extends IBase> baseClass) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        final boolean negate = FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(conditions.get(0).getOperator());

        boolean matches = true;
        for (final Condition precondition : conditions) {
            for (final String criteria : precondition.getCriterias()) {
                final boolean criteriaContained = precondition.getTargetAttributes().stream()
                        .anyMatch(targetAttribute -> versionedFhirPath
                                .evaluate(resource, targetAttribute, baseClass).stream()
                                .anyMatch(value -> attributeValueAsString(value).contains(criteria)));
                if (!criteriaContained) {
                    matches = false;
                    break;
                }
            }
            if (!matches) {
                break;
            }
        }
        return negate != matches;
    }

    private String attributeValueAsString(final IBase value) {
        return value instanceof IPrimitiveType<?> primitive ? primitive.getValueAsString() : value.toString();
    }

    @SuppressWarnings("unchecked")
    private Class<? extends IBase> resolveBaseClass(final Spec.Version fhirVersion) {
        try {
            return (Class<? extends IBase>) Class.forName(fhirVersion.modelPackage() + "Base");
        } catch (final ClassNotFoundException e) {
            return org.hl7.fhir.r4.model.Base.class;
        }
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
        // segment, then collapse any outer [n] markers to :0. A split path without any [n] is a
        // non-repeating hierarchy node (or one whose AQL didn't fully resolve against the template),
        // so there is no occurrence to index and it is used as-is.
        final String indexed = replaceLastRecurring(splitPath, i);
        return indexed.replaceAll(RECURRING_SYNTAX_ESCAPED, ":0");
    }

    private String replaceLastRecurring(final String path, final int i) {
        if (!path.contains(RECURRING_SYNTAX)) {
            return path;
        }
        return stringUtils.replaceLastIndexOf(path, RECURRING_SYNTAX, ":" + i);
    }

    /**
     * Executes one mapping against one element.
     *
     * @return how it ended; a problem that is reported here as its own issue (an unevaluable FHIRPath) comes back
     * as {@link MappingOutcome#NOT_APPLICABLE} so the walk does not report it a second time
     */
    MappingOutcome doMapping(final MappingHelper helper, final JsonObject flatComposition, final IBase iteratingBase,
                             final Map<String, Integer> indexByHierarchyPath,
                             final Class<? extends IBase> baseClass,
                             final Spec.Version fhirVersion,
                             final MappingIssueCollector issueCollector) {
        final MappingTimer mappingTimer = MappingTimer.start();
        try {
            final String fhirPath = helper.getFhir();

            final IBase toResolveOn = getToResolveOn(iteratingBase, helper);

            final IFhirPath versionedFhirPath = fhirContextRegistry.getFhirPath(fhirVersion);

            final List<? extends IBase> results;
            try {
                results = resolveFhirResults(helper, fhirPath, toResolveOn, versionedFhirPath, baseClass);
            } catch (final FhirPathEvaluationException e) {
                // the expression in the mapper cannot be evaluated against this input: skip the mapping, but tell
                // the caller which mapping and expression, since that is what the mapper author needs to fix it
                final MappingContext context = MappingContext.of(helper, UNIDIRECTIONAL_TOOPENEHR);
                log.warn("Skipped {}: {}", context.describe(), e.getMessage(), e);
                issueCollector.addWarning(String.format("Skipped %s: %s", context.describe(), e.getMessage()));
                return MappingOutcome.NOT_APPLICABLE;
            }
            if (results == null) {
                return MappingOutcome.NOT_APPLICABLE; // nothing resolvable for this mapping (see resolveReference)
            }

            if (helper.getProgrammedMapping() == null
                    && (results.isEmpty() || resultsRepresentMissingPrimitiveValues(results))) {
                if (handleMissingResults(helper, flatComposition, toResolveOn, fhirPath, versionedFhirPath,
                        baseClass)) {
                    return MappingOutcome.MAPPED;
                }
                // a reference mapping filters by resource type and a condition filters by content: an empty result
                // there is the gate saying "not this element", not data that is missing
                final boolean gated = isReferenceMapping(helper)
                        || (helper.getFhirConditions() != null && !helper.getFhirConditions().isEmpty());
                return gated ? MappingOutcome.NOT_APPLICABLE : MappingOutcome.NO_DATA;
            }
            return populateOpenEhrForEachResult(helper, flatComposition, toResolveOn, results, fhirPath,
                    indexByHierarchyPath, versionedFhirPath, baseClass, fhirVersion, issueCollector);
        } finally {
            metricsLogger.record("doMapping",
                    "mapping=" + helper.getMappingName() + " model=" + helper.getModelMetadataName(),
                    mappingTimer.elapsedMs());
        }
    }

    private IBase getToResolveOn(final IBase iteratingBase,
                                 final MappingHelper helper) {
        if (helper.getOriginalFhirPath() == null) {
            return iteratingBase;
        }
        return helper.getOriginalFhirPath().startsWith(FHIR_RESOURCE_FC) ? helper.getGeneratingFhirResource() : iteratingBase;
    }

    /**
     * Resolves FHIR results for the given path and base resource.
     *
     * @throws FhirPathEvaluationException when the mapping's FHIRPath expression cannot be evaluated
     * @return the resolved elements, or {@code null} when a reference mapping has nothing to resolve against
     */
    private List<? extends IBase> resolveFhirResults(final MappingHelper helper, final String fhirPath,
                                                     final IBase toResolveOn,
                                                     final IFhirPath versionedFhirPath,
                                                     final Class<? extends IBase> baseClass) {
        if (isReferenceMapping(helper)) {
            return resolveReference(toResolveOn, helper, versionedFhirPath, baseClass);
        }

        if (StringUtils.isEmpty(fhirPath)
                || FhirConnectConst.FHIR_ROOT_FC.equals(helper.getOriginalFhirPath())
                || helper.isUseParentRoot()) {
            return resolveAsParentRoot(helper, fhirPath, toResolveOn, versionedFhirPath, baseClass);
        }

        if (FhirConditionEvaluator.hasPathFilteringConditions(helper.getFhirConditions())) {
            return fhirConditionEvaluator.evaluateWithConditions(helper, fhirPath, toResolveOn, versionedFhirPath,
                    baseClass);
        }

        return evaluateFhirPath(fhirPath, toResolveOn, versionedFhirPath, baseClass);
    }

    /**
     * A mapping whose job is to reach the elements its children map: a slot archetype link, a {@code $reference}
     * mapping, or any mapping with children.
     */
    private static boolean isStructural(final MappingHelper helper) {
        return helper.isHasSlot() || isReferenceMapping(helper) || !helper.getChildren().isEmpty();
    }

    private static boolean isReferenceMapping(final MappingHelper helper) {
        return helper.getOriginalOpenEhrPath() != null
                && helper.getOriginalOpenEhrPath().startsWith(FhirConnectConst.REFERENCE);
    }

    private List<IBase> resolveAsParentRoot(final MappingHelper helper, final String fhirPath,
                                            final IBase toResolveOn,
                                            final IFhirPath versionedFhirPath,
                                            final Class<? extends IBase> baseClass) {
        log.debug("Taking Base itself as fhirPath is {}", fhirPath);
        if (helper.getFhirConditions() == null) {
            return Collections.singletonList(toResolveOn);
        }
        // conditions present — verify they still pass before accepting the parent root
        return fhirConditionEvaluator.parentRootPassesConditions(helper, toResolveOn, versionedFhirPath, baseClass)
                ? Collections.singletonList(toResolveOn)
                : Collections.emptyList();
    }

    private List<? extends IBase> evaluateFhirPath(final String fhirPath,
                                                   final IBase toResolveOn,
                                                   final IFhirPath versionedFhirPath,
                                                   final Class<? extends IBase> baseClass) {
        try {

            final String fhirPathToUse;
            if (!toResolveOn.fhirType().equalsIgnoreCase("Extension")
                    && toResolveOn.fhirType().equalsIgnoreCase(fhirPath.split("\\.")[0])) {
                fhirPathToUse = fhirPath.substring(fhirPath.indexOf(".") + 1);
            } else {
                fhirPathToUse = fhirPath;
            }

            return versionedFhirPath.evaluate(toResolveOn,
                    fhirPathToUse.replace(".as(Enumeration)", ""),
                    // casting to enumeration only works when doing toFhir, else it complains it's not a valid fhir type
                    baseClass);
        } catch (final RuntimeException e) {
            // e.g. a malformed expression, or resolve() unable to find the referenced resource; the mapping loop
            // turns this into a warning that names the mapping and the expression
            throw new FhirPathEvaluationException(fhirPath, e);
        }
    }

    private boolean handleMissingResults(final MappingHelper helper, final JsonObject flatComposition,
                                         final IBase toResolveOn, final String fhirPath,
                                         final IFhirPath versionedFhirPath,
                                         final Class<? extends IBase> baseClass) {
        final boolean handledNullFlavour = toOpenEhrNullFlavour.handleDataAbsentReasonWhenNoResult(
                helper, flatComposition, toResolveOn, versionedFhirPath, baseClass);
        if (handledNullFlavour) {
            return true;
        }
        log.warn("No results found for FHIRPath {}, evaluating on type: {}", fhirPath, toResolveOn.getClass());
        return false;
    }

    /**
     * Writes the helper's value for each resolved element and recurses into its children.
     *
     * @return the best outcome over all results: the helper's own value write, or — for a helper that carries no
     * value of its own (a structural {@code $fhirRoot} / reference / slot mapping) — what its children did
     */
    private MappingOutcome populateOpenEhrForEachResult(final MappingHelper helper, final JsonObject flatComposition,
                                                        final IBase toResolveOn, final List<? extends IBase> results,
                                                        final String fhirPath,
                                                        final Map<String, Integer> indexByHierarchyPath,
                                                        final IFhirPath versionedFhirPath,
                                                        final Class<? extends IBase> baseClass,
                                                        final Spec.Version fhirVersion,
                                                        final MappingIssueCollector issueCollector) {
        final String fullOpenEhrFlatPath = helper.getFullOpenEhrFlatPath();
        MappingOutcome outcome = MappingOutcome.NOT_APPLICABLE;
        for (int i = 0; i < results.size(); i++) {
            final MappingHelper clonedHelper = helper.cloneWithFhirResourceAndRootIntact();
            final IBase result = results.get(i);

            final String thePath = resolveIndexedPath(fullOpenEhrFlatPath, i);
            log.debug("Setting value taken with fhirPath {} from object type {}", fhirPath, toResolveOn.getClass());

            fixAllChildrenRecurringElements(clonedHelper, thePath);

            MappingOutcome valueOutcome = MappingOutcome.NOT_APPLICABLE;
            if (!OPENEHR_TYPE_NONE.equals(clonedHelper.getHardcodedType())) {
                final List<String> possibleRmTypes = clonedHelper.getPossibleRmTypes();
                if (possibleRmTypes != null && !possibleRmTypes.isEmpty()) {
                    final String deducedRmType = deduceRmType(result, possibleRmTypes); // if we can deduce RM type based on what result is and what possibleRmTypes are, we do that
                    final String openEhrPathToPopulateTo =
                            openFhirMapperUtils.removeAqlSuffix(thePath, deducedRmType)
                                    + clonedHelper.getFlatPathPipeSuffix();
                    valueOutcome = populateValue(helper, clonedHelper, result, thePath, openEhrPathToPopulateTo,
                            flatComposition, deducedRmType, issueCollector);
                } else if (StringUtils.isNotEmpty(clonedHelper.getProgrammedMapping())) {
                    // still invoke the programmed one
                    valueOutcome = populateValue(helper, clonedHelper, result, thePath, thePath, flatComposition,
                            null, issueCollector);
                }
            }

            if (valueOutcome == MappingOutcome.NOTHING_WRITTEN && isStructural(clonedHelper)) {
                // a slot, reference or parent mapping exists to hand the element to its children; that its own
                // value write produces nothing is by design, so only what the children did counts
                valueOutcome = MappingOutcome.NOT_APPLICABLE;
            }
            final MappingOutcome childOutcome = recurseIntoChildren(clonedHelper, result, helper, flatComposition,
                    indexByHierarchyPath, baseClass, fhirVersion, issueCollector);
            outcome = outcome.best(valueOutcome).best(childOutcome);
        }

        if(results.isEmpty() && helper.getProgrammedMapping() != null) {
            // we still want programmed mapping to happen and within there you can decide what to do
            outcome = outcome.best(invokeProgrammedMapping(helper, flatComposition, null, issueCollector));
        }
        return outcome;
    }

    private String deduceRmType(final IBase result, final List<String> possibleRmTypes) {
        if (possibleRmTypes != null && possibleRmTypes.size() == 1) {
            return possibleRmTypes.get(0);
        }
        final String fhirType = result.fhirType();
        switch (fhirType) {
            case "CodeableConcept" -> {
                if (possibleRmTypes.contains(DV_ORDINAL)) return DV_ORDINAL;
                if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
                if (possibleRmTypes.contains(DV_TEXT)) return DV_TEXT;
            }
            case "Coding" -> {
                if (possibleRmTypes.contains(CODE_PHRASE)) return CODE_PHRASE;
                if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
            }
            case "Quantity" -> {
                if (possibleRmTypes.contains(DV_QUANTITY)) return DV_QUANTITY;
                if (possibleRmTypes.contains(DV_COUNT)) return DV_COUNT;
                if (possibleRmTypes.contains(DV_PROPORTION)) return DV_PROPORTION;
                if (possibleRmTypes.contains(DV_ORDINAL)) return DV_ORDINAL;
            }
            case "integer", "positiveInt", "unsignedInt" -> {
                if (possibleRmTypes.contains(DV_COUNT)) return DV_COUNT;
                if (possibleRmTypes.contains(DV_ORDINAL)) return DV_ORDINAL;
            }
            case "dateTime", "instant" -> {
                if (possibleRmTypes.contains(DV_DATE_TIME)) return DV_DATE_TIME;
            }
            case "date" -> {
                if (possibleRmTypes.contains(DV_DATE)) return DV_DATE;
                if (possibleRmTypes.contains(DV_DATE_TIME)) return DV_DATE_TIME;
            }
            case "time" -> {
                if (possibleRmTypes.contains(DV_TIME)) return DV_TIME;
            }
            case "boolean" -> {
                if (possibleRmTypes.contains(DV_BOOL)) return DV_BOOL;
            }
            case "string", "markdown", "uri", "url", "canonical", "id", "oid", "uuid" -> {
                if (possibleRmTypes.contains(DV_TEXT)) return DV_TEXT;
                if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
                if (possibleRmTypes.contains(DV_DATE_TIME)) return DV_DATE_TIME;
                if (possibleRmTypes.contains(DV_DURATION)) return DV_DURATION;
            }
            case "Period" -> {
                if (possibleRmTypes.contains(DV_INTERVAL)) return DV_INTERVAL;
            }
            case "Range" -> {
                if (possibleRmTypes.contains(DV_INTERVAL)) return DV_INTERVAL;
            }
            case "Attachment" -> {
                if (possibleRmTypes.contains(DV_MULTIMEDIA)) return DV_MULTIMEDIA;
            }
            case "Identifier" -> {
                if (possibleRmTypes.contains(DV_IDENTIFIER)) return DV_IDENTIFIER;
            }
            case "Ratio" -> {
                if (possibleRmTypes.contains(DV_PROPORTION)) return DV_PROPORTION;
                if (possibleRmTypes.contains(DV_QUANTITY)) return DV_QUANTITY;
            }
            default -> {
                // Enumeration types report their bound type name (e.g. "code") via fhirType();
                // fall back to checking IBaseEnumeration interface for coded value deduction.
                if (result instanceof IBaseEnumeration<?>) {
                    if (possibleRmTypes.contains(DV_CODED_TEXT)) return DV_CODED_TEXT;
                    if (possibleRmTypes.contains(DV_TEXT)) return DV_TEXT;
                }
            }
        }
        return null;
    }

    private String resolveIndexedPath(final String fullOpenEhrFlatPath, final int i) {
        if (fullOpenEhrFlatPath == null || !fullOpenEhrFlatPath.contains(RECURRING_SYNTAX)) {
            return fullOpenEhrFlatPath;
        }
        return stringUtils.replaceLastIndexOf(fullOpenEhrFlatPath, RECURRING_SYNTAX, ":" + i);
    }

    /**
     * @return {@link MappingOutcome#MAPPED} when a flat entry was written, {@link MappingOutcome#NOTHING_WRITTEN}
     * when the populator produced nothing from the resolved data, or what the mapping code reported
     */
    private MappingOutcome populateValue(final MappingHelper helper, final MappingHelper clonedHelper,
                                         final IBase result,
                                         final String thePath, final String openEhrPathToPopulateTo,
                                         final JsonObject flatComposition, final String rmType,
                                         final MappingIssueCollector issueCollector) {

        long possibleRmTypes = helper.getPossibleRmTypes().size();
        boolean isMultipleTypes = possibleRmTypes > 1 && !isOnlyText(helper.getPossibleRmTypes());
        final int before = flatComposition.size();
        if (StringUtils.isNotEmpty(clonedHelper.getManualOpenEhrValue())) {
            log.debug("Hardcoding value {} to path: {}", clonedHelper.getManualOpenEhrValue(), openEhrPathToPopulateTo);
            // is it ok we use string type here? could it be something else? probably it could be..
            openEhrPopulator.setOpenEhrValue(helper, openEhrPathToPopulateTo,
                    new StringType(clonedHelper.getManualOpenEhrValue()),
                    rmType, isMultipleTypes, flatComposition, helper.getTerminology(),
                    helper.getAvailableCodings());
        } else if (StringUtils.isNotEmpty(helper.getProgrammedMapping())) {
            return invokeProgrammedMapping(helper, flatComposition, result, issueCollector);
        } else {
            final boolean handledEventTime = applyEventTypeMappingIfNeeded(helper, result, thePath, flatComposition);
            if (!handledEventTime) {
                openEhrPopulator.setOpenEhrValue(helper, openEhrPathToPopulateTo, result, rmType,
                        isMultipleTypes,
                        flatComposition, helper.getTerminology(), helper.getAvailableCodings());
            }
        }
        return flatComposition.size() > before ? MappingOutcome.MAPPED : MappingOutcome.NOTHING_WRITTEN;
    }

    private boolean isOnlyText(final List<String> possibleRmTypes) {
//        return possibleRmTypes.size() == 2
//                && possibleRmTypes.contains(DV_TEXT)
//                && possibleRmTypes.contains(DV_CODED_TEXT);
        return false;
    }

    /**
     * @return what the children's walk did; {@link MappingOutcome#NOT_APPLICABLE} when there are no children or
     * the slot's resource type gate rejects the element
     */
    private MappingOutcome recurseIntoChildren(final MappingHelper clonedHelper, final IBase result,
                                               final MappingHelper parentHelper, final JsonObject flatComposition,
                                               final Map<String, Integer> indexByHierarchyPath,
                                               final Class<? extends IBase> baseClass,
                                               final Spec.Version fhirVersion,
                                               final MappingIssueCollector issueCollector) {
        if (clonedHelper.getChildren().isEmpty()) {
            return MappingOutcome.NOT_APPLICABLE;
        }
        clonedHelper.getChildren().forEach(c -> {
            c.setGeneratingFhirRoot(result);
            if (c.isFollowedBy()) {
                c.setGeneratingFhirResource(parentHelper.getGeneratingFhirResource());
            } else if (clonedHelper.isHasSlot()) {
                c.setGeneratingFhirResource(result);
            } else {
                c.setGeneratingFhirResource(parentHelper.getGeneratingFhirResource());
            }
        });
        final String generatingResourceType = clonedHelper.getChildren().get(clonedHelper.getChildren().size() - 1).getGeneratingResourceType();
        final boolean isBackboneElement = "BackboneElement".equals(generatingResourceType);
        if (!isBackboneElement && clonedHelper.isHasSlot() && !result.fhirType().equalsIgnoreCase(generatingResourceType)) {
            return MappingOutcome.NOT_APPLICABLE;
        }
        return walk(clonedHelper.getChildren(), flatComposition, result,
                clonedHelper.isHasSlot(), indexByHierarchyPath, baseClass, fhirVersion, issueCollector);
    }

    /**
     * Runs the mapping code registered for the helper. A failure inside the custom mapping is reported through
     * {@link #reportMappingFailure} (recorded as an {@code error} issue, or thrown when the collector is
     * fail-fast) instead of being swallowed; a mapping code that declines to apply, or one that is not registered,
     * is reported as a warning.
     *
     * @return {@link MappingOutcome#MAPPED} or {@link MappingOutcome#NOTHING_WRITTEN} when the code claimed
     * success, {@link MappingOutcome#NOT_APPLICABLE} when the problem was reported here already
     */
    private MappingOutcome invokeProgrammedMapping(final MappingHelper mappingHelper,
                                                   final JsonObject flatComposition,
                                                   final IBase result,
                                                   final MappingIssueCollector issueCollector) {
        final String mappingCode = mappingHelper.getProgrammedMapping();
        log.info("Using mapping code: {}", mappingCode);

        final CustomMapping customMapping = customMappingRegistry.find(mappingCode).orElse(null);
        if (customMapping == null) {
            log.warn("No CustomMapping found for mapping code: {}", mappingCode);
            issueCollector.addWarning(String.format(
                    "Element could not be mapped: no CustomMapping registered for mapping code '%s' (%s).",
                    mappingCode, MappingContext.of(mappingHelper, UNIDIRECTIONAL_TOOPENEHR).describe()));
            return MappingOutcome.NOT_APPLICABLE;
        }

        final int before = flatComposition.size();
        final boolean success;
        try {
            success = customMapping.applyFhirToOpenEhrMapping(
                    mappingHelper,
                    result,
                    mappingHelper.getPossibleRmTypes(),
                    flatComposition,
                    openEhrPopulator,
                    openFhirMapperUtils,
                    stringUtils
            );
        } catch (final RuntimeException e) {
            reportMappingFailure(e, mappingHelper, UNIDIRECTIONAL_TOOPENEHR, issueCollector);
            return MappingOutcome.NOT_APPLICABLE;
        }

        if (!success) {
            log.warn("Mapping failed for code: {}", mappingCode);
            issueCollector.addWarning(String.format(
                    "Mapping code '%s' did not map anything for %s.",
                    mappingCode, MappingContext.of(mappingHelper, UNIDIRECTIONAL_TOOPENEHR).describe()));
            return MappingOutcome.NOT_APPLICABLE;
        }
        return flatComposition.size() > before ? MappingOutcome.MAPPED : MappingOutcome.NOTHING_WRITTEN;
    }

    private boolean applyEventTypeMappingIfNeeded(final MappingHelper helper,
                                                  final IBase result,
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
            final String fhirType = result.fhirType();
            if ("Period".equals(fhirType)) {
                final Date start = getPeriodDate(result, "getStart");
                final Date end = getPeriodDate(result, "getEnd");
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
            } else if ("dateTime".equals(fhirType) || "instant".equals(fhirType) || "date".equals(fhirType)) {
                openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/time", result,
                        FhirConnectConst.DV_DATE_TIME, false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                return true;
            } else if ("time".equals(fhirType)) {
                openEhrPopulator.setOpenEhrValue(helper, openEhrPath + "/time", result,
                        FhirConnectConst.DV_TIME, false, flatComposition, helper.getTerminology(), helper.getAvailableCodings());
                return true;
            }
        }
        return false;
    }

    private Date getPeriodDate(final IBase period, final String methodName) {
        try {
            final Object result = period.getClass().getMethod(methodName).invoke(period);
            return result instanceof Date ? (Date) result : null;
        } catch (final Exception e) {
            return null;
        }
    }

    private boolean isEventRmType(final String rmType) {
        return DV_EVENT.equals(rmType) || POINT_EVENT.equals(rmType) || INTERVAL_EVENT.equals(rmType);
    }

    private List<? extends IBase> resolveReference(final IBase toResolveOn,
                                                   final MappingHelper mappingHelper,
                                                   final IFhirPath versionedFhirPath,
                                                   final Class<? extends IBase> baseClass) {
        final String needsToBeResourceType = mappingHelper.getResolveResourceType();
        if ("BundleEntryComponent".equals(toResolveOn.getClass().getSimpleName())) {
            try {
                final Object resource = toResolveOn.getClass().getMethod("getResource").invoke(toResolveOn);
                List<IBase> iBases = resource instanceof IBase ? List.of((IBase) resource) : Collections.emptyList();
                return iBases.stream().filter(e -> e.fhirType().equals(needsToBeResourceType))
                        .collect(Collectors.toList());
            } catch (final Exception e) {
                log.error("Could not get resource from BundleEntryComponent", e);
                return Collections.emptyList();
            }
        }
        if (!(toResolveOn instanceof IBaseReference)) {
            List<? extends IBase> evaluated = versionedFhirPath.evaluate(toResolveOn, mappingHelper.getFhir(), baseClass);
            return evaluated.stream().filter(e -> e.fhirType().equals(needsToBeResourceType))
                    .collect(Collectors.toList());
        }
        if (FhirConnectConst.REFERENCE.equals(mappingHelper.getOriginalOpenEhrPath())) {
            List<? extends IBase> evaluated = versionedFhirPath.evaluate(toResolveOn, "resolve()", baseClass);
            return evaluated.stream().filter(e -> e.fhirType().equals(needsToBeResourceType))
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    private boolean resultsRepresentMissingPrimitiveValues(final List<? extends IBase> results) {
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
