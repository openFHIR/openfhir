package com.syntaric.openfhir.mapping.helpers;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.parser.ValueToFHIRParser;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.StringType;
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

    private final OpenFhirStringUtils openFhirStringUtils;
    private final OpenFhirMapperUtils openFhirMapperUtils;
    private final ValueToFHIRParser valueToFHIRParser;

    @Autowired
    public OpenEhrFlatPathDataExtractor(final OpenFhirStringUtils openFhirStringUtils,
                                        final OpenFhirMapperUtils openFhirMapperUtils,
                                        final ValueToFHIRParser valueToFHIRParser) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.openFhirMapperUtils = openFhirMapperUtils;
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
        // An attribute pinned with strict AQL (.../value/magnitude) is read from that attribute only: read as the
        // element, its other attributes - or the bare element key a DV_PARSABLE's value is written as - answer for it.
        final RmAttributePins.Attribute pinned = RmAttributePins.pinnedBy(mappingHelper);
        if (pinned != null) {
            return extractPinnedAttribute(mappingHelper, flatJsonObject,
                                          pinned.isElementItself() || pinned.emittedOnElement());
        }
        final List<DataWithIndex> extracted = extractByFlatPath(mappingHelper, flatJsonObject);
        return extracted.isEmpty() ? extractPinnedAttribute(mappingHelper, flatJsonObject, false) : extracted;
    }

    private List<DataWithIndex> extractByFlatPath(final MappingHelper mappingHelper, final JsonObject flatJsonObject) {
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
                            hardcodedType, mappingHelper.getFhir(), mappingHelper.getOriginalOpenEhrPath());
                    if(dataWithIndex != null) {
                        mappingHelper.setDetectedType(dataWithIndex.getDetectedType());
                    }
                    return dataWithIndex;
                })
                .filter(d -> d != null)
                .collect(Collectors.toList());
    }

    /**
     * A path pinning one attribute of a data value is written to that pipe attribute of the element
     * ({@code active_inactive_status|code}, {@code analyte_result/quantity_value|magnitude}); it is read back from there,
     * as text the FHIR side parses into its own type. Also for the shorthand of a coded value
     * ({@code .../defining_code/code_string}, {@code .../|code}), whose flat path keeps the RM segments
     * ({@code active_inactive_status/defining_code}) no flat key has.
     *
     * @param orBareElement the attribute may (also) come as the element itself: it is the whole value in the flat
     *                      format ({@code value} of a DV_TEXT), or the SDK writes it so
     */
    private List<DataWithIndex> extractPinnedAttribute(final MappingHelper mappingHelper,
                                                       final JsonObject flatJsonObject,
                                                       final boolean orBareElement) {
        final String pipeSuffix = StringUtils.defaultString(mappingHelper.getFlatPathPipeSuffix());
        final String flatPath = mappingHelper.getFullOpenEhrFlatPath();
        if ((pipeSuffix.isEmpty() && !orBareElement) || StringUtils.isBlank(flatPath)) {
            return List.of();
        }
        final List<String> possibleRmTypes = mappingHelper.getPossibleRmTypes();
        final String rmType = possibleRmTypes == null || possibleRmTypes.isEmpty() ? null : possibleRmTypes.get(0);
        final String elementFlatPath = openFhirMapperUtils.removeAqlSuffix(flatPath, rmType)
                .replace(RECURRING_SYNTAX, "");
        List<String> keys = pipeSuffix.isEmpty() ? List.of()
                : wholeKeysMatching(openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(elementFlatPath + pipeSuffix),
                                    flatJsonObject);
        if (keys.isEmpty() && orBareElement) {
            keys = wholeKeysMatching(openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(elementFlatPath),
                                     flatJsonObject).stream()
                    .filter(key -> !key.contains("|"))
                    .collect(Collectors.toList());
        }

        final List<DataWithIndex> extracted = keys.stream()
                .filter(key -> flatJsonObject.has(key) && flatJsonObject.get(key).isJsonPrimitive())
                .map(key -> new DataWithIndex(new StringType(flatJsonObject.get(key).getAsString()),
                        openFhirStringUtils.getLastIndex(key.contains("|") ? key.substring(0, key.lastIndexOf('|')) : key),
                        key, FhirConnectConst.DV_TEXT))
                .collect(Collectors.toList());
        if (!extracted.isEmpty()) {
            mappingHelper.setDetectedType(FhirConnectConst.DV_TEXT);
        }
        return extracted;
    }

    // The flat keys the regex matches as a whole: |unit must not take |units_system for its own.
    private static List<String> wholeKeysMatching(final String regex, final JsonObject flatJsonObject) {
        final Pattern pattern = Pattern.compile(regex);
        return flatJsonObject.keySet().stream()
                .filter(key -> pattern.matcher(key).matches())
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
                                          final String fhirPath,
                                          final String originalOpenEhrPath) {
        final int index = openFhirStringUtils.getLastIndex(root);
        final List<String> types = StringUtils.isEmpty(hardcodedType) ? possibleRmTypes : List.of(hardcodedType);

        return valueToFHIRParser.parse(keys, types, flatJson, root, index, fhirPath, originalOpenEhrPath);
    }
}
