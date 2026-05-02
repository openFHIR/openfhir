package com.syntaric.openfhir.mapping.custommappings;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.narrative.CustomThymeleafNarrativeGenerator;
import ca.uhn.fhir.util.BundleBuilder;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.tofhir.ToFhirInstantiator;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.FhirInstanceCreator;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Custom mapping that generates a FHIR narrative (text) on the resource being built when mapping
 * openEHR→FHIR. Uses HAPI's built-in Thymeleaf narrative generator.
 */
@Slf4j
@Component
public class GenerateNarrativeCustomMapping extends CustomMapping {

    private static final Set<String> CODES = Set.of("generateNarrative");

    private static final String HAPI_DEFAULT_NARRATIVES = "classpath:ca/uhn/fhir/narrative/narratives.properties";
    private static final String OPENFHIR_DEFAULT_NARRATIVES = "classpath:openfhir-narratives.properties";

    private final FhirContextRegistry fhirContextRegistry;
    private final ToFhirInstantiator toFhirInstantiator;

    @Value("${openfhir.narrative.properties-file:}")
    private String narrativePropertiesFile;

    @Autowired
    public GenerateNarrativeCustomMapping(final FhirContextRegistry fhirContextRegistry,
                                          final ToFhirInstantiator toFhirInstantiator) {
        this.fhirContextRegistry = fhirContextRegistry;
        this.toFhirInstantiator = toFhirInstantiator;
    }

    public GenerateNarrativeCustomMapping() {
        this.fhirContextRegistry = new FhirContextRegistry();
        this.toFhirInstantiator = new ToFhirInstantiator(new FhirInstanceCreator(new OpenFhirStringUtils(),
                new FhirInstanceCreatorUtility(new OpenFhirStringUtils())));
    }

    @Override
    public Set<String> mappingCodes() {
        return CODES;
    }

    @Override
    public DataWithIndex applyOpenEhrToFhirMapping(final MappingHelper mappingHelper,
                                                   final List<String> joinedValues,
                                                   final JsonObject valueHolder,
                                                   final Integer lastIndex,
                                                   final String path,
                                                   final String resourceType,
                                                   final String fhirPath,
                                                   final OpenFhirStringUtils stringUtils,
                                                   final OpenFhirMapperUtils mapperUtils) {

        try {
            final String fhirPathForNarrativeGeneration = extractArgument(mappingHelper.getProgrammedMapping());
            if (fhirPathForNarrativeGeneration != null) {
                log.debug("generateNarrative: argument '{}' provided but not yet used", fhirPathForNarrativeGeneration);
            }

            final Spec.Version version = detectVersion(mappingHelper.getGeneratingFhirResource());

            // get resource to generate narrative of
            final IBaseResource resourceToGenerateNarrativeOf = getResourceToGenerateNarrativeOf(mappingHelper,
                    fhirPathForNarrativeGeneration, version);

            final String generatedNarrative = generateNarrative(resourceToGenerateNarrativeOf, version);

            Object instantiated = toFhirInstantiator.instantiateElement(mappingHelper, null, -1, version.modelPackage()); // is it ok to be -1?
            if (instantiated instanceof INarrative narrative) {
                try {
                    narrative.setStatusAsString("generated");
                    narrative.setDivAsString(generatedNarrative);
                } catch (final Exception e) {
                    log.error("Exception trying to set narrative on div", e);
                }
            } else if (instantiated instanceof IPrimitiveType<?> primitiveType) {
                primitiveType.setValueAsString(generatedNarrative);
            }
        } catch (final Exception e) {
            log.error("Exception trying to generateNarrative; gracefully continuing with mappings", e);
        }

        return null;
    }

