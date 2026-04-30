package com.syntaric.openfhir.mapping.custommappings;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
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

import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Custom mapping that generates a FHIR narrative (text) on the resource being built when mapping
 * openEHR→FHIR. Uses HAPI's built-in Thymeleaf narrative generator.
 */
@Slf4j
@Component
public class GenerateNarrativeCustomMapping extends CustomMapping {

    private static final Set<String> CODES = Set.of("generateNarrative");

    private final FhirContextRegistry fhirContextRegistry;
    private final ToFhirInstantiator toFhirInstantiator;

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

        if(StringUtils.isEmpty(mappingHelper.getFhir()) || FhirConnectConst.FHIR_RESOURCE_FC.equals(mappingHelper.getOriginalFhirPath())) {
            generateNarrativeOnResource((IBaseResource) mappingHelper.getGeneratingFhirRoot()); // todo test
            return null;
        }

//        Object instantiated = toFhirInstantiator.instantiateElement(
//                mappingHelper,
//                null,
//                -1,
//                version.modelPackage());
//
//        try {
//            new DefaultThymeleafNarrativeGenerator().populateResourceNarrative(ctx, (IBaseResource) resource);
//        } catch (Exception e) {
//            log.warn("generateNarrative: narrative generation failed: {}", e.getMessage());
//        }
        return null;
    }

    private void generateNarrativeOnResource(final IBaseResource resource) {
        try {
            final Spec.Version version = detectVersion(resource);
            final FhirContext ctx = fhirContextRegistry.getContext(version);
            new DefaultThymeleafNarrativeGenerator().populateResourceNarrative(ctx, resource);
        } catch (Exception e) {
            log.warn("generateNarrative: narrative generation failed: {}", e.getMessage());
        }
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
