package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.terminology.OfCoding;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumeration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_CLUSTER;
import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

/**
 * Class used for populating openEHR flat path Composition
 */
@Slf4j
@Component
public class OpenEhrPopulator {

    private final OpenFhirMapperUtils openFhirMapperUtils;
    private final TerminologyTranslatorInterface terminologyTranslator;
    private final PrePostOpenEhrPopulatorInterface prePostOpenEhrPopulatorInterface;
    private final OpenFhirStringUtils openFhirStringUtils;

    public static final String DATA_ABSENT_REASON_URL = "http://hl7.org/fhir/StructureDefinition/data-absent-reason";
    private static final Set<String> DATA_ABSENT_REASON_SYSTEMS = Set.of(
            "http://terminology.hl7.org/CodeSystem/data-absent-reason",
            "http://hl7.org/fhir/data-absent-reason",
            "http://terminology.hl7.org/CodeSystem/dataabsentreason"
    );
    private static final String NULL_FLAVOUR_TERMINOLOGY = "openehr";

    private enum NullFlavourAttributes {
        UNKNOWN("unknown", "253"),
        NO_INFORMATION("no information", "271"),
        MASKED("masked", "272"),
        NOT_APPLICABLE("not applicable", "273");

        private final String value;
        private final String code;

        NullFlavourAttributes(String value, String code) {
            this.value = value;
            this.code = code;
        }

        public String getValue() {
            return value;
        }

        public String getCode() {
            return code;
        }
    }

    @Autowired
    public OpenEhrPopulator(final OpenFhirMapperUtils openFhirMapperUtils,
                            final TerminologyTranslatorInterface terminologyTranslator,
                            final PrePostOpenEhrPopulatorInterface prePostOpenEhrPopulatorInterface,
                            final OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.terminologyTranslator = terminologyTranslator;
        this.prePostOpenEhrPopulatorInterface = prePostOpenEhrPopulatorInterface;
        this.openFhirStringUtils = openFhirStringUtils;
    }

    /**
     * Adds extracted value to the openEHR flat path composition represented with the 'constructingFlat' variable
     *
     * @param openEhrPath      path that should be used in the flat path composition
     * @param extractedValue   value as extracted from a FHIR object
     * @param openEhrType      openEHR type as defined in the fhir connect model mapping
     * @param constructingFlat composition in a flat path format that's being constructed
     */
    public void setOpenEhrValue(final MappingHelper mappingHelper,
                                String openEhrPath,
                                final Base extractedValue,
                                final String openEhrType,
                                final boolean isMultipleTypes, // true if there can be multiple types for a certain field, in which case we need to add the leaf type suffix (i.e. quantity_value)
                                final JsonObject constructingFlat,
                                final Terminology terminology,
                                final List<OfCoding> availableCodings) {
        prePostOpenEhrPopulatorInterface.prePopulateElement(mappingHelper,
                openEhrPath,
                extractedValue,
                openEhrType,
                constructingFlat,
                terminology,
                availableCodings);

        final Set<String> keysBefore = new HashSet<>(constructingFlat.keySet());

        setOpenEhrValueInternal(mappingHelper, openEhrPath, extractedValue, openEhrType, isMultipleTypes, constructingFlat, terminology,
                availableCodings);

        final List<String> toPaths = constructingFlat.keySet().stream()
                .filter(k -> !keysBefore.contains(k))
                .collect(java.util.stream.Collectors.toList());
        final List<String> added = toPaths.stream()
                .map(k -> constructingFlat.get(k).getAsString())
                .collect(java.util.stream.Collectors.toList());

        prePostOpenEhrPopulatorInterface.postPopulateElement(mappingHelper,
                openEhrPath,
                extractedValue,
                openEhrType,
                constructingFlat,
                terminology,
                availableCodings,
                toPaths,
                added);
    }


