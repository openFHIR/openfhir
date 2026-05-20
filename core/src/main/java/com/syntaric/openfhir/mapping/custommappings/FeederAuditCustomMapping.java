package com.syntaric.openfhir.mapping.custommappings;

import com.google.gson.JsonObject;
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
import org.hl7.fhir.instance.model.api.IBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Custom mapping that generates a FHIR narrative (text) on the resource being built when mapping
 * openEHR→FHIR. Uses HAPI's built-in Thymeleaf narrative generator.
 */
@Slf4j
@Component
public class FeederAuditCustomMapping extends CustomMapping {

    private static final Set<String> CODES = Set.of("feederAudit");

    private final FhirContextRegistry fhirContextRegistry;
    private final ToFhirInstantiator toFhirInstantiator;

    @Autowired
    public FeederAuditCustomMapping(final FhirContextRegistry fhirContextRegistry,
                                    final ToFhirInstantiator toFhirInstantiator) {
        this.fhirContextRegistry = fhirContextRegistry;
        this.toFhirInstantiator = toFhirInstantiator;
    }

    public FeederAuditCustomMapping() {
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
