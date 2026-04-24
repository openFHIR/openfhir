package com.syntaric.openfhir.mapping.tofhir;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.BidirectionalMappingEngine;
import com.syntaric.openfhir.mapping.custommappings.CustomMapping;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.OpenEhrFlatPathDataExtractor;
import com.syntaric.openfhir.metrics.MappingMetricsLogger;
import com.syntaric.openfhir.metrics.MappingTimer;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static com.syntaric.openfhir.fc.FhirConnectConst.UNIDIRECTIONAL_TOFHIR;

@Slf4j
@Component
public class ToFhirMappingEngine extends BidirectionalMappingEngine {

    final private OpenEhrConditionEvaluator openEhrConditionEvaluator;
    final private FhirInstanceCreatorUtility fhirInstanceCreatorUtility;
    final private OpenEhrFlatPathDataExtractor openEhrFlatPathDataExtractor;
    final private OpenFhirStringUtils openFhirStringUtils;
    final private FhirInstancePopulator fhirInstancePopulator;
    final private ToFhirInstantiator toFhirInstantiator;
    final private CustomMappingRegistry customMappingRegistry;
    final private OpenFhirMapperUtils openFhirMapperUtils;
    final private MappingMetricsLogger metricsLogger;

    @Autowired
    public ToFhirMappingEngine(final OpenEhrConditionEvaluator openEhrConditionEvaluator,
                               final FhirInstanceCreatorUtility fhirInstanceCreatorUtility,
                               final FhirContextRegistry fhirContextRegistry,
                               final OpenEhrFlatPathDataExtractor openEhrFlatPathDataExtractor,
                               final OpenFhirStringUtils openFhirStringUtils,
                               final FhirInstancePopulator fhirInstancePopulator,
                               final ToFhirInstantiator toFhirInstantiator,
                               final CustomMappingRegistry customMappingRegistry,
                               final OpenFhirMapperUtils openFhirMapperUtils,
                               final MappingMetricsLogger metricsLogger) {
        super(fhirContextRegistry);
        this.openEhrConditionEvaluator = openEhrConditionEvaluator;
        this.fhirInstanceCreatorUtility = fhirInstanceCreatorUtility;
        this.openEhrFlatPathDataExtractor = openEhrFlatPathDataExtractor;
        this.openFhirStringUtils = openFhirStringUtils;
        this.fhirInstancePopulator = fhirInstancePopulator;
        this.toFhirInstantiator = toFhirInstantiator;
        this.customMappingRegistry = customMappingRegistry;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.metricsLogger = metricsLogger;
    }

    public IBaseBundle mapToFhir(final Map<String, List<MappingHelper>> mappingHelpersByArchetype,
                                 final JsonObject flatJsonObject,
                                 final Spec.Version fhirVersion) {
        final String modelPackage = fhirVersion.modelPackage();
        final IBaseBundle returningBundle = getBundle(fhirVersion);
        mappingHelpersByArchetype.forEach((archetype, mappingHelpers) -> {
            final MappingTimer archetypeTimer = MappingTimer.start();

            final List<JsonObject> splitForEachResource = splitByHierarchy(flatJsonObject, mappingHelpers.get(0)
                    .getOpenEhrHierarchySplitFlatPath());

            for (final JsonObject jsonObject : splitForEachResource) {
                // we need to clone MappingHelpers because we're adding things on them as we go

                final List<MappingHelper> copiesToIterateOver = mappingHelpers.stream().map(MappingHelper::clone)
                        .toList();
                final MappingHelper firstMapping = copiesToIterateOver.get(0);
                final IAnyResource generatedResource = fhirInstanceCreatorUtility.create(
                        firstMapping.getGeneratingResourceType(), modelPackage); // generating resource is always same for all mappings
                copiesToIterateOver.forEach(helper -> helper.setGeneratingFhirResource(generatedResource));
                addBundleEntry(returningBundle, generatedResource, fhirVersion);

                handleMappingIterations(copiesToIterateOver, jsonObject, modelPackage, fhirVersion);
            }

            metricsLogger.record("mapToFhir.archetype", "archetype=" + archetype, archetypeTimer.elapsedMs());
        });

        return returningBundle;
    }

    private void addBundleEntry(final IBaseBundle bundle, final IAnyResource resource,
                                final Spec.Version fhirVersion) {
        switch (fhirVersion) {
            case R4 -> ((Bundle) bundle).addEntry(new Bundle.BundleEntryComponent().setResource((Resource) resource));
            case STU3 ->
                    ((org.hl7.fhir.dstu3.model.Bundle) bundle).addEntry(new org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent().setResource((org.hl7.fhir.dstu3.model.Resource) resource));
            case R4B ->
                    ((org.hl7.fhir.r4b.model.Bundle) bundle).addEntry(new org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent().setResource((org.hl7.fhir.r4b.model.Resource) resource));
            case R5 ->
                    ((org.hl7.fhir.r5.model.Bundle) bundle).addEntry(new org.hl7.fhir.r5.model.Bundle.BundleEntryComponent().setResource((org.hl7.fhir.r5.model.Resource) resource));
        }
    }

