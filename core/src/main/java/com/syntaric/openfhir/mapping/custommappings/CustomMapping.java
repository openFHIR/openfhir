package com.syntaric.openfhir.mapping.custommappings;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBase;
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
     * Extracts the base code from a raw mapping code that may carry arguments,
     * e.g. {@code "generateNarrative(text)"} → {@code "generateNarrative"}.
     */
    public static String extractCode(final String rawCode) {
        if (rawCode == null) {
            return null;
        }
        final int paren = rawCode.indexOf('(');
        return paren < 0 ? rawCode : rawCode.substring(0, paren);
    }

    /**
     * Extracts the first argument from a raw mapping code, or {@code null} if none is present,
     * e.g. {@code "generateNarrative(text)"} → {@code "text"}.
     */
    public static String extractArgument(final String rawCode) {
        return extractArgumentAt(rawCode, 0);
    }

    /**
     * Extracts the argument at the given zero-based index from a raw mapping code,
     * or {@code null} if the argument at that index is absent.
     * e.g. {@code "generateNarrative(entry, http://hl7.org/fhir/StructureDefinition/Condition)"} with index 1
     * → {@code "http://hl7.org/fhir/StructureDefinition/Condition"}.
     */
    public static String extractArgumentAt(final String rawCode, final int index) {
        if (rawCode == null) {
            return null;
        }
        final int open = rawCode.indexOf('(');
        final int close = rawCode.lastIndexOf(')');
        if (open < 0 || close <= open + 1) {
            return null;
        }
        final String[] parts = rawCode.substring(open + 1, close).split(",", -1);
        if (index >= parts.length) {
            return null;
        }
        final String value = parts[index].trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Apply a custom mapping from FHIR to openEHR.
     *
     * @return true if the mapping was applied.
     */
    public boolean applyFhirToOpenEhrMapping(final MappingHelper mappingHelper,
                                             final IBase fhirValue,
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
