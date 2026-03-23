package com.syntaric.openfhir.mapping.tofhir;

import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_NOT_OF;
import static com.syntaric.openfhir.fc.FhirConnectConst.UNIDIRECTIONAL_TOFHIR;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.mapping.BidirectionalMappingEngine;
import com.syntaric.openfhir.mapping.custommappings.CustomMapping;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.OpenEhrFlatPathDataExtractor;
import com.syntaric.openfhir.util.FhirInstanceCreator;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.FhirInstancePopulator;
import com.syntaric.openfhir.util.OpenEhrConditionEvaluator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ToFhirMappingEngine extends BidirectionalMappingEngine {

    final private OpenEhrConditionEvaluator openEhrConditionEvaluator;
    final private FhirInstanceCreator fhirInstanceCreator;
    final private FhirInstanceCreatorUtility fhirInstanceCreatorUtility;
    final private FhirPathR4 fhirPath;
    final private OpenEhrFlatPathDataExtractor openEhrFlatPathDataExtractor;
    final private OpenFhirStringUtils openFhirStringUtils;
    final private FhirInstancePopulator fhirInstancePopulator;
    final private ToFhirInstantiator toFhirInstantiator;
    final private CustomMappingRegistry customMappingRegistry;
    final private OpenFhirMapperUtils openFhirMapperUtils;

    @Autowired
    public ToFhirMappingEngine(final OpenEhrConditionEvaluator openEhrConditionEvaluator,
                               final FhirInstanceCreator fhirInstanceCreator,
                               final FhirInstanceCreatorUtility fhirInstanceCreatorUtility,
                               final FhirPathR4 fhirPath,
                               final OpenEhrFlatPathDataExtractor openEhrFlatPathDataExtractor,
                               final OpenFhirStringUtils openFhirStringUtils,
                               final FhirInstancePopulator fhirInstancePopulator,
                               final ToFhirInstantiator toFhirInstantiator,
                               final CustomMappingRegistry customMappingRegistry,
                               final OpenFhirMapperUtils openFhirMapperUtils) {
        super(fhirPath);
        this.openEhrConditionEvaluator = openEhrConditionEvaluator;
        this.fhirInstanceCreator = fhirInstanceCreator;
        this.fhirInstanceCreatorUtility = fhirInstanceCreatorUtility;
        this.fhirPath = fhirPath;
        this.openEhrFlatPathDataExtractor = openEhrFlatPathDataExtractor;
        this.openFhirStringUtils = openFhirStringUtils;
        this.fhirInstancePopulator = fhirInstancePopulator;
        this.toFhirInstantiator = toFhirInstantiator;
        this.customMappingRegistry = customMappingRegistry;
        this.openFhirMapperUtils = openFhirMapperUtils;
    }

    public Bundle mapToFhir(final Map<String, List<MappingHelper>> mappingHelpersByArchetype,
                            final JsonObject flatJsonObject) {
        final Bundle returningBundle = new Bundle();
        mappingHelpersByArchetype.forEach((archetype, mappingHelpers) -> {
            final List<JsonObject> splitForEachResource = splitByHierarchy(flatJsonObject, mappingHelpers.get(0)
                    .getOpenEhrHierarchySplitFlatPath());


            for (final JsonObject jsonObject : splitForEachResource) {
                // we need to clone MappingHelpers because we're adding things on them as we go

                final List<MappingHelper> copiesToIterateOver = mappingHelpers.stream().map(MappingHelper::clone)
                        .toList();
                final MappingHelper firstMapping = copiesToIterateOver.get(0);
                final Resource generatedResource = fhirInstanceCreatorUtility.create(
                        firstMapping.getGeneratingResourceType()); // generating resource is always same for all mappings
                copiesToIterateOver.forEach(helper -> helper.setGeneratingFhirResource(generatedResource));
                returningBundle.addEntry(new Bundle.BundleEntryComponent().setResource(generatedResource));

                handleMappingIterations(copiesToIterateOver, jsonObject, true);

                postProcessMappingFromCoverConditions(generatedResource,
                        mappingHelpers.get(0).getPreprocessorFhirConditions(),
                        archetype,
                        mappingHelpers.get(0).getTerminology());

            }
        });


        return returningBundle;
    }

    public void handleMappingIterations(final List<MappingHelper> helpers,
                                        final JsonObject jsonObject,
                                        final boolean isFirstEntryIntoModelMapping) {
        for (final MappingHelper mappingHelper : helpers) {
            final JsonObject relevantJsonObject = getRelevantJsonObject(jsonObject,
                    mappingHelper.getPreprocessorOpenEhrCondition(),
                    mappingHelper);
            if (relevantJsonObject.entrySet().isEmpty()) {
                log.warn("No relevant entries found for mapping name {}; skipping mapping.",
                        mappingHelper.getMappingName());
                continue;
            }

            final List<DataWithIndex> extractedData;
            if (StringUtils.isNotEmpty(mappingHelper.getProgrammedMapping())) {
                extractedData = invokeProgrammedMapping(mappingHelper, relevantJsonObject);
            } else {
                extractedData = openEhrFlatPathDataExtractor.extract(mappingHelper,
                        relevantJsonObject);
            }

            if (!shouldProcessMapping(mappingHelper, UNIDIRECTIONAL_TOFHIR)) {
                continue;
            }

            if (isChildrenOnlyIteration(mappingHelper, extractedData)) {
                handleChildrenOnlyIteration(mappingHelper, relevantJsonObject);
            } else if (mappingHelper.getManualFhirValue() != null) {
                handleHardcodedIteration(mappingHelper);
            } else {
                handleExtractedDataIteration(mappingHelper, extractedData, relevantJsonObject);
            }
        }


        if (isFirstEntryIntoModelMapping) {
            final MappingHelper aHelper = helpers.get(0);
            final List<Condition> preprocessorFhirConditions = aHelper.getPreprocessorFhirConditions();
            final Object generatingFhirRoot = aHelper.getGeneratingFhirRoot();
            if (preprocessorFhirConditions != null && !preprocessorFhirConditions.isEmpty()
                    && generatingFhirRoot != null) {
                postProcessMappingFromCoverConditions((Base) generatingFhirRoot,
                        preprocessorFhirConditions,
                        aHelper.getGeneratingResourceType(),
                        aHelper.getTerminology());
            }
        }
    }

    private List<DataWithIndex> invokeProgrammedMapping(final MappingHelper mappingHelper,
                                                        final JsonObject relevantJsonObject) {
        CustomMapping customMapping = customMappingRegistry.find(mappingHelper.getProgrammedMapping()).orElse(null);
        if (customMapping == null) {
            log.warn("No CustomMapping found for mapping code: {}", mappingHelper.getProgrammedMapping());
            return Collections.emptyList();
        } else {
            if (relevantJsonObject.isEmpty()) {
                final String fallbackPath = mappingHelper.getFullOpenEhrFlatPath();
                final DataWithIndex fallback = customMapping.applyOpenEhrToFhirMapping(
                        mappingHelper,
                        Collections.emptyList(),
                        relevantJsonObject,
                        -1,
                        fallbackPath,
                        mappingHelper.getGeneratingResourceType(),
                        mappingHelper.getFhir(),
                        openFhirStringUtils,
                        openFhirMapperUtils
                );
                return fallback == null ? new ArrayList<>() : new ArrayList<>(List.of(fallback));
            } else {
                final Integer lastIndex = openFhirStringUtils.getLastIndex(mappingHelper.getFullOpenEhrFlatPath());
                final DataWithIndex programmedExtracted = customMapping.applyOpenEhrToFhirMapping(
                        mappingHelper,
                        List.of(mappingHelper.getFullOpenEhrFlatPath()),
                        relevantJsonObject,
                        lastIndex,
                        mappingHelper.getFullOpenEhrFlatPath(),
                        mappingHelper.getGeneratingResourceType(),
                        mappingHelper.getFhir(),
                        openFhirStringUtils,
                        openFhirMapperUtils
                );
                return programmedExtracted == null ? Collections.emptyList() : List.of(programmedExtracted);
            }
        }
    }

    /**
     * Returns true when no data was extracted but children exist and the type is not NONE —
     * meaning we must iterate by hierarchy splits and recurse into children only.
     */
    private boolean isChildrenOnlyIteration(final MappingHelper mappingHelper,
                                            final List<DataWithIndex> extractedData) {
        boolean isReference = FhirConnectConst.REFERENCE.equals(mappingHelper.getOriginalOpenEhrPath());
        boolean isSlot = mappingHelper.isHasSlot();
        final List<String> possibleRmTypes = mappingHelper.getPossibleRmTypes();
        final boolean isEvent = possibleRmTypes != null
                && possibleRmTypes.contains(FhirConnectConst.DV_EVENT) && !mappingHelper.getFhir().contains("effective"); // we should rather check which element this is in Fhir and if it's date time, then let it pass
        boolean isNonPopulatingField = isReference || isSlot || isEvent;
        return isNonPopulatingField || extractedData.isEmpty()
                && !mappingHelper.getChildren().isEmpty();
    }

    /**
     * Handles the case where no data was extracted but children need to be processed.
     * Splits the JSON by the openEHR flat path hierarchy and recurses into children for each slice.
     */
    private void handleChildrenOnlyIteration(final MappingHelper mappingHelper,
                                             final JsonObject relevantJsonObject) {
        final String fullOpenEhrFlatPath = mappingHelper.getFullOpenEhrFlatPath();
        final List<JsonObject> jsonObjects = splitByHierarchy(relevantJsonObject, fullOpenEhrFlatPath);
        for (final JsonObject object : jsonObjects) {
            final Object newRoot = resolveNewRootForChildrenIteration(mappingHelper, object, fullOpenEhrFlatPath);
            if (newRoot == null && fullOpenEhrFlatPath != null) {
                continue;
            }
            if (mappingHelper.getManualFhirValue() != null) {
                handleHardcodedMapping(mappingHelper, newRoot);
            }
            propagateToChildrenAndRecurse(mappingHelper, newRoot, object);
        }
    }

    /**
     * Instantiates the new FHIR root for a children-only iteration slice.
     * Returns null (signalling skip) when the flat path has no matching entries in the slice.
     */
    private Object resolveNewRootForChildrenIteration(final MappingHelper mappingHelper,
                                                      final JsonObject object,
                                                      final String fullOpenEhrFlatPath) {
        if (fullOpenEhrFlatPath == null) {
            return toFhirInstantiator.instantiateElement(mappingHelper, null, -1);
        }

        final String simplifiedPath = openFhirStringUtils
                .addRegexPatternToSimplifiedFlatFormat(fullOpenEhrFlatPath)
                .replace("[n]", "");
        final List<String> allEntriesThatMatch = openFhirStringUtils.getAllEntriesThatMatch(simplifiedPath, object);

        if (allEntriesThatMatch.isEmpty()) {
            return null;
        }

        final Integer index = openFhirStringUtils.getLastIndex(allEntriesThatMatch.get(0));
        final Object instantiated = toFhirInstantiator.instantiateElement(mappingHelper, null, -1);

        if (instantiated instanceof List<?> listObject && index != -1) {
            return listObject.get(listObject.size() - 1);
        }
        return instantiated;
    }

    /**
     * Handles the case where a manual FHIR value is set and no extracted data exists.
     * Instantiates the element and populates it with the hardcoded value.
     */
    private void handleHardcodedIteration(final MappingHelper mappingHelper) {
        final Object instantiated = toFhirInstantiator.instantiateElement(mappingHelper, null, -1);
        handleHardcodedMapping(mappingHelper, instantiated);
    }

    /**
     * Handles the normal case where openEHR data was extracted.
     * Iterates over each data point, instantiates and populates the corresponding FHIR element,
     * then recurses into children. If no data points exist, children are still recursed with the
     * current root.
     */
    private void handleExtractedDataIteration(final MappingHelper mappingHelper,
                                              final List<DataWithIndex> extractedData,
                                              final JsonObject relevantJsonObject) {
        final List<DataWithIndex> modifiableList = new ArrayList<>(extractedData);
        sortByLastIndex(modifiableList);

        for (final DataWithIndex extractedDataPoint : modifiableList) {
            final Object instantiated = toFhirInstantiator.instantiateElement(
                    mappingHelper,
                    extractedDataPoint.getData().getClass().getSimpleName(),
                    extractedDataPoint.getIndex());

            populateExtractedDataPoint(mappingHelper, instantiated, extractedDataPoint);
            propagateToChildrenAndRecurse(mappingHelper, instantiated, relevantJsonObject);
        }

        if (modifiableList.isEmpty() && !mappingHelper.getChildren().isEmpty()) {
            propagateToChildrenAndRecurse(mappingHelper, mappingHelper.getGeneratingFhirRoot(), relevantJsonObject);
        }
    }

    /**
     * Populates the instantiated FHIR element with the extracted data point value,
     * unless a manual value or NONE type is configured.
     */
    private void populateExtractedDataPoint(final MappingHelper mappingHelper,
                                            final Object instantiated,
                                            final DataWithIndex extractedDataPoint) {
        if (mappingHelper.getManualFhirValue() != null) {
            handleHardcodedMapping(mappingHelper, instantiated);
        } else if (!FhirConnectConst.OPENEHR_TYPE_NONE.equals(mappingHelper.getHardcodedType())) {
            fhirInstancePopulator.populateElement(mappingHelper,
                    instantiated,
                    extractedDataPoint,
                    mappingHelper.getModelMetadataName(),
                    mappingHelper.getMappingName(),
                    mappingHelper.getOpenEhr(),
                    mappingHelper.getFhir(),
                    mappingHelper.getTerminology());
        }
    }

    /**
     * Sets the given root and the parent's generating resource on all children,
     * then recurses into {@link #handleMappingIterations} for those children.
     */
    private void propagateToChildrenAndRecurse(final MappingHelper mappingHelper,
                                               final Object newRoot,
                                               final JsonObject jsonObject) {
        if (mappingHelper.getChildren().isEmpty()) {
            return;
        }
        mappingHelper.getChildren().forEach(child -> {
            child.setGeneratingFhirRoot(newRoot);
            child.setGeneratingFhirResource(mappingHelper.getGeneratingFhirResource());
        });
        handleMappingIterations(mappingHelper.getChildren(), jsonObject, mappingHelper.isHasSlot());
    }

    /**
     * Sorts datas by last index
     */
    private void sortByLastIndex(final List<DataWithIndex> datas) {
        Collections.sort(datas, (o1, o2) -> {
            // Extract numbers from the strings
            int num1 = openFhirStringUtils.getLastIndex(o1.getFullOpenEhrPath());
            int num2 = openFhirStringUtils.getLastIndex(o2.getFullOpenEhrPath());

            // Compare the numbers
            return Integer.compare(num1, num2);
        });
    }

    /**
     * Post processing that handles hard-coding of Resource data based on FhirConfig conditions in the header
     * of each mapping
     *
     * @param creatingResource a created resources
     * @param conditions       conditions in the header of a mapping
     */
    private void postProcessMappingFromCoverConditions(final Base creatingResource,
                                                       final List<Condition> conditions,
                                                       final String modelName,
                                                       final Terminology terminology) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        for (Condition condition : conditions) {
            if (condition.getCriteria() == null || CONDITION_OPERATOR_NOT_OF.equals(condition.getOperator())) {
                continue;
            }

            final String conditionFhirPathWithConditions = openFhirStringUtils.getFhirPathWithConditions(
                    condition.getTargetRoot(), condition, creatingResource.fhirType(), null);

            // check if it exists
            final List<Base> alreadyExists = fhirPath.evaluate(creatingResource, conditionFhirPathWithConditions,
                    Base.class);
            if (alreadyExists != null && !alreadyExists.isEmpty()) {
                continue;
            }
            final FhirInstanceCreator.InstantiateAndSetReturn hardcodedReturn = fhirInstanceCreator.instantiateAndSetElement(
                    creatingResource,
                    creatingResource.getClass(),
                    condition.getTargetRoot() + "." + condition.getTargetAttribute(),
                    null);

            final Object toSetCriteriaOn = toFhirInstantiator.getLastReturn(hardcodedReturn).getReturning();
            final Coding stringFromCriteria = openFhirStringUtils.getStringFromCriteria(
                    condition.getCriteria());
            fhirInstancePopulator.populateElement(null,
                    toSetCriteriaOn,
                    new StringType(stringFromCriteria.getCode()),
                    modelName,
                    "Cover Condition",
                    // todo: not sure what value this path will have and whether its ok for it to be a mappingName in analytics
                    null, // null since its hardcoding
                    conditionFhirPathWithConditions,
                    -1,
                    terminology);
        }
    }

    /**
     * Splits a flat-format composition {@link JsonObject} into one slice per occurrence of
     * {@code splitKey}.
     *
     * <p>When {@code splitKey} is {@code null} or produces no matches the original object is
     * returned as a single-element list (no split needed).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Strip the {@code [n]} recurring marker from {@code splitKey} and build a regex via
     *       {@link com.syntaric.openfhir.util.OpenFhirStringUtils#addRegexPatternToSimplifiedFlatFormat}.</li>
     *   <li>Collect the distinct occurrence prefixes that match (e.g. {@code diagnose/diagnose:0},
     *       {@code diagnose/diagnose:1}).</li>
     *   <li>For each prefix build a slice that contains:
     *       <ul>
     *         <li>every entry whose key starts with that prefix, and</li>
     *         <li>every "context" entry whose key does not start with <em>any</em> occurrence prefix
     *             (e.g. {@code diagnose/context/start_time}).</li>
     *       </ul>
     *   </li>
     * </ol>
     */
    List<JsonObject> splitByHierarchy(final JsonObject flatJsonObject, final String splitKey) {
        if (splitKey == null || !splitKey.endsWith(com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX)) {
            return List.of(flatJsonObject);
        }

        // build regex from splitKey (strip [n] first so it matches real :0/:1 indices)
        final String strippedKey = splitKey.replace(com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX,
                "");
        final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(strippedKey);

        // getAllEntriesThatMatch returns the matched substring from each key via matcher.group().
        // That substring may be a false positive (e.g. "order" matching inside "order_identifier"),
        // so we validate: a candidate is a real occurrence prefix only if at least one key in the
        // flat JSON actually starts with candidate + "/" or "|" (i.e. the candidate is a full segment).
        final List<String> occurrencePrefixes = openFhirStringUtils
                .getAllEntriesThatMatch(withRegex, flatJsonObject)
                .stream()
                .distinct()
                .filter(candidate -> flatJsonObject.keySet().stream()
                        .anyMatch(k -> k.startsWith(candidate + "/") || k.startsWith(candidate + "|") || k.equals(
                                candidate)))
                .toList();

        if (occurrencePrefixes.isEmpty()) {
            return List.of(flatJsonObject);
        }

        return occurrencePrefixes.stream()
                .map(prefix -> sliceForOccurrence(flatJsonObject, prefix, occurrencePrefixes))
                .toList();
    }

    /**
     * Builds a {@link JsonObject} slice for one occurrence prefix.
     * Includes entries belonging to {@code prefix} and context entries not under any occurrence.
     */
    private static JsonObject sliceForOccurrence(final JsonObject flatJsonObject,
                                                 final String prefix,
                                                 final List<String> allPrefixes) {
        final JsonObject slice = new JsonObject();
        for (final Entry<String, JsonElement> entry : flatJsonObject.entrySet()) {
            final String key = entry.getKey();
            final boolean belongsToThisOccurrence = key.startsWith(prefix);
            final boolean isContext = allPrefixes.stream().noneMatch(key::startsWith);
            if (belongsToThisOccurrence || isContext) {
                slice.add(key, entry.getValue());
            }
        }
        return slice;
    }

    private void handleHardcodedMapping(final MappingHelper mappingHelper,
                                        final Object lastInstantiatedElement) {
        log.info("Handling hardcoded mapping for mapping name {}.", mappingHelper.getMappingName());
        fhirInstancePopulator.populateElement(mappingHelper,
                lastInstantiatedElement,
                new StringType(mappingHelper.getManualFhirValue()),
                mappingHelper.getModelMetadataName(),
                mappingHelper.getMappingName(),
                mappingHelper.getOpenEhr(),
                mappingHelper.getFhir(),
                -1,
                mappingHelper.getTerminology());
    }

    JsonObject getRelevantJsonObject(final JsonObject flatJsonObject,
                                     final Condition preprocessorOpenEhrCondition,
                                     final MappingHelper mappingHelper) {
        final JsonObject splitByPreprocessor = evaluatePreprocessorCondition(flatJsonObject,
                preprocessorOpenEhrCondition);

        return evaluateSpecificMappingCondition(splitByPreprocessor, mappingHelper);
    }

    private JsonObject evaluatePreprocessorCondition(final JsonObject flatJsonObject,
                                                     final Condition preprocessorOpenEhrCondition) {
        if (preprocessorOpenEhrCondition == null) {
            return flatJsonObject;
        }
        return openEhrConditionEvaluator.splitByOpenEhrCondition(flatJsonObject,
                preprocessorOpenEhrCondition);
    }

    private JsonObject evaluateSpecificMappingCondition(final JsonObject flatJsonObject,
                                                        final MappingHelper mappingHelper) {
        if (mappingHelper.getOpenEhrConditions() == null || mappingHelper.getOpenEhrConditions().isEmpty()) {
            return flatJsonObject;
        }
        final Condition preprocessorOpenehrCondition = mappingHelper.getOpenEhrConditions().get(0);
        return openEhrConditionEvaluator.splitByOpenEhrCondition(flatJsonObject,
                preprocessorOpenehrCondition);
    }
}