    public IBaseBundle prepareBundle(final Spec.Version fhirVersion) {
        switch (fhirVersion) {
            case STU3 -> {
                final org.hl7.fhir.dstu3.model.Bundle b = new org.hl7.fhir.dstu3.model.Bundle();
                b.setType(org.hl7.fhir.dstu3.model.Bundle.BundleType.COLLECTION);
                return b;
            }
            case R4B -> {
                final org.hl7.fhir.r4b.model.Bundle b = new org.hl7.fhir.r4b.model.Bundle();
                b.setType(org.hl7.fhir.r4b.model.Bundle.BundleType.COLLECTION);
                return b;
            }
            case R5 -> {
                final org.hl7.fhir.r5.model.Bundle b = new org.hl7.fhir.r5.model.Bundle();
                b.setType(org.hl7.fhir.r5.model.Bundle.BundleType.COLLECTION);
                return b;
            }
            default -> {
                final Bundle b = new Bundle();
                b.setType(Bundle.BundleType.COLLECTION);
                return b;
            }
        }
    }

    private IBaseBundle getBundle(final Spec.Version fhirVersion) {
        return prepareBundle(fhirVersion);
    }

    public void mergeEntries(final IBaseBundle target, final IBaseBundle source, final Spec.Version fhirVersion) {
        switch (fhirVersion) {
            case STU3 ->
                    ((org.hl7.fhir.dstu3.model.Bundle) target).getEntry().addAll(((org.hl7.fhir.dstu3.model.Bundle) source).getEntry());
            case R4B ->
                    ((org.hl7.fhir.r4b.model.Bundle) target).getEntry().addAll(((org.hl7.fhir.r4b.model.Bundle) source).getEntry());
            case R5 ->
                    ((org.hl7.fhir.r5.model.Bundle) target).getEntry().addAll(((org.hl7.fhir.r5.model.Bundle) source).getEntry());
            default ->
                    ((Bundle) target).getEntry().addAll(((Bundle) source).getEntry());
        }
    }

