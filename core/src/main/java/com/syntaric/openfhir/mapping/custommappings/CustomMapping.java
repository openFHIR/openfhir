package com.syntaric.openfhir.mapping.custommappings;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Base;

/**
 * Base class for in-code custom mappings referenced by mappingCode in model mappings.
 * Implementations should live in com.syntaric.openfhir.mapping.custommappings.
 */
public abstract class CustomMapping {

    /**
     * Mapping codes supported by this custom mapping.
     */
    public abstract Set<String> mappingCodes();

    /**
     * Apply a custom mapping from FHIR to openEHR.
     *
     * @return true if the mapping was applied.
     */
    public boolean applyFhirToOpenEhrMapping(final MappingHelper mappingHelper,
                                             final Base fhirValue,
                                             final List<String> possibleRmTypes,
                                             final JsonObject flat,
                                             final OpenEhrPopulator populator,
                                             final OpenFhirMapperUtils mapperUtils,
                                             final OpenFhirStringUtils stringUtils) {
        return false;
    }

    /**
     * Apply a custom mapping from openEHR to FHIR.
     *
     * @return a DataWithIndex result, or null if not applicable.
     */
    public DataWithIndex applyOpenEhrToFhirMapping(final MappingHelper mappingHelper,
                                                   final List<String> joinedValues,
                                                   final JsonObject valueHolder,
                                                   final Integer lastIndex,
                                                   final String path,
                                                   final String resourceType,
                                                   final String fhirPath,
                                                   final OpenFhirStringUtils stringUtils,
                                                   final OpenFhirMapperUtils mapperUtils) {
        return null;
    }

}