    private void setOpenEhrValueInternal(final MappingHelper mappingHelper,
                                         String openEhrPath,
                                         final Base extractedValue,
                                         final String openEhrType,
                                         final boolean isMultipleTypes,
                                         final JsonObject constructingFlat,
                                         final Terminology terminology,
                                         final List<OfCoding> availableCodings) {


        if (openEhrType == null) {
            return;
        }
        if (OPENEHR_TYPE_NONE.equals(openEhrType) || OPENEHR_TYPE_CLUSTER.equals(openEhrType)) {
            log.warn("Adding nothing on path {} as type is marked as NONE / CLUSTER", openEhrPath);
            return;
        }
        if (extractedValue == null) {
            log.warn("Extracted value is null");
            return;
        }
        if (openEhrPath.contains(RECURRING_SYNTAX)) {
            // still has recurring syntax due to the fact some recurring elements were not aligned or simply couldn't have been
            // in this case just set all to 0th
            openEhrPath = openEhrPath.replace(RECURRING_SYNTAX, ":0");
        }

        if (openEhrPath.contains("null_flavour")) {
            final boolean handledNullFlavour = setNullFlavourForDataAbsentReason(openEhrPath,
                    extractedValue,
                    constructingFlat);
            if (handledNullFlavour) {
                return;
            }
        }

        if (openEhrPath.contains("|")) {
            // can only be a string, ignore the actual type
            addPrimitive(extractedValue, openEhrPath, constructingFlat, terminology);
            return;
        }

        switch (openEhrType) {
            case FhirConnectConst.DV_MULTIMEDIA:
                handleDvMultimedia(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                return;
            case FhirConnectConst.DV_QUANTITY:
                final boolean addedQuantity = handleDvQuantity(mappingHelper, openEhrPath, extractedValue, constructingFlat,
                        terminology, isMultipleTypes, availableCodings);
                if (addedQuantity) {
                    return;
                }
            case FhirConnectConst.DV_ORDINAL:
                boolean addedOrdinal = handleDvOrdinal(openEhrPath, extractedValue, isMultipleTypes, constructingFlat, terminology);
                if (addedOrdinal) {
                    return;
                }
            case FhirConnectConst.DV_PROPORTION:
                boolean addedProportion = handleDvProportion(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedProportion) {
                    return;
                }
            case FhirConnectConst.DV_COUNT:
                final boolean addedCount = handleDvCount(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedCount) {
                    return;
                }
            case FhirConnectConst.DV_DATE_TIME:
                final boolean addedDateTime = handleDvDateTime(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedDateTime) {
                    return;
                }
            case FhirConnectConst.DV_INTERVAL:
                final boolean addedInterval = handleDvInterval(mappingHelper, openEhrPath, extractedValue,
                        constructingFlat, terminology, isMultipleTypes, availableCodings);
                if (addedInterval) {
                    return;
                }
            case FhirConnectConst.DV_DURATION:
                final boolean addedDuration = handleDvDuration(openEhrPath, extractedValue, isMultipleTypes, constructingFlat,
                        terminology);
                if (addedDuration) {
                    return;
                }
            case FhirConnectConst.DV_DATE:
                final boolean addedDate = handleDvDate(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedDate) {
                    return;
                }
            case FhirConnectConst.DV_TIME:
                final boolean addedTime = handleDvTime(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedTime) {
                    return;
                }
            case FhirConnectConst.DV_CODED_TEXT:
                final boolean addedCodeText = handleDvCodedText(openEhrPath, extractedValue, isMultipleTypes, constructingFlat,
                        terminology);
                if (addedCodeText) {
                    return;
                }
            case FhirConnectConst.DV_IDENTIFIER:
                final boolean addedIdentifier = handleIdentifier(openEhrPath, extractedValue, isMultipleTypes, constructingFlat,
                        terminology);
                if (addedIdentifier) {
                    return;
                }
            case FhirConnectConst.CODE_PHRASE:
                final boolean addedCode = handleCodePhrase(mappingHelper, openEhrPath, extractedValue, isMultipleTypes, constructingFlat, openEhrType,
                        terminology, availableCodings);
                if (addedCode) {
                    return;
                }
            case FhirConnectConst.DV_TEXT:
                if (extractedValue instanceof CodeableConcept codeableConcept && StringUtils.isNotEmpty(codeableConcept.getText())) {
                    addValuePerFhirType(mappingHelper, new StringType(codeableConcept.getText()), openEhrPath, isMultipleTypes, constructingFlat, FhirConnectConst.DV_TEXT,
                            terminology, availableCodings);
                } else {
                    addValuePerFhirType(mappingHelper, extractedValue, openEhrPath, isMultipleTypes, constructingFlat, FhirConnectConst.DV_TEXT,
                            terminology, availableCodings);
                }
                return;
            case FhirConnectConst.EVENT_CONTEXT:
                addValuePerFhirType(mappingHelper, extractedValue, openEhrPath, isMultipleTypes, constructingFlat, FhirConnectConst.DV_TEXT,
                        terminology, availableCodings);
                return;
            case FhirConnectConst.DV_BOOL:
                final boolean addedBool = handleDvBool(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedBool) {
                    return;
                }
            case FhirConnectConst.DV_PARTY_IDENTIFIED:
                final boolean addedPartyIdentified = handlePartyIdentifier(openEhrPath, extractedValue,
                        isMultipleTypes, constructingFlat, terminology);
                if (addedPartyIdentified) {
                    return;
                }
            case FhirConnectConst.DV_PARTY_PROXY:
                final boolean addedPartyProxy = handlePartyProxy(openEhrPath, extractedValue, isMultipleTypes, constructingFlat,
                        terminology);
                if (addedPartyProxy) {
                    return;
                }
            case FhirConnectConst.DV_EVENT:
                final boolean addedDvEvent = handleDateTimeEvent(openEhrPath, extractedValue, isMultipleTypes, constructingFlat);
                if (addedDvEvent) {
                    return;
                }
//            default:
//                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat, openEhrType);

        }
    }

    private void addPrimitive(final Base fhirValue, final String openEhrPath,
                              final JsonObject constructingFlat, final Terminology terminology) {
        final String primitiveValue = fhirValue.primitiveValue();
        addToConstructingFlat(openEhrPath, translate(primitiveValue, null, terminology), constructingFlat);
    }

    private void handleDvMultimedia(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_MULTIMEDIA_DATA)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_MULTIMEDIA_DATA;
        }
        if (value instanceof Attachment attachment) {
            int size = (attachment.getSize() == 0 && attachment.getData() != null) ? attachment.getData().length
                    : attachment.getSize();
            addToConstructingFlat(path + "|size", String.valueOf(size), flat);
            addToConstructingFlat(path + "|mediatype", attachment.getContentType(), flat);
            if (StringUtils.isNotEmpty(attachment.getUrl())) {
                addToConstructingFlat(path + "|url", attachment.getUrl(), flat);
            } else if (attachment.getData() != null) {
                final String dataString = new String(attachment.getData(), StandardCharsets.UTF_8);
                final String dataToStore = isLikelyBase64(dataString) ? dataString
                        : Base64.getEncoder().encodeToString(attachment.getData());
                addToConstructingFlat(path + "|data", dataToStore, flat);
            }
        } else {
            log.warn("openEhrType is MULTIMEDIA but extracted value is not Attachment; is {}", value.getClass());
        }
    }

    private boolean isLikelyBase64(final String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        final String trimmed = value.trim();
        if (trimmed.length() % 4 != 0) {
            return false;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            final boolean ok = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private boolean handleDvQuantity(final MappingHelper helper,
                                     String path,
                                     final Base value,
                                     final JsonObject flat,
                                     final Terminology terminology,
                                     final boolean isMultipleTypes,
                                     final List<OfCoding> availableCodings) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_QUANTITY_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_QUANTITY_VALUE;
        }
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + "|magnitude", quantity.getValue().doubleValue(), flat);
            }
            // openEHR DV_QUANTITY.units expects the canonical unit code (UCUM by default),
            // therefore prefer FHIR Quantity.code and fall back to unit display text.
            String unit = quantity.getCode();
            if (StringUtils.isBlank(unit)) {
                unit = quantity.getUnit();
            }
            addToConstructingFlat(path + "|unit", translate(unit, quantity.getSystem(), terminology), flat);
            return true;
        } else if (value instanceof Ratio ratio) {
            setOpenEhrValue(helper, path, ratio.getNumerator(), FhirConnectConst.DV_QUANTITY, isMultipleTypes, flat, terminology,
                    availableCodings);
            return true;
        } else if (value instanceof StringType stringType) {
            addToConstructingFlatDouble(path + "|magnitude", Double.valueOf(stringType.getValue()), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDvDuration(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                     final Terminology terminology) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_DURATION_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_DURATION_VALUE;
        }
        if (value instanceof StringType stringType) {
            if (StringUtils.isNotBlank(stringType.getValue())) {
                addToConstructingFlat(path, translate(stringType.getValue(), null, terminology), flat);
                return true;
            }
        } else if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlat(path, quantity.getValue().toPlainString(), flat);
                return true;
            }
        } else if (value != null && value.hasPrimitiveValue()) {
            addToConstructingFlat(path, translate(value.primitiveValue(), null, terminology), flat);
            return true;
        }
        return false;
    }

    private boolean handleDvOrdinal(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                    final Terminology terminology) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_ORDINAL_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_ORDINAL_VALUE;
        }
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlat(path + "|ordinal", quantity.getValue().toPlainString(), flat);
            }
            addToConstructingFlat(path + "|value", translate(quantity.getUnit(), quantity.getSystem(), terminology),
                    flat);
            addToConstructingFlat(path + "|code", translate(quantity.getCode(), quantity.getSystem(), terminology),
                    flat);
            return true;
        } else {
            log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvProportion(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE;
        }
        if (value instanceof Quantity quantity) {
            if ("%".equals(quantity.getCode())) {
                addToConstructingFlatDouble(path + "|denominator", 100.0, flat);
            }
            if (quantity.getValue() != null) {
                addToConstructingFlatDouble(path + "|numerator", quantity.getValue().doubleValue(), flat);
            }
            addToConstructingFlat(path + "|type", "2", flat); // hardcoded?
            return true;
        } else {
            log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvCount(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_COUNT_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_COUNT_VALUE;
        }
        if (value instanceof Quantity quantity) {
            if (quantity.getValue() != null) {
                addToConstructingFlatInteger(path, quantity.getValue().intValueExact(), flat);
            }
            return true;
        } else if (value instanceof IntegerType integerType) {
            if (integerType.getValue() != null) {
                addToConstructingFlatInteger(path, integerType.getValue(), flat);
            }
            return true;
        } else {
            log.warn("openEhrType is DV_COUNT but extracted value is not Quantity and not IntegerType; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDvDateTime(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_DATE_TIME_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_DATE_TIME_VALUE;
        }
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateTimeToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof TimeType time) {
            if (time.getValue() != null) {
                addToConstructingFlat(path, time.getValue(), flat);
            }
            return true;
        } else if (value instanceof InstantType instant) {
            if (instant.getValue() != null) {
                addToConstructingFlat(path, openFhirMapperUtils.dateTimeToString(instant.getValue()), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvDate(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_DATE_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_DATE_VALUE;
        }
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.dateToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvInterval(final MappingHelper mappingHelper, final String path, final Base value, final JsonObject flat,
                                     final Terminology terminology, final boolean isMultipleTypes, final List<OfCoding> availableCodings) {
        if (value instanceof Period period) {
            if (period.getStart() != null) {
                addToConstructingFlat(path + "/lower|value",
                        openFhirMapperUtils.dateTimeToString(period.getStart()), flat);
//                addToConstructingFlat(path + "/lower|_type", FhirConnectConst.DV_DATE_TIME, flat);
//                addToConstructingFlat(path + "/lower_included", "true", flat); unsupported in flat
            }
            if (period.getEnd() != null) {
                addToConstructingFlat(path + "/upper|value",
                        openFhirMapperUtils.dateTimeToString(period.getEnd()), flat);
//                addToConstructingFlat(path + "/upper|_type", FhirConnectConst.DV_DATE_TIME, flat);
                //               addToConstructingFlat(path + "/upper_included", "true", flat); unsupported in flat
            }
            if (period.getStart() != null || period.getEnd() != null) {
//                addToConstructingFlat(path + "|_type", FhirConnectConst.DV_INTERVAL, flat);
            }
            return true;
        } else if (value instanceof Range range) {
            boolean lowerPopulated = false;
            boolean upperPopulated = false;

            Quantity low = range.getLow();
            if (hasQuantityContent(low)) {
                handleDvQuantity(mappingHelper, path + "/lower", low, flat, terminology, isMultipleTypes, availableCodings);
                //               addToConstructingFlat(path + "/lower|_type", FhirConnectConst.DV_QUANTITY, flat);
                //               addToConstructingFlat(path + "/lower_included", "true", flat); unsupported in flat
                lowerPopulated = true;
            }

            Quantity high = range.getHigh();
            if (hasQuantityContent(high)) {
                handleDvQuantity(mappingHelper, path + "/upper", high, flat, terminology, isMultipleTypes, availableCodings);
//                addToConstructingFlat(path + "/upper|_type", FhirConnectConst.DV_QUANTITY, flat);
//                addToConstructingFlat(path + "/upper_included", "true", flat); unsupported in flat
                upperPopulated = true;
            }

            if (lowerPopulated || upperPopulated) {
                //               addToConstructingFlat(path + "|_type", FhirConnectConst.DV_INTERVAL, flat);
                return true;
            }
        }
        return false;
    }

    private boolean hasQuantityContent(final Quantity quantity) {
        if (quantity == null) {
            return false;
        }
        return quantity.getValue() != null
                || StringUtils.isNotBlank(quantity.getUnit())
                || StringUtils.isNotBlank(quantity.getCode());
    }

    private boolean handleDvTime(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_TIME_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_TIME_VALUE;
        }
        if (value instanceof DateTimeType dateTime) {
            if (dateTime.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.timeToString(dateTime.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof DateType date) {
            if (date.getValue() != null) {
                final String formattedDate = openFhirMapperUtils.timeToString(date.getValue());
                addToConstructingFlat(path, formattedDate, flat);
            }
            return true;
        } else if (value instanceof TimeType time) {
            if (time.getValue() != null) {
                addToConstructingFlat(path, time.getValue(), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvCodedText(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                      final Terminology terminology) {
//        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE)) {
//            path = path + "/" + FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE; //todo: I really don't understand when this should be here as leaf type and when not
//        }
        if (value instanceof CodeableConcept codeableConcept) {
            List<Coding> codings = codeableConcept.getCoding().stream()
                    .filter(coding -> StringUtils.isNotBlank(coding.getCode()))
                    .toList();
            if (!codings.isEmpty()) {
                // Handle the first coding as the primary coded text
                Coding primaryCoding = codings.get(0);
                addToConstructingFlat(path + "|code",
                        translate(primaryCoding.getCode(), primaryCoding.getSystem(), terminology), flat);
                setTerminology(path + "|terminology", primaryCoding, flat, terminology);
                if (primaryCoding.hasDisplay()) {
                    addToConstructingFlat(path + "|value",
                            translate(primaryCoding.getDisplay(), primaryCoding.getSystem(), terminology),
                            flat);
                } else if (primaryCoding.hasCode()) {
                    addToConstructingFlat(path + "|value",
                            translate(primaryCoding.getCode(), primaryCoding.getSystem(), terminology),
                            flat);
                }

                // Handle additional codings as mappings
                addAdditionalCodingsAsMappings(path, codings, flat, terminology);
                addToConstructingFlat(path + "|value", translate(codeableConcept.getText(), null, terminology), flat);
            } else {
                // No codings with code: skip mapping entirely
                return true;
            }
            return true;
        } else if (value instanceof Coding coding) {
            if (StringUtils.isBlank(coding.getCode())) {
                return true;
            }
            addToConstructingFlat(path + "|code", translate(coding.getCode(), coding.getSystem(), terminology), flat);
            setTerminology(path + "|terminology", coding, flat, terminology);
            setDisplay(path, coding, flat, terminology);
            addToConstructingFlat(path + "|value", translate(coding.getDisplay(), coding.getSystem(), terminology),
                    flat);
            return true;
        } else if (value instanceof StringType extractedString && path.contains("|")) {
            addToConstructingFlat(path, translate(extractedString.getValue(), null, terminology), flat);
            return true;
        } else if (value instanceof Identifier identifier) {
            addToConstructingFlat(path + "|code", translate(identifier.getValue(), identifier.getSystem(), terminology),
                    flat);
            addToConstructingFlat(path + "|terminology", identifier.getSystem(), flat);
            addToConstructingFlat(path + "|value",
                    translate(identifier.getValue(), identifier.getSystem(), terminology), flat);
            return true;
        } else if (value instanceof Enumeration enumeration) {
            addToConstructingFlat(path + "|code", translate(enumeration.getValueAsString(), null, terminology),
                    flat);
            addToConstructingFlat(path + "|terminology", translateSystem(enumeration.getValueAsString(),
                    null,
                    terminology), flat);
            addToConstructingFlat(path + "|value",
                    translate(enumeration.getValueAsString(), null, terminology), flat);
            return true;
        } else if (value instanceof StringType stringType) {
            addToConstructingFlat(path + "|value", translate(stringType.getValue(), null, terminology), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}",
                    value.getClass());
        }
        return false;
    }


    private void setDisplay(String path, Coding coding, JsonObject flat, Terminology terminology) {
        if (coding.hasDisplay()) {
            addToConstructingFlat(path + "|value", translate(coding.getDisplay(), coding.getSystem(), terminology),
                    flat);
        } else if (coding.hasCode()) {
            addToConstructingFlat(path + "|value", translate(coding.getCode(), coding.getSystem(), terminology), flat);
        }
    }


    private void setTerminology(String path, Coding coding, JsonObject flat,
                                final Terminology terminology) {
        final String translatedSystem = translateSystem(coding.getCode(),
                coding.getSystem(),
                terminology);
        if (translatedSystem == null && coding.hasVersion() & !Objects.equals(coding.getVersion(), "")) {
            String version;
            if (coding.getVersion()
                    .contains("http://snomed.info/sct")) { // might be ugly but is defined by spec like that.
                version = coding.getVersion()
                        .substring(coding.getVersion().lastIndexOf("/version/") + "/version/".length());
            } else {
                version = coding.getVersion();
            }
            addToConstructingFlat(path, coding.getSystem() + " (" + version + ")", flat);
        } else {
            addToConstructingFlat(path, translatedSystem == null ? coding.getSystem() : translatedSystem, flat);
        }
    }

    /**
     * Adds additional codings from a CodeableConcept as mappings in the openEHR flat format
     *
     * @param path    The base path for the mappings
     * @param codings The list of codings (first one is skipped as it's the primary coding)
     * @param flat    The JSON object to add the mappings to
     */
    private void addAdditionalCodingsAsMappings(String path, List<Coding> codings, JsonObject flat,
                                                Terminology terminology) {
        int mappingIndex = 0;
        for (int i = 1; i < codings.size(); i++) {
            Coding coding = codings.get(i);
            if (StringUtils.isBlank(coding.getCode())) {
                continue;
            }
            String mappingPath = path + "/_mapping:" + mappingIndex++;

            addToConstructingFlat(mappingPath + "/match", "=", flat);
            addToConstructingFlat(mappingPath + "/target|preferred_term",
                    translate(coding.getDisplay(), coding.getSystem(), terminology), flat);
            addToConstructingFlat(mappingPath + "/target|code",
                    translate(coding.getCode(), coding.getSystem(), terminology), flat);
            setTerminology(mappingPath + "/target|terminology", coding, flat, terminology);
        }
    }

    public boolean setNullFlavourForDataAbsentReason(final String openEhrPath,
                                                     final Base dataAbsentReasonValue,
                                                     final JsonObject constructingFlat) {
        if (constructingFlat == null || StringUtils.isBlank(openEhrPath) || dataAbsentReasonValue == null) {
            return false;
        }
        final String basePath = extractNullFlavourBasePath(openEhrPath);
        if (StringUtils.isBlank(basePath)) {
            return false;
        }
        final NullFlavourAttributes attributes = resolveNullFlavour(dataAbsentReasonValue);
        if (attributes == null) {
            return false;
        }

        addToConstructingFlat(basePath + "|value", attributes.getValue(), constructingFlat);
        addToConstructingFlat(basePath + "|code", attributes.getCode(), constructingFlat);
        addToConstructingFlat(basePath + "|terminology", NULL_FLAVOUR_TERMINOLOGY, constructingFlat);
        return true;
    }

    private String extractNullFlavourBasePath(final String openEhrPath) {
        final int pipeIndex = openEhrPath.indexOf('|');
        if (pipeIndex >= 0) {
            return openEhrPath.substring(0, pipeIndex);
        }
        return openEhrPath;
    }

    private NullFlavourAttributes resolveNullFlavour(final Base value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Extension extension) {
            if (!DATA_ABSENT_REASON_URL.equals(extension.getUrl())) {
                return null;
            }
            return resolveNullFlavour(extension.getValue());
        }
        if (value instanceof CodeableConcept concept) {
            for (Coding coding : concept.getCoding()) {
                final NullFlavourAttributes mapped = resolveNullFlavourFromCoding(coding);
                if (mapped != null) {
                    return mapped;
                }
            }
            return mapDataAbsentReasonCode(concept.getText());
        }
        if (value instanceof Coding coding) {
            return resolveNullFlavourFromCoding(coding);
        }
        if (value instanceof Enumeration<?> enumeration) {
            return mapDataAbsentReasonCode(enumeration.getValueAsString());
        }
        if (value instanceof CodeType codeType) {
            return mapDataAbsentReasonCode(codeType.getCode());
        }
        if (value instanceof StringType stringType) {
            return mapDataAbsentReasonCode(stringType.getValue());
        }
        if (value.hasPrimitiveValue()) {
            return mapDataAbsentReasonCode(value.primitiveValue());
        }
        return null;
    }

    private NullFlavourAttributes resolveNullFlavourFromCoding(final Coding coding) {
        if (coding == null) {
            return null;
        }
        if (StringUtils.isNotBlank(coding.getSystem())
                && !DATA_ABSENT_REASON_SYSTEMS.contains(coding.getSystem())) {
            return null;
        }
        return mapDataAbsentReasonCode(coding.getCode());
    }

    private NullFlavourAttributes mapDataAbsentReasonCode(final String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        final String normalized = code.toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "unknown":
            case "asked-unknown":
            case "temp-unknown":
            case "not-asked":
            case "not-a-number":
            case "negative-infinity":
            case "positive-infinity":
            case "not-performed":
            case "other":
                return NullFlavourAttributes.UNKNOWN;
            case "asked-declined":
            case "masked":
            case "not-permitted":
                return NullFlavourAttributes.MASKED;
            case "not-applicable":
            case "unsupported":
                return NullFlavourAttributes.NOT_APPLICABLE;
            case "as-text":
            case "error":
            default:
                return NullFlavourAttributes.NO_INFORMATION;
        }
    }

    private boolean handleIdentifier(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                     final Terminology terminology) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_IDENTIFIER_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_IDENTIFIER_VALUE;
        }
        if (value instanceof Identifier identifier) {
            addToConstructingFlat(path + "|id", translate(identifier.getValue(), identifier.getSystem(), terminology),
                    flat);
            addToConstructingFlat(path + "|issuer", normalizeIdentifierSystem(identifier.getSystem()), flat);
            addToConstructingFlat(path + "|type", buildIdentifierTypeString(identifier), flat);
            addToConstructingFlat(path + "|assigner", buildIdentifierAssignerString(identifier), flat);
            return true;
        } else if (value instanceof StringType identifier) {
            addToConstructingFlat(path + "|id", translate(identifier.getValue(), null, terminology), flat);
            return true;
        } else {
            log.warn("openEhrType is IDENTIFIER but extracted value is not Identifier; is {}", value.getClass());
        }
        return false;
    }

    private String normalizeIdentifierSystem(final String system) {
        if (StringUtils.isBlank(system)) {
            return system;
        }
        final String prefix = "http://openehr.org/identifier";
        if (system.startsWith(prefix)) {
            String trimmed = system.substring(prefix.length());
            while (trimmed.startsWith("/") || trimmed.startsWith("#")) {
                trimmed = trimmed.substring(1);
            }
            return trimmed;
        }
        return system;
    }

    private String buildIdentifierTypeString(final Identifier identifier) {
        if (identifier == null || !identifier.hasType() || !identifier.getType().hasCoding()) {
            return null;
        }
        final Coding coding = identifier.getType().getCodingFirstRep();
        final String code = coding.getCode();
        if (StringUtils.isBlank(code)) {
            return null;
        }
        if (StringUtils.isNotBlank(coding.getSystem())) {
            return coding.getSystem() + "::" + code;
        }
        return code;
    }

    private String buildIdentifierAssignerString(final Identifier identifier) {
        if (identifier == null || !identifier.hasAssigner()) {
            return null;
        }
        final Reference assigner = identifier.getAssigner();
        final String display = assigner.getDisplay();
        if (!assigner.hasIdentifier()) {
            return display;
        }
        final Identifier assignerIdentifier = assigner.getIdentifier();
        final String system = assignerIdentifier.getSystem();
        final String value = assignerIdentifier.getValue();
        final String chosenValue = StringUtils.isNotBlank(value) ? value : display;
        if (StringUtils.isBlank(chosenValue)) {
            return null;
        }
        if (StringUtils.isNotBlank(system)) {
            return system + "::" + chosenValue;
        }
        return chosenValue;
    }

    private boolean handlePartyIdentifier(final String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                          final Terminology terminology) {
        if (value instanceof StringType string) {
            addToConstructingFlat(path + "|name", translate(string.getValue(), null, terminology), flat);
            return true;
        } else if (value instanceof Identifier id) {
            addToConstructingFlat(path + "|id", translate(id.getValue(), id.getSystem(), terminology), flat);
            addToConstructingFlat(path + "|assigner", id.getSystem(), flat);
            addToConstructingFlat(path + "|type", id.getType().getText(), flat);
            // if coding.code exists, it should override the type
            addToConstructingFlat(path + "|type", id.getType().getCodingFirstRep().getCode(), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handlePartyProxy(final String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                     final Terminology terminology) {
        if (value instanceof StringType string) {
            addToConstructingFlat(path + "|name", translate(string.getValue(), null, terminology), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDateTimeEvent(final String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (value instanceof DateTimeType dateTimeType) {
            addToConstructingFlat(path + "/time", openFhirMapperUtils.dateTimeToString(dateTimeType.getValue()), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is EVENT but extracted value is not DateTimeType; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleCodePhrase(final MappingHelper mappingHelper, String path, final Base value, final boolean isMultipleTypes, final JsonObject flat,
                                     final String openEhrType, final Terminology terminology,
                                     final List<OfCoding> availableCodes) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE;
        }
        if (value instanceof Coding coding) {
            addToConstructingFlat(path + "|code", translate(coding.getCode(), coding.getSystem(), terminology), flat);
            addToConstructingFlat(path + "|value", translate(coding.getCode(), coding.getSystem(), terminology), flat);
            setTerminology(path + "|terminology", coding, flat, terminology);
            return true;
        } else if (value instanceof Extension extension) {
            setOpenEhrValue(mappingHelper, path, extension.getValue(), openEhrType, isMultipleTypes, flat, terminology, availableCodes);
            return true;
        } else if (value instanceof CodeableConcept concept) {
            setOpenEhrValue(mappingHelper, path, concept.getCodingFirstRep(), openEhrType, isMultipleTypes, flat, terminology, availableCodes);
            return true;
        } else if (value instanceof Enumeration<?> enumeration) {
            addToConstructingFlat(path + "|code", translate(enumeration.getValueAsString(), null, terminology), flat);
            addToConstructingFlat(path + "|value", translate(enumeration.getValueAsString(), null, terminology), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDvBool(String path, final Base value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_BOOLEAN_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_BOOLEAN_VALUE;
        }
        if (value instanceof BooleanType booleanType) {
            addToConstructingBoolean(path, booleanType.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_BOOL but extracted value is not BooleanType; is {}", value.getClass());
        }
        return false;
    }

    private void addValuePerFhirType(final MappingHelper mappingHelper, final Base fhirValue, String openEhrPath,
                                     final boolean isMultipleTypes, final JsonObject constructingFlat,
                                     final String openehrType, final Terminology terminology,
                                     final List<OfCoding> availableCodes) {
        if (isMultipleTypes) {
            openEhrPath = openEhrPath + "/" + FhirConnectConst.getLeafTypeForRmType(openehrType);
        }

        if (fhirValue instanceof Quantity extractedQuantity) {
            if (extractedQuantity.getValue() != null) {
                addToConstructingFlat(openEhrPath, extractedQuantity.getValue().toPlainString(), constructingFlat);
            }
        } else if (fhirValue instanceof Coding extractedCoding) {
            handleCodePhrase(mappingHelper, openEhrPath, extractedCoding, false, constructingFlat, openehrType, terminology, availableCodes);
        } else if (fhirValue instanceof CodeableConcept codeableConcept) {
            handleDvCodedText(openEhrPath, codeableConcept.getCodingFirstRep(), false, constructingFlat, terminology);
        } else if (fhirValue instanceof DateTimeType extractedQuantity) {
            String dt = openFhirMapperUtils.dateTimeToString(extractedQuantity.getValue());
            addToConstructingFlat(openEhrPath, dt, constructingFlat);
        } else if (fhirValue instanceof Annotation extracted) {
            addToConstructingFlat(openEhrPath, translate(extracted.getText(), null, terminology), constructingFlat);
        } else if (fhirValue instanceof Address extracted) {
            addToConstructingFlat(openEhrPath, translate(extracted.getText(), null, terminology), constructingFlat);
        } else if (fhirValue instanceof HumanName extracted) {
            addToConstructingFlat(openEhrPath, translate(extracted.getNameAsSingleString(), null, terminology),
                    constructingFlat);
        } else if (fhirValue instanceof Extension extracted) {
            if (extracted.getValue().hasPrimitiveValue()) {
                addValuePerFhirType(mappingHelper, extracted.getValue(), openEhrPath, false, constructingFlat, openehrType, terminology,
                        availableCodes);
            }
        } else if (fhirValue.hasPrimitiveValue()) {
            addToConstructingFlat(openEhrPath, translate(fhirValue.primitiveValue(), null, terminology),
                    constructingFlat);
        } else {
            log.error("Unsupported fhir value toString!: {}", fhirValue);
        }
    }

    private Coding translateCoding(final String value, final String system, final Terminology terminology) {
        if (StringUtils.isBlank(value) || terminology == null) {
            return null;
        }
        return terminologyTranslator.translateToOpenEhr(value, system, null, terminology, null);
    }

    private String translate(final String value, final String system, final Terminology terminology) {
        final Coding translated = translateCoding(value, system, terminology);
        if (translated != null && StringUtils.isNotBlank(translated.getCode())) {
            return translated.getCode();
        }
        return value;
    }

    private String translateSystem(final String value, final String system, final Terminology terminology) {
        final Coding translated = translateCoding(value, system, terminology);
        if (translated != null && StringUtils.isNotBlank(translated.getSystem())) {
            return translated.getSystem();
        }
        return null;
    }

    final void addToConstructingFlat(final String key, final String value, final JsonObject constructingFlat) {
        if (StringUtils.isEmpty(value)) {
            return;
        }

        if (isContextStartKey(key)) {
            toUpdateContextBoundary(key, value, constructingFlat, true);
            return;
        }

        if (isContextEndKey(key)) {
            toUpdateContextBoundary(key, value, constructingFlat, false);
            return;
        }

        log.debug("Setting value {} on path {}", value, key);
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    private boolean isContextStartKey(final String key) {
        return key.contains("/context/") && key.endsWith("start_time");
    }

    private boolean isContextEndKey(final String key) {
        if (!key.contains("/context/")) {
            return false;
        }
        return key.endsWith("_end_time") || key.endsWith("/end_time");
    }

    private void toUpdateContextBoundary(final String key,
                                         final String candidateValue,
                                         final JsonObject constructingFlat,
                                         final boolean pickEarliest) {
        final var existing = constructingFlat.get(key);
        if (existing == null || existing.isJsonNull()) {
            constructingFlat.add(key, new JsonPrimitive(candidateValue));
            return;
        }

        final String existingValue = existing.getAsString();
        final Date existingDate = openFhirMapperUtils.stringToDate(existingValue);
        final Date candidateDate = openFhirMapperUtils.stringToDate(candidateValue);

        if (existingDate == null || candidateDate == null) {
            final boolean shouldReplace = pickEarliest
                    ? candidateValue.compareTo(existingValue) < 0
                    : candidateValue.compareTo(existingValue) > 0;
            if (shouldReplace) {
                constructingFlat.add(key, new JsonPrimitive(candidateValue));
            }
            return;
        }

        final boolean shouldReplace = pickEarliest ? candidateDate.before(existingDate)
                : candidateDate.after(existingDate);
        if (shouldReplace) {
            constructingFlat.add(key, new JsonPrimitive(candidateValue));
        }
    }

    final void addToConstructingBoolean(final String key, final Boolean value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.addProperty(key, value);
    }

    final void addToConstructingFlatDouble(final String key, final Double value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingFlatInteger(final String key, final Integer value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }
}
