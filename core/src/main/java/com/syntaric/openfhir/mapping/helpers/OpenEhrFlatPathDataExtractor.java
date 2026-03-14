package com.syntaric.openfhir.mapping.helpers;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.parser.ValueToFHIRParser;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Extracts openEHR data from a flat-format {@link JsonObject} for a given {@link MappingHelper}.
 *
 * <p>Uses {@link MappingHelper#getFullOpenEhrFlatPathWithMatchingRegex()} to find all matching
 * flat-path keys in the composition, groups pipe-suffixed siblings into per-occurrence buckets,
 * and converts each bucket into a typed FHIR {@link org.hl7.fhir.r4.model.Base} element inside a
 * {@link DataWithIndex} based on {@link MappingHelper#getDetectedType()}.
 *
 * <p>Terminology translation is intentionally omitted — that is a concern of the mapping engine
 * layer that consumes the returned data.
 */
@Slf4j
@Component
public class OpenEhrFlatPathDataExtractor {

    private OpenFhirStringUtils openFhirStringUtils;
    private final ValueToFHIRParser valueToFHIRParser;

    @Autowired
    public OpenEhrFlatPathDataExtractor(final OpenFhirStringUtils openFhirStringUtils,
                                        final ValueToFHIRParser valueToFHIRParser) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.valueToFHIRParser = valueToFHIRParser;
    }

    /**
     * Extracts typed FHIR data from {@code flatJsonObject} for the given {@code mappingHelper}.
     *
     * @param mappingHelper the mapping whose flat-path regex and detected type drive extraction
     * @param flatJsonObject the composition in flat-path format
     * @return one {@link DataWithIndex} per matching occurrence, never {@code null}
     */
    public List<DataWithIndex> extract(final MappingHelper mappingHelper, final JsonObject flatJsonObject) {
        final String withRegex = mappingHelper.getFullOpenEhrFlatPathWithMatchingRegex();
        if (StringUtils.isBlank(withRegex)) {
            log.debug("No flat-path regex on mapping '{}'; returning empty.",
                      mappingHelper.getMappingName());
            return List.of();
        }

        final List<String> matchingKeys = openFhirStringUtils.getAllEntriesThatMatch(withRegex, flatJsonObject);
        if (matchingKeys.isEmpty()) {
            log.debug("No matching flat-path keys for mapping '{}' with regex '{}'.",
                      mappingHelper.getMappingName(), withRegex);
            return List.of();
        }

        // group pipe-suffixed siblings (|value, |code, …) into per-occurrence buckets keyed by root
        final Map<String, List<String>> grouped = openFhirStringUtils.joinValuesThatAreOne(matchingKeys);
        final List<String> possibleRmTypes = mappingHelper.getPossibleRmTypes();
        final String hardcodedType = mappingHelper.getHardcodedType();

        return grouped.entrySet().stream()
                .map(entry -> {
                    DataWithIndex dataWithIndex = toDataWithIndex(entry.getKey(), entry.getValue(), flatJsonObject, possibleRmTypes,
                            hardcodedType, mappingHelper.getFhir());
                    if(dataWithIndex != null) {
                        mappingHelper.setDetectedType(dataWithIndex.getDetectedType());
                    }
                    return dataWithIndex;
                })
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Dispatch by type
    // -----------------------------------------------------------------------

    private DataWithIndex toDataWithIndex(final String root,
                                          final List<String> keys,
                                          final JsonObject flatJson,
                                          final List<String> possibleRmTypes,
                                          final String hardcodedType,
                                          final String fhirPath) {
        final int index = openFhirStringUtils.getLastIndex(root);
        final List<String> types = StringUtils.isEmpty(hardcodedType) ? possibleRmTypes : List.of(hardcodedType);

        return valueToFHIRParser.parse(keys, types, flatJson, root, index, fhirPath);
    }
}