    IBaseResource getResourceToGenerateNarrativeOf(final MappingHelper mappingHelper,
                                                   final String fhirPathForNarrativeGeneration,
                                                   final Spec.Version version) {
        if (fhirPathForNarrativeGeneration.equals(FhirConnectConst.FHIR_RESOURCE_FC)) {
            return (IBaseResource) mappingHelper.getGeneratingFhirResource();
        }
        if (fhirPathForNarrativeGeneration.equals(FhirConnectConst.FHIR_ROOT_FC)) {
            return (IBaseResource) mappingHelper.getGeneratingFhirRoot();
        }
        final IFhirPath fhirPath = fhirContextRegistry.getFhirPath(version);
        boolean fhirPathOnResource = fhirPathForNarrativeGeneration.startsWith(FhirConnectConst.FHIR_RESOURCE_FC);
        final Object generatingFhirRoot = fhirPathOnResource ?
                mappingHelper.getGeneratingFhirResource() : mappingHelper.getGeneratingFhirRoot();

        final String fhirPathForEvaluation = fhirPathOnResource ? fhirPathForNarrativeGeneration.replace(String.format("%s.",
                FhirConnectConst.FHIR_RESOURCE_FC), "") : fhirPathForNarrativeGeneration;

        final BundleBuilder builder = new BundleBuilder(fhirContextRegistry.getContext(version));
        if (generatingFhirRoot instanceof List listOfGeneratedResources) {
            for (Object listOfGeneratedResource : listOfGeneratedResources) {
                final List<IBase> evaluated = fhirPath.evaluate((IBase) listOfGeneratedResource, fhirPathForEvaluation, IBase.class);
                evaluated.forEach(ev -> {
                    List<? extends IBaseResource> iBases = resolveReference(ev, mappingHelper, fhirPath);
                    iBases.forEach(builder::addCollectionEntry);
                });
            }
        } else {
            final List<IBase> evaluated = fhirPath.evaluate((IBase) generatingFhirRoot, fhirPathForEvaluation, IBase.class);
            if (evaluated.size() == 1) {
                return resolveReference(evaluated.get(0), mappingHelper, fhirPath).get(0);
            }
            evaluated.forEach(ev -> {
                List<? extends IBaseResource> iBases = resolveReference(ev, mappingHelper, fhirPath);
                iBases.forEach(builder::addCollectionEntry);
            });
        }
        return builder.getBundle();
    }

    private List<? extends IBaseResource> resolveReference(final IBase toResolveOn,
                                                           final MappingHelper mappingHelper,
                                                           final IFhirPath versionedFhirPath) {
        if ("BundleEntryComponent".equals(toResolveOn.getClass().getSimpleName())
                || toResolveOn instanceof IBaseReference) {
            try {
                final Object resource = toResolveOn.getClass().getMethod("getResource").invoke(toResolveOn);
                return resource instanceof IBase ? List.of((IBaseResource) resource) : Collections.emptyList();
            } catch (final Exception e) {
                log.error("Could not get resource from BundleEntryComponent", e);
                return Collections.emptyList();
            }
        }

        if (FhirConnectConst.REFERENCE.equals(mappingHelper.getOriginalOpenEhrPath())) {
            return versionedFhirPath.evaluate(toResolveOn, "resolve()", IBaseResource.class);
        }
        if (toResolveOn instanceof IBaseResource) {
            return List.of((IBaseResource) toResolveOn);
        }
        return Collections.emptyList();
    }

    private String generateNarrative(final IBaseResource resource,
                                     final Spec.Version version) {
        try {
            final FhirContext ctx = fhirContextRegistry.getContext(version);
            final CustomThymeleafNarrativeGenerator generator;
            if (StringUtils.isNotBlank(narrativePropertiesFile)) {
                generator = new CustomThymeleafNarrativeGenerator(narrativePropertiesFile, HAPI_DEFAULT_NARRATIVES, OPENFHIR_DEFAULT_NARRATIVES);
            } else {
                generator = new CustomThymeleafNarrativeGenerator(HAPI_DEFAULT_NARRATIVES, OPENFHIR_DEFAULT_NARRATIVES);
            }
            return generator.generateResourceNarrative(ctx, resource);
        } catch (Exception e) {
            log.warn("generateNarrative: narrative generation failed: {}", e.getMessage());
        }
        return null;
    }

    private Spec.Version detectVersion(final IBase resource) {
        if (resource instanceof org.hl7.fhir.dstu3.model.Resource) {
            return Spec.Version.STU3;
        }
        if (resource instanceof org.hl7.fhir.r4b.model.Resource) {
            return Spec.Version.R4B;
        }
        if (resource instanceof org.hl7.fhir.r5.model.Resource) {
            return Spec.Version.R5;
        }
        return Spec.Version.R4;
    }
}