    public List<org.hl7.fhir.instance.model.api.IBaseResource> getBundleResources(final IBaseBundle bundle,
                                                                                    final Spec.Version fhirVersion) {
        return switch (fhirVersion) {
            case STU3 -> ((org.hl7.fhir.dstu3.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent::getResource)
                    .collect(java.util.stream.Collectors.toList());
            case R4B -> ((org.hl7.fhir.r4b.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent::getResource)
                    .collect(java.util.stream.Collectors.toList());
            case R5 -> ((org.hl7.fhir.r5.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.r5.model.Bundle.BundleEntryComponent::getResource)
                    .collect(java.util.stream.Collectors.toList());
            default -> ((Bundle) bundle).getEntry().stream()
                    .map(Bundle.BundleEntryComponent::getResource)
                    .collect(java.util.stream.Collectors.toList());
        };
    }

    public void handleMappingIterations(final List<MappingHelper> helpers,
                                        final JsonObject jsonObject,
                                        final String modelPackage,
                                        final Spec.Version fhirVersion) {
        for (final MappingHelper mappingHelper : helpers) {
            final MappingTimer mappingTimer = MappingTimer.start();

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

            if (!shouldProcessMapping(mappingHelper, UNIDIRECTIONAL_TOFHIR, fhirVersion)) {
                continue;
            }

            if (isChildrenOnlyIteration(mappingHelper, extractedData)) {
                handleChildrenOnlyIteration(mappingHelper, relevantJsonObject, modelPackage, fhirVersion);
            } else if (mappingHelper.getManualFhirValue() != null) {
                handleHardcodedIteration(mappingHelper, modelPackage);
            } else {
                handleExtractedDataIteration(mappingHelper, extractedData, relevantJsonObject, modelPackage, fhirVersion);
            }

            metricsLogger.record("mapping.iteration",
                    "mapping=" + mappingHelper.getMappingName() + " model=" + mappingHelper.getModelMetadataName(),
                    mappingTimer.elapsedMs());
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
                                             final JsonObject relevantJsonObject,
                                             final String modelPackage,
                                             final Spec.Version fhirVersion) {
        final String fullOpenEhrFlatPath = mappingHelper.getFullOpenEhrFlatPath();
        final List<JsonObject> jsonObjects = splitByHierarchy(relevantJsonObject, fullOpenEhrFlatPath);
        for (final JsonObject object : jsonObjects) {
            final Object newRoot = resolveNewRootForChildrenIteration(mappingHelper, object, fullOpenEhrFlatPath,
                    modelPackage);
            if (newRoot == null && fullOpenEhrFlatPath != null) {
                continue;
            }
            if (mappingHelper.getManualFhirValue() != null) {
                handleHardcodedMapping(mappingHelper, newRoot);
            }
            propagateToChildrenAndRecurse(mappingHelper, newRoot, object, modelPackage, fhirVersion);
        }
    }

    /**
     * Instantiates the new FHIR root for a children-only iteration slice.
     * Returns null (signalling skip) when the flat path has no matching entries in the slice.
     */
    private Object resolveNewRootForChildrenIteration(final MappingHelper mappingHelper,
                                                      final JsonObject object,
                                                      final String fullOpenEhrFlatPath,
                                                      final String modelPackage) {
        if (fullOpenEhrFlatPath == null) {
            return toFhirInstantiator.instantiateElement(mappingHelper, null, -1, modelPackage);
        }

        final String simplifiedPath = openFhirStringUtils
                .addRegexPatternToSimplifiedFlatFormat(fullOpenEhrFlatPath)
                .replace("[n]", "");
        final List<String> allEntriesThatMatch = openFhirStringUtils.getAllEntriesThatMatch(simplifiedPath, object);

        if (allEntriesThatMatch.isEmpty()) {
            return null;
        }

        final Integer index = openFhirStringUtils.getLastIndex(allEntriesThatMatch.get(0));
        final Object instantiated = toFhirInstantiator.instantiateElement(mappingHelper, null, -1, modelPackage);

        if (instantiated instanceof List<?> listObject && index != -1) {
            return listObject.get(listObject.size() - 1);
        }
        return instantiated;
    }

    /**
     * Handles the case where a manual FHIR value is set and no extracted data exists.
     * Instantiates the element and populates it with the hardcoded value.
     */
    private void handleHardcodedIteration(final MappingHelper mappingHelper, final String modelPackage) {
        final Object instantiated = toFhirInstantiator.instantiateElement(mappingHelper, null, -1, modelPackage);
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
                                              final JsonObject relevantJsonObject,
                                              final String modelPackage,
                                              final Spec.Version fhirVersion) {
        final List<DataWithIndex> modifiableList = new ArrayList<>(extractedData);
        sortByLastIndex(modifiableList);

        for (final DataWithIndex extractedDataPoint : modifiableList) {
            final Object instantiated = toFhirInstantiator.instantiateElement(
                    mappingHelper,
                    extractedDataPoint.getData().getClass().getSimpleName(),
                    extractedDataPoint.getIndex(),
                    modelPackage);

            populateExtractedDataPoint(mappingHelper, instantiated, extractedDataPoint);
            propagateToChildrenAndRecurse(mappingHelper, instantiated, relevantJsonObject, modelPackage, fhirVersion);
        }

        if (modifiableList.isEmpty() && !mappingHelper.getChildren().isEmpty()) {
            propagateToChildrenAndRecurse(mappingHelper, mappingHelper.getGeneratingFhirRoot(), relevantJsonObject,
                    modelPackage, fhirVersion);
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
                                               final JsonObject jsonObject,
                                               final String modelPackage,
                                               final Spec.Version fhirVersion) {
        if (mappingHelper.getChildren().isEmpty()) {
            return;
        }
        mappingHelper.getChildren().forEach(child -> {
            child.setGeneratingFhirRoot(newRoot);
            child.setGeneratingFhirResource(mappingHelper.getGeneratingFhirResource());
        });
        handleMappingIterations(mappingHelper.getChildren(), jsonObject, modelPackage, fhirVersion);
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
     * Splits a flat-format composition {@link JsonObject} into one slice per occurrence of
     * {@code splitKey}.
     *
     * <p>When {@code splitKey} is {@code null} or produces no matches the original object is
     * returned as a single-element list (no split needed).
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Strip the {@code [n]} recurring marker from {@code splitKey} and build a regex via
     *       {@link OpenFhirStringUtils#addRegexPatternToSimplifiedFlatFormat}.</li>
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
        if (splitKey == null || !splitKey.endsWith(OpenFhirStringUtils.RECURRING_SYNTAX)) {
            return List.of(flatJsonObject);
        }

        // build regex from splitKey (strip [n] first so it matches real :0/:1 indices)
        final String strippedKey = splitKey.replace(OpenFhirStringUtils.RECURRING_SYNTAX,
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
