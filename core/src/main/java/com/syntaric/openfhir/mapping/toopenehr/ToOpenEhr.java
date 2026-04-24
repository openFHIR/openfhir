package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.metrics.MappingMetricsLogger;
import com.syntaric.openfhir.metrics.MappingTimer;
import com.syntaric.openfhir.util.OpenEhrTemplateUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import ca.uhn.fhir.fhirpath.IFhirPath;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ToOpenEhr {


    private final ToOpenEhrPrePostProcessorInterface prePostProcessor;
    private final FlatJsonUnmarshaller flatJsonUnmarshaller;
    private final OpenEhrTemplateUtils openEhrApplicationScopedUtils;
    private final HelpersCreator helpersCreator;
    private final Gson gson;
    private final ToOpenEhrMappingEngine toOpenEhrMappingEngine;
    private final FhirContextRegistry fhirContextRegistry;
    private final OpenFhirStringUtils openFhirStringUtils;
    private final MappingMetricsLogger metricsLogger;

    @Autowired
    public ToOpenEhr(final ToOpenEhrPrePostProcessorInterface prePostProcessor,
                     final FlatJsonUnmarshaller flatJsonUnmarshaller,
                     final OpenEhrTemplateUtils openEhrApplicationScopedUtils,
                     final HelpersCreator helpersCreator,
                     final Gson gson,
                     final ToOpenEhrMappingEngine toOpenEhrMappingEngine,
                     final FhirContextRegistry fhirContextRegistry,
                     final OpenFhirStringUtils openFhirStringUtils,
                     final MappingMetricsLogger metricsLogger) {
        this.prePostProcessor = prePostProcessor;
        this.flatJsonUnmarshaller = flatJsonUnmarshaller;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.helpersCreator = helpersCreator;
        this.gson = gson;
        this.toOpenEhrMappingEngine = toOpenEhrMappingEngine;
        this.fhirContextRegistry = fhirContextRegistry;
        this.openFhirStringUtils = openFhirStringUtils;
        this.metricsLogger = metricsLogger;
    }



    /**
     * Mapping to a canonical format of a Composition. This method invokes the fhirToFlatJsonObject and then serializes
     * flat json path output to a canonical format.
     *
     * @param context fhir connect context being used for the mappings
     * @param resource FHIR Resource that's being mapped to openEHR
     * @return Composition that's been created based on the FHIR input and fhir connect mappers
     */
    public Composition fhirToCompositionRm(final FhirConnectContext context, final IAnyResource resource,
                                           final WebTemplate webTemplate) {
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(
                context.getContext().getTemplate().getId());
        final MappingTimer compositionRmTimer = MappingTimer.start();

        // invoke the actual mapping logic
        final JsonObject flattenedWithValues = fhirToFlatJsonObject(context, resource, webTemplate);

        // unmarshall flat path to a canonical json format
        final Composition composition = flatJsonUnmarshaller.unmarshal(gson.toJson(flattenedWithValues), webTemplate);

        prePostProcessor.postProcess(composition);

        metricsLogger.record("fhirToCompositionRm", "template=" + templateId, compositionRmTimer.elapsedMs());

        return composition;
    }

    /**
     * Main method that takes care of mapping from FHIR to openEHR. Mapping is always done to a flat path that can
     * later on be converted to a canonical JSON format.
     *
     * @param context fhir connect context mapper
     * @param resource that needs to be mapped to openEHR, can be a Bundle or a single Resource
     * @return JsonObject representing a flat path structure/format of the mapped openEHR Composition
     */
    public JsonObject fhirToFlatJsonObject(final FhirConnectContext context,
                                           final IAnyResource resource,
                                           final WebTemplate webTemplate) {
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(
                context.getContext().getTemplate().getId());

        prePostProcessor.preProcess(context, resource);

        /**
         * get start archetype
         * then find Resource that matches that archetype
         * start mapping from there. All resources need to be contained within that one so you don't loop
         * over entry of the Bundle but assume everything is linked from that one (of course that main Resource
         * could also be a Bundle
         */

        final JsonObject finalFlat = new JsonObject();

        final Spec.Version fhirVersion = context.getSpec() != null && context.getSpec().getVersion() != null
                ? context.getSpec().getVersion() : Spec.Version.R4;

        final MappingTimer helpersTimer = MappingTimer.start();
        final Map<String, List<MappingHelper>> mappersOfMainArchetype = helpersCreator.constructHelpers(templateId,
                                                                                                        context.getContext()
                                                                                                                .getStart(),
                                                                                                        context.getContext()
                                                                                                                .getArchetypes(),
                                                                                                        webTemplate);
        metricsLogger.record("fhirToFlatJsonObject.constructHelpers", "template=" + templateId, helpersTimer.elapsedMs());

        // apply limiting criteria and find the starting point within the Bundle
        final List<MappingHelper> mappingHelpersOfMainArchetype = mappersOfMainArchetype.get(
                context.getContext().getStart());
        final MappingHelper aMapper = mappingHelpersOfMainArchetype.get(0);

        final List<IAnyResource> startingResources = findStartingResource(aMapper, resource, fhirVersion);
        if (startingResources == null) {
            log.error("No starting resources found for template: {}, archetype: {}", templateId,
                      context.getContext().getStart());
            // empty flat at this point, nothing mapped
            return finalFlat;
        }

        if (startingResources.size() > 1) {
            final Map<String, Integer> indexByHierarchyPath = new HashMap<>();
            indexByHierarchyPath.put(aMapper.getOpenEhrHierarchySplitFlatPath(), 0);
            for (IAnyResource startingResource : startingResources) {
                mappingHelpersOfMainArchetype.forEach(mh -> {
                    mh.setGeneratingFhirResource(startingResource);
                    mh.setGeneratingFhirRoot(startingResource);
                });
                final MappingTimer mapTimer = MappingTimer.start();
                toOpenEhrMappingEngine.mapToOpenEhr(mappingHelpersOfMainArchetype,
                                                    finalFlat,
                                                    startingResource,
                                                    true,
                                                    indexByHierarchyPath,
                                                    fhirVersion);
                metricsLogger.record("fhirToFlatJsonObject.mapToOpenEhr",
                        "template=" + templateId + " resources=" + startingResources.size(), mapTimer.elapsedMs());
            }
        } else {
            prepareBundle(startingResources.get(0), fhirVersion);

            mappingHelpersOfMainArchetype.forEach(mh -> {
                mh.setGeneratingFhirResource(startingResources.get(0));
                mh.setGeneratingFhirRoot(startingResources.get(0));
            });
            final MappingTimer mapTimer = MappingTimer.start();
            toOpenEhrMappingEngine.mapToOpenEhr(mappingHelpersOfMainArchetype,
                                                finalFlat,
                                                startingResources.get(0),
                                                true,
                                                new HashMap<>(),
                                                fhirVersion);
            metricsLogger.record("fhirToFlatJsonObject.mapToOpenEhr",
                    "template=" + templateId + " resources=" + startingResources.size(), mapTimer.elapsedMs());
        }

        prePostProcessor.postProcess(finalFlat);

        return finalFlat;

    }


    private List<IAnyResource> findStartingResource(final MappingHelper aMapperFromStartingArchetype,
                                                     final IAnyResource toEvaluateOn,
                                                     final Spec.Version fhirVersion) {
        final IFhirPath fhirPath = fhirContextRegistry.getFhirPath(fhirVersion);
        final List<Condition> preprocessorFhirConditions = aMapperFromStartingArchetype.getPreprocessorFhirConditions();
        if (preprocessorFhirConditions == null) {
            // still we need to narrow it down because entrypoint is always a Bundle
            if (toEvaluateOn instanceof IBaseBundle) {
                if (aMapperFromStartingArchetype.getGeneratingResourceType().equals("Bundle")) {
                    return Collections.singletonList(toEvaluateOn);
                }
                return fhirPath.evaluate(toEvaluateOn,
                                         String.format("entry.resource.ofType(%s)",
                                                       aMapperFromStartingArchetype.getGeneratingResourceType()),
                        IAnyResource.class);
            } else {
                return Collections.singletonList(toEvaluateOn);
            }
        }
        final String limitingCriteriaBasedOnCoverCondition = getLimitingCriteria(preprocessorFhirConditions,
                                                                                 aMapperFromStartingArchetype.getGeneratingResourceType());

        // apply limiting factor
        final List<IAnyResource> relevantDataPoints = fhirPath.evaluate(toEvaluateOn,
                                                                        limitingCriteriaBasedOnCoverCondition,
                IAnyResource.class);

        if (relevantDataPoints.isEmpty()) {
            log.warn("No relevant resources found for {}",
                     limitingCriteriaBasedOnCoverCondition);
            return null;
        } else {
            log.info("Evaluation of {} returned {} entries that will be used for mapping.",
                     limitingCriteriaBasedOnCoverCondition,
                     relevantDataPoints.size());
            return relevantDataPoints;
        }
    }


    /**
     * Creates limiting criteria based on the FhirConnect FhirConfig Condition element.
     */
    private String getLimitingCriteria(final List<Condition> preConditions,
                                       final String mainResource) {
        if (preConditions == null || preConditions.isEmpty()) {
            return String.format("Bundle.entry.resource.ofType(%s)", mainResource);
        }
        final String existingFhirPath = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                                                                          preConditions,
                                                                          mainResource);
        final String withoutResourceType = existingFhirPath.replace(mainResource + ".", "");
        return String.format("Bundle.entry.resource.ofType(%s).where(%s)", mainResource,
                             withoutResourceType);
    }

    private IBaseBundle prepareBundle(final IAnyResource startingResource, final Spec.Version fhirVersion) {
        if (startingResource instanceof IBaseBundle) {
            return (IBaseBundle) startingResource;
        }
        switch (fhirVersion) {
            case STU3 -> {
                final org.hl7.fhir.dstu3.model.Bundle b = new org.hl7.fhir.dstu3.model.Bundle();
                b.addEntry(new org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent()
                        .setResource((org.hl7.fhir.dstu3.model.Resource) startingResource));
                return b;
            }
            case R4B -> {
                final org.hl7.fhir.r4b.model.Bundle b = new org.hl7.fhir.r4b.model.Bundle();
                b.addEntry(new org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent()
                        .setResource((org.hl7.fhir.r4b.model.Resource) startingResource));
                return b;
            }
            case R5 -> {
                final org.hl7.fhir.r5.model.Bundle b = new org.hl7.fhir.r5.model.Bundle();
                b.addEntry(new org.hl7.fhir.r5.model.Bundle.BundleEntryComponent()
                        .setResource((org.hl7.fhir.r5.model.Resource) startingResource));
                return b;
            }
            default -> {
                final Bundle b = new Bundle();
                b.addEntry(new Bundle.BundleEntryComponent().setResource((Resource) startingResource));
                return b;
            }
        }
    }
}
