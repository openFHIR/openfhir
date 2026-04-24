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
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseBooleanDatatype;
import org.hl7.fhir.instance.model.api.IBaseCoding;
import org.hl7.fhir.instance.model.api.IBaseEnumeration;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseIntegerDatatype;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4.model.Coding;
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
                                final IBase extractedValue,
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
                                         final IBase extractedValue,
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
                if (isCodeableConcept(extractedValue) && !getCodeableConceptCodings(extractedValue).isEmpty()) {
                    handleDvCodedText(openEhrPath, extractedValue, isMultipleTypes, constructingFlat, terminology);
                } else if (isCodeableConcept(extractedValue) && StringUtils.isNotEmpty(getCodeableConceptText(extractedValue))) {
                    addValuePerFhirType(mappingHelper, primitiveStringValue(getCodeableConceptText(extractedValue)), openEhrPath, isMultipleTypes, constructingFlat, FhirConnectConst.DV_TEXT,
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

    private static boolean hasPrimitiveValue(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Base r4Base) {
            return r4Base.hasPrimitiveValue();
        } else if (value instanceof org.hl7.fhir.dstu3.model.Base stu3Base) {
            return stu3Base.hasPrimitiveValue();
        } else if (value instanceof org.hl7.fhir.r4b.model.Base r4bBase) {
            return r4bBase.hasPrimitiveValue();
        } else if (value instanceof org.hl7.fhir.r5.model.Base r5Base) {
            return r5Base.hasPrimitiveValue();
        }
        return false;
    }

    private static String getPrimitiveValue(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Base r4Base) {
            return r4Base.primitiveValue();
        } else if (value instanceof org.hl7.fhir.dstu3.model.Base stu3Base) {
            return stu3Base.primitiveValue();
        } else if (value instanceof org.hl7.fhir.r4b.model.Base r4bBase) {
            return r4bBase.primitiveValue();
        } else if (value instanceof org.hl7.fhir.r5.model.Base r5Base) {
            return r5Base.primitiveValue();
        }
        return null;
    }

    private void addPrimitive(final IBase fhirValue, final String openEhrPath,
                              final JsonObject constructingFlat, final Terminology terminology) {
        final String primitiveValue = getPrimitiveValue(fhirValue);
        addToConstructingFlat(openEhrPath, translate(primitiveValue, null, terminology), constructingFlat);
    }

    private void handleDvMultimedia(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_MULTIMEDIA_DATA)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_MULTIMEDIA_DATA;
        }
        final byte[] data;
        final int size;
        final String contentType;
        final String url;
        if (value instanceof org.hl7.fhir.r4.model.Attachment a) {
            data = a.getData(); size = a.getSize(); contentType = a.getContentType(); url = a.getUrl();
        } else if (value instanceof org.hl7.fhir.dstu3.model.Attachment a) {
            data = a.getData(); size = a.getSize(); contentType = a.getContentType(); url = a.getUrl();
        } else if (value instanceof org.hl7.fhir.r4b.model.Attachment a) {
            data = a.getData(); size = (int) a.getSize(); contentType = a.getContentType(); url = a.getUrl();
        } else if (value instanceof org.hl7.fhir.r5.model.Attachment a) {
            data = a.getData(); size = (int) a.getSize(); contentType = a.getContentType(); url = a.getUrl();
        } else {
            log.warn("openEhrType is MULTIMEDIA but extracted value is not Attachment; is {}", value.getClass());
            return;
        }
        final int effectiveSize = (size == 0 && data != null) ? data.length : size;
        addToConstructingFlat(path + "|size", String.valueOf(effectiveSize), flat);
        addToConstructingFlat(path + "|mediatype", contentType, flat);
        if (StringUtils.isNotEmpty(url)) {
            addToConstructingFlat(path + "|url", url, flat);
        } else if (data != null) {
            final String dataString = new String(data, StandardCharsets.UTF_8);
            final String dataToStore = isLikelyBase64(dataString) ? dataString
                    : Base64.getEncoder().encodeToString(data);
            addToConstructingFlat(path + "|data", dataToStore, flat);
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
                                     final IBase value,
                                     final JsonObject flat,
                                     final Terminology terminology,
                                     final boolean isMultipleTypes,
                                     final List<OfCoding> availableCodings) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_QUANTITY_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_QUANTITY_VALUE;
        }
        if (isQuantity(value)) {
            final java.math.BigDecimal qValue = getQuantityValue(value);
            if (qValue != null) {
                addToConstructingFlatDouble(path + "|magnitude", qValue.doubleValue(), flat);
            }
            // openEHR DV_QUANTITY.units expects the canonical unit code (UCUM by default),
            // therefore prefer FHIR Quantity.code and fall back to unit display text.
            String unit = getQuantityCode(value);
            if (StringUtils.isBlank(unit)) {
                unit = getQuantityUnit(value);
            }
            addToConstructingFlat(path + "|unit", translate(unit, getQuantitySystem(value), terminology), flat);
            return true;
        } else if (isRatio(value)) {
            setOpenEhrValue(helper, path, getRatioNumerator(value), FhirConnectConst.DV_QUANTITY, isMultipleTypes, flat, terminology,
                    availableCodings);
            return true;
        } else if (value instanceof IPrimitiveType<?> prim && prim.getValueAsString() != null) {
            try {
                addToConstructingFlatDouble(path + "|magnitude", Double.valueOf(prim.getValueAsString()), flat);
                return true;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}",
                value.getClass());
        return false;
    }

    private boolean handleDvDuration(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                     final Terminology terminology) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_DURATION_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_DURATION_VALUE;
        }
        if (value instanceof IPrimitiveType<?> prim) {
            final String v = prim.getValueAsString();
            if (StringUtils.isNotBlank(v)) {
                addToConstructingFlat(path, translate(v, null, terminology), flat);
                return true;
            }
        } else if (isQuantity(value)) {
            final java.math.BigDecimal qValue = getQuantityValue(value);
            if (qValue != null) {
                addToConstructingFlat(path, qValue.toPlainString(), flat);
                return true;
            }
        } else if (value != null && hasPrimitiveValue(value)) {
            addToConstructingFlat(path, translate(getPrimitiveValue(value), null, terminology), flat);
            return true;
        }
        return false;
    }

    private boolean handleDvOrdinal(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                    final Terminology terminology) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_ORDINAL_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_ORDINAL_VALUE;
        }
        if (isQuantity(value)) {
            final java.math.BigDecimal qValue = getQuantityValue(value);
            if (qValue != null) {
                addToConstructingFlat(path + "|ordinal", qValue.toPlainString(), flat);
            }
            addToConstructingFlat(path + "|value", translate(getQuantityUnit(value), getQuantitySystem(value), terminology), flat);
            addToConstructingFlat(path + "|code", translate(getQuantityCode(value), getQuantitySystem(value), terminology), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvProportion(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_PROPORTION_VALUE;
        }
        if (isQuantity(value)) {
            if ("%".equals(getQuantityCode(value))) {
                addToConstructingFlatDouble(path + "|denominator", 100.0, flat);
            }
            final java.math.BigDecimal qValue = getQuantityValue(value);
            if (qValue != null) {
                addToConstructingFlatDouble(path + "|numerator", qValue.doubleValue(), flat);
            }
            addToConstructingFlat(path + "|type", "2", flat); // hardcoded?
            return true;
        } else {
            log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity; is {}", value.getClass());
        }
        return false;
    }

    private boolean handleDvCount(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_COUNT_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_COUNT_VALUE;
        }
        if (isQuantity(value)) {
            final java.math.BigDecimal qValue = getQuantityValue(value);
            if (qValue != null) {
                addToConstructingFlatInteger(path, qValue.intValueExact(), flat);
            }
            return true;
        } else if (value instanceof IBaseIntegerDatatype integerType) {
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

    private boolean handleDvDateTime(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_DATE_TIME_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_DATE_TIME_VALUE;
        }
        final String fhirType = value != null ? value.fhirType() : null;
        if ("dateTime".equals(fhirType) || "instant".equals(fhirType)) {
            final java.util.Date date = getDateValue(value);
            if (date != null) {
                addToConstructingFlat(path, openFhirMapperUtils.dateTimeToString(date), flat);
            }
            return true;
        } else if ("date".equals(fhirType)) {
            final java.util.Date date = getDateValue(value);
            if (date != null) {
                addToConstructingFlat(path, openFhirMapperUtils.dateToString(date), flat);
            }
            return true;
        } else if ("time".equals(fhirType)) {
            final String timeStr = getPrimitiveValue(value);
            if (timeStr != null) {
                addToConstructingFlat(path, timeStr, flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvDate(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_DATE_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_DATE_VALUE;
        }
        final String fhirType = value != null ? value.fhirType() : null;
        if ("dateTime".equals(fhirType) || "date".equals(fhirType)) {
            final java.util.Date date = getDateValue(value);
            if (date != null) {
                addToConstructingFlat(path, openFhirMapperUtils.dateToString(date), flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvInterval(final MappingHelper mappingHelper, final String path, final IBase value, final JsonObject flat,
                                     final Terminology terminology, final boolean isMultipleTypes, final List<OfCoding> availableCodings) {
        if (isPeriod(value)) {
            final java.util.Date start = getPeriodStart(value);
            final java.util.Date end = getPeriodEnd(value);
            if (start != null) {
                addToConstructingFlat(path + "/lower|value",
                        openFhirMapperUtils.dateTimeToString(start), flat);
//                addToConstructingFlat(path + "/lower|_type", FhirConnectConst.DV_DATE_TIME, flat);
//                addToConstructingFlat(path + "/lower_included", "true", flat); unsupported in flat
            }
            if (end != null) {
                addToConstructingFlat(path + "/upper|value",
                        openFhirMapperUtils.dateTimeToString(end), flat);
//                addToConstructingFlat(path + "/upper|_type", FhirConnectConst.DV_DATE_TIME, flat);
                //               addToConstructingFlat(path + "/upper_included", "true", flat); unsupported in flat
            }
            if (start != null || end != null) {
//                addToConstructingFlat(path + "|_type", FhirConnectConst.DV_INTERVAL, flat);
            }
            return true;
        } else if (isRange(value)) {
            boolean lowerPopulated = false;
            boolean upperPopulated = false;

            IBase low = getRangeLow(value);
            if (low != null && isQuantityWithContent(low)) {
                handleDvQuantity(mappingHelper, path + "/lower", low, flat, terminology, isMultipleTypes, availableCodings);
                //               addToConstructingFlat(path + "/lower|_type", FhirConnectConst.DV_QUANTITY, flat);
                //               addToConstructingFlat(path + "/lower_included", "true", flat); unsupported in flat
                lowerPopulated = true;
            }

            IBase high = getRangeHigh(value);
            if (high != null && isQuantityWithContent(high)) {
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

    private boolean isQuantityWithContent(final IBase value) {
        if (!isQuantity(value)) {
            return false;
        }
        return getQuantityValue(value) != null
                || StringUtils.isNotBlank(getQuantityUnit(value))
                || StringUtils.isNotBlank(getQuantityCode(value));
    }

    private boolean handleDvTime(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_TIME_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_TIME_VALUE;
        }
        final String fhirType = value != null ? value.fhirType() : null;
        if ("dateTime".equals(fhirType) || "date".equals(fhirType)) {
            final java.util.Date date = getDateValue(value);
            if (date != null) {
                addToConstructingFlat(path, openFhirMapperUtils.timeToString(date), flat);
            }
            return true;
        } else if ("time".equals(fhirType)) {
            final String timeStr = getPrimitiveValue(value);
            if (timeStr != null) {
                addToConstructingFlat(path, timeStr, flat);
            }
            return true;
        }
        return false;
    }

    private boolean handleDvCodedText(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                      final Terminology terminology) {
//        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE)) {
//            path = path + "/" + FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE; //todo: I really don't understand when this should be here as leaf type and when not
//        }
        if (isCodeableConcept(value)) {
            List<IBaseCoding> codings = getCodeableConceptCodings(value).stream()
                    .filter(coding -> StringUtils.isNotBlank(coding.getCode()))
                    .toList();
            if (!codings.isEmpty()) {
                // Handle the first coding as the primary coded text
                IBaseCoding primaryCoding = codings.get(0);
                addToConstructingFlat(path + "|code",
                        translate(primaryCoding.getCode(), primaryCoding.getSystem(), terminology), flat);
                setTerminology(path + "|terminology", primaryCoding, flat, terminology);
                if (StringUtils.isNotBlank(primaryCoding.getDisplay())) {
                    addToConstructingFlat(path + "|value",
                            translate(primaryCoding.getDisplay(), primaryCoding.getSystem(), terminology),
                            flat);
                } else if (StringUtils.isNotBlank(primaryCoding.getCode())) {
                    addToConstructingFlat(path + "|value",
                            translate(primaryCoding.getCode(), primaryCoding.getSystem(), terminology),
                            flat);
                }

                // Handle additional codings as mappings
                addAdditionalCodingsAsMappings(path, codings, flat, terminology);
                addToConstructingFlat(path + "|value", translate(getCodeableConceptText(value), null, terminology), flat);
            } else {
                // No codings with code: skip mapping entirely
                return true;
            }
            return true;
        } else if (value instanceof IBaseCoding coding) {
            if (StringUtils.isBlank(coding.getCode())) {
                return true;
            }
            addToConstructingFlat(path + "|code", translate(coding.getCode(), coding.getSystem(), terminology), flat);
            setTerminology(path + "|terminology", coding, flat, terminology);
            setDisplay(path, coding, flat, terminology);
            addToConstructingFlat(path + "|value", translate(coding.getDisplay(), coding.getSystem(), terminology),
                    flat);
            return true;
        } else if (value instanceof IPrimitiveType<?> prim && path.contains("|")) {
            addToConstructingFlat(path, translate(prim.getValueAsString(), null, terminology), flat);
            return true;
        } else if (isIdentifier(value)) {
            addToConstructingFlat(path + "|code", translate(getIdentifierValue(value), getIdentifierSystem(value), terminology),
                    flat);
            addToConstructingFlat(path + "|terminology", getIdentifierSystem(value), flat);
            addToConstructingFlat(path + "|value",
                    translate(getIdentifierValue(value), getIdentifierSystem(value), terminology), flat);
            return true;
        } else if (value instanceof IBaseEnumeration<?> enumeration) {
            final String enumVal = enumeration.getValueAsString();
            addToConstructingFlat(path + "|code", translate(enumVal, null, terminology), flat);
            addToConstructingFlat(path + "|terminology", translateSystem(enumVal, null, terminology), flat);
            addToConstructingFlat(path + "|value", translate(enumVal, null, terminology), flat);
            return true;
        } else if (value instanceof IPrimitiveType<?> prim) {
            addToConstructingFlat(path + "|value", translate(prim.getValueAsString(), null, terminology), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}",
                    value.getClass());
        }
        return false;
    }


    private void setDisplay(String path, IBaseCoding coding, JsonObject flat, Terminology terminology) {
        if (StringUtils.isNotBlank(coding.getDisplay())) {
            addToConstructingFlat(path + "|value", translate(coding.getDisplay(), coding.getSystem(), terminology),
                    flat);
        } else if (StringUtils.isNotBlank(coding.getCode())) {
            addToConstructingFlat(path + "|value", translate(coding.getCode(), coding.getSystem(), terminology), flat);
        }
    }


    private void setTerminology(String path, IBaseCoding coding, JsonObject flat,
                                final Terminology terminology) {
        final String translatedSystem = translateSystem(coding.getCode(),
                coding.getSystem(),
                terminology);
        final String version = coding.getVersion();
        if (translatedSystem == null && StringUtils.isNotBlank(version)) {
            String displayVersion;
            if (version.contains("http://snomed.info/sct")) { // might be ugly but is defined by spec like that.
                displayVersion = version.substring(version.lastIndexOf("/version/") + "/version/".length());
            } else {
                displayVersion = version;
            }
            addToConstructingFlat(path, coding.getSystem() + " (" + displayVersion + ")", flat);
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
    private void addAdditionalCodingsAsMappings(String path, List<IBaseCoding> codings, JsonObject flat,
                                                Terminology terminology) {
        int mappingIndex = 0;
        for (int i = 1; i < codings.size(); i++) {
            IBaseCoding coding = codings.get(i);
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
                                                     final IBase dataAbsentReasonValue,
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

    private NullFlavourAttributes resolveNullFlavour(final IBase value) {
        if (value == null) {
            return null;
        }
        if (value instanceof IBaseExtension<?, ?> extension) {
            if (!DATA_ABSENT_REASON_URL.equals(extension.getUrl())) {
                return null;
            }
            return resolveNullFlavour(extension.getValue());
        }
        if (isCodeableConcept(value)) {
            for (IBaseCoding coding : getCodeableConceptCodings(value)) {
                final NullFlavourAttributes mapped = resolveNullFlavourFromCoding(coding);
                if (mapped != null) {
                    return mapped;
                }
            }
            return mapDataAbsentReasonCode(getCodeableConceptText(value));
        }
        if (value instanceof IBaseCoding coding) {
            return resolveNullFlavourFromCoding(coding);
        }
        if (value instanceof IBaseEnumeration<?> enumeration) {
            return mapDataAbsentReasonCode(enumeration.getValueAsString());
        }
        if (hasPrimitiveValue(value)) {
            return mapDataAbsentReasonCode(getPrimitiveValue(value));
        }
        return null;
    }

    private NullFlavourAttributes resolveNullFlavourFromCoding(final IBaseCoding coding) {
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

    private boolean handleIdentifier(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                     final Terminology terminology) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_IDENTIFIER_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_IDENTIFIER_VALUE;
        }
        if (isIdentifier(value)) {
            addToConstructingFlat(path + "|id", translate(getIdentifierValue(value), getIdentifierSystem(value), terminology),
                    flat);
            addToConstructingFlat(path + "|issuer", normalizeIdentifierSystem(getIdentifierSystem(value)), flat);
            addToConstructingFlat(path + "|type", buildIdentifierTypeString(value), flat);
            addToConstructingFlat(path + "|assigner", buildIdentifierAssignerString(value), flat);
            return true;
        } else if (value instanceof IPrimitiveType<?> prim) {
            addToConstructingFlat(path + "|id", translate(prim.getValueAsString(), null, terminology), flat);
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

    private String buildIdentifierTypeString(final IBase value) {
        final IBaseCoding coding = getIdentifierTypeCodingFirstRep(value);
        if (coding == null) {
            return null;
        }
        final String code = coding.getCode();
        if (StringUtils.isBlank(code)) {
            return null;
        }
        if (StringUtils.isNotBlank(coding.getSystem())) {
            return coding.getSystem() + "::" + code;
        }
        return code;
    }

    private String buildIdentifierAssignerString(final IBase value) {
        final String[] assignerInfo = getIdentifierAssignerInfo(value);
        if (assignerInfo == null) {
            return null;
        }
        // assignerInfo[0] = display, assignerInfo[1] = assignerIdentifierSystem, assignerInfo[2] = assignerIdentifierValue
        final String display = assignerInfo[0];
        final String assignerIdSystem = assignerInfo[1];
        final String assignerIdValue = assignerInfo[2];
        if (assignerIdSystem == null && assignerIdValue == null) {
            return display;
        }
        final String chosenValue = StringUtils.isNotBlank(assignerIdValue) ? assignerIdValue : display;
        if (StringUtils.isBlank(chosenValue)) {
            return null;
        }
        if (StringUtils.isNotBlank(assignerIdSystem)) {
            return assignerIdSystem + "::" + chosenValue;
        }
        return chosenValue;
    }

    private boolean handlePartyIdentifier(final String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                          final Terminology terminology) {
        if (value instanceof IPrimitiveType<?> prim) {
            addToConstructingFlat(path + "|name", translate(prim.getValueAsString(), null, terminology), flat);
            return true;
        } else if (isIdentifier(value)) {
            addToConstructingFlat(path + "|id", translate(getIdentifierValue(value), getIdentifierSystem(value), terminology), flat);
            addToConstructingFlat(path + "|assigner", getIdentifierSystem(value), flat);
            addToConstructingFlat(path + "|type", getIdentifierTypeText(value), flat);
            // if coding.code exists, it should override the type
            final IBaseCoding typeCoding = getIdentifierTypeCodingFirstRep(value);
            if (typeCoding != null) {
                addToConstructingFlat(path + "|type", typeCoding.getCode(), flat);
            }
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handlePartyProxy(final String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                     final Terminology terminology) {
        if (value instanceof IPrimitiveType<?> prim) {
            addToConstructingFlat(path + "|name", translate(prim.getValueAsString(), null, terminology), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDateTimeEvent(final String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (value != null && "dateTime".equals(value.fhirType())) {
            final java.util.Date date = getDateValue(value);
            if (date != null) {
                addToConstructingFlat(path + "/time", openFhirMapperUtils.dateTimeToString(date), flat);
            }
            return true;
        } else {
            log.warn(
                    "openEhrType is EVENT but extracted value is not DateTimeType; is {}",
                    value != null ? value.getClass() : "null");
        }
        return false;
    }

    private boolean handleCodePhrase(final MappingHelper mappingHelper, String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat,
                                     final String openEhrType, final Terminology terminology,
                                     final List<OfCoding> availableCodes) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_CODED_TEXT_VALUE;
        }
        if (value instanceof IBaseCoding coding) {
            addToConstructingFlat(path + "|code", translate(coding.getCode(), coding.getSystem(), terminology), flat);
            addToConstructingFlat(path + "|value", translate(coding.getCode(), coding.getSystem(), terminology), flat);
            setTerminology(path + "|terminology", coding, flat, terminology);
            return true;
        } else if (value instanceof IBaseExtension<?, ?> extension) {
            setOpenEhrValue(mappingHelper, path, extension.getValue(), openEhrType, isMultipleTypes, flat, terminology, availableCodes);
            return true;
        } else if (isCodeableConcept(value)) {
            setOpenEhrValue(mappingHelper, path, getCodeableConceptCodingFirstRep(value), openEhrType, isMultipleTypes, flat, terminology, availableCodes);
            return true;
        } else if (value instanceof IBaseEnumeration<?> enumeration) {
            final String enumVal = enumeration.getValueAsString();
            addToConstructingFlat(path + "|code", translate(enumVal, null, terminology), flat);
            addToConstructingFlat(path + "|value", translate(enumVal, null, terminology), flat);
            return true;
        } else {
            log.warn(
                    "openEhrType is CODE_PHRASE but extracted value is not Coding, Extension, CodeableConcept or Enumeration; is {}",
                    value.getClass());
        }
        return false;
    }

    private boolean handleDvBool(String path, final IBase value, final boolean isMultipleTypes, final JsonObject flat) {
        if (isMultipleTypes && !path.endsWith(FhirConnectConst.LEAF_TYPE_BOOLEAN_VALUE)) {
            path = path + "/" + FhirConnectConst.LEAF_TYPE_BOOLEAN_VALUE;
        }
        if (value instanceof IBaseBooleanDatatype booleanType) {
            addToConstructingBoolean(path, booleanType.getValue(), flat);
            return true;
        } else {
            log.warn("openEhrType is DV_BOOL but extracted value is not BooleanType; is {}", value.getClass());
        }
        return false;
    }

    private void addValuePerFhirType(final MappingHelper mappingHelper, final IBase fhirValue, String openEhrPath,
                                     final boolean isMultipleTypes, final JsonObject constructingFlat,
                                     final String openehrType, final Terminology terminology,
                                     final List<OfCoding> availableCodes) {
        if (isMultipleTypes) {
            openEhrPath = openEhrPath + "/" + FhirConnectConst.getLeafTypeForRmType(openehrType);
        }

        if (isQuantity(fhirValue)) {
            final java.math.BigDecimal qValue = getQuantityValue(fhirValue);
            if (qValue != null) {
                addToConstructingFlat(openEhrPath, qValue.toPlainString(), constructingFlat);
            }
        } else if (fhirValue instanceof IBaseCoding extractedCoding) {
            handleCodePhrase(mappingHelper, openEhrPath, extractedCoding, false, constructingFlat, openehrType, terminology, availableCodes);
        } else if (isCodeableConcept(fhirValue)) {
            handleDvCodedText(openEhrPath, getCodeableConceptCodingFirstRep(fhirValue), false, constructingFlat, terminology);
        } else if (fhirValue != null && "dateTime".equals(fhirValue.fhirType())) {
            final java.util.Date date = getDateValue(fhirValue);
            if (date != null) {
                addToConstructingFlat(openEhrPath, openFhirMapperUtils.dateTimeToString(date), constructingFlat);
            }
        } else if (fhirValue != null && "Annotation".equals(fhirValue.fhirType())) {
            addToConstructingFlat(openEhrPath, translate(getAnnotationText(fhirValue), null, terminology), constructingFlat);
        } else if (fhirValue != null && "Address".equals(fhirValue.fhirType())) {
            addToConstructingFlat(openEhrPath, translate(getAddressText(fhirValue), null, terminology), constructingFlat);
        } else if (fhirValue != null && "HumanName".equals(fhirValue.fhirType())) {
            addToConstructingFlat(openEhrPath, translate(getHumanNameText(fhirValue), null, terminology), constructingFlat);
        } else if (fhirValue instanceof IBaseExtension<?, ?> extracted) {
            if (hasPrimitiveValue(extracted.getValue())) {
                addValuePerFhirType(mappingHelper, extracted.getValue(), openEhrPath, false, constructingFlat, openehrType, terminology,
                        availableCodes);
            }
        } else if (hasPrimitiveValue(fhirValue)) {
            addToConstructingFlat(openEhrPath, translate(getPrimitiveValue(fhirValue), null, terminology),
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

    // ── Version-agnostic type helpers ──────────────────────────────────────────

    private static boolean isQuantity(final IBase value) {
        return value != null && "Quantity".equals(value.fhirType());
    }

    private static java.math.BigDecimal getQuantityValue(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Quantity q) return q.getValue();
        if (value instanceof org.hl7.fhir.dstu3.model.Quantity q) return q.getValue();
        if (value instanceof org.hl7.fhir.r4b.model.Quantity q) return q.getValue();
        if (value instanceof org.hl7.fhir.r5.model.Quantity q) return q.getValue();
        return null;
    }

    private static String getQuantityUnit(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Quantity q) return q.getUnit();
        if (value instanceof org.hl7.fhir.dstu3.model.Quantity q) return q.getUnit();
        if (value instanceof org.hl7.fhir.r4b.model.Quantity q) return q.getUnit();
        if (value instanceof org.hl7.fhir.r5.model.Quantity q) return q.getUnit();
        return null;
    }

    private static String getQuantityCode(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Quantity q) return q.getCode();
        if (value instanceof org.hl7.fhir.dstu3.model.Quantity q) return q.getCode();
        if (value instanceof org.hl7.fhir.r4b.model.Quantity q) return q.getCode();
        if (value instanceof org.hl7.fhir.r5.model.Quantity q) return q.getCode();
        return null;
    }

    private static String getQuantitySystem(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Quantity q) return q.getSystem();
        if (value instanceof org.hl7.fhir.dstu3.model.Quantity q) return q.getSystem();
        if (value instanceof org.hl7.fhir.r4b.model.Quantity q) return q.getSystem();
        if (value instanceof org.hl7.fhir.r5.model.Quantity q) return q.getSystem();
        return null;
    }

    private static boolean isRatio(final IBase value) {
        return value != null && "Ratio".equals(value.fhirType());
    }

    private static IBase getRatioNumerator(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Ratio r) return r.getNumerator();
        if (value instanceof org.hl7.fhir.dstu3.model.Ratio r) return r.getNumerator();
        if (value instanceof org.hl7.fhir.r4b.model.Ratio r) return r.getNumerator();
        if (value instanceof org.hl7.fhir.r5.model.Ratio r) return r.getNumerator();
        return null;
    }

    private static boolean isPeriod(final IBase value) {
        return value != null && "Period".equals(value.fhirType());
    }

    private static java.util.Date getPeriodStart(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Period p) return p.getStart();
        if (value instanceof org.hl7.fhir.dstu3.model.Period p) return p.getStart();
        if (value instanceof org.hl7.fhir.r4b.model.Period p) return p.getStart();
        if (value instanceof org.hl7.fhir.r5.model.Period p) return p.getStart();
        return null;
    }

    private static java.util.Date getPeriodEnd(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Period p) return p.getEnd();
        if (value instanceof org.hl7.fhir.dstu3.model.Period p) return p.getEnd();
        if (value instanceof org.hl7.fhir.r4b.model.Period p) return p.getEnd();
        if (value instanceof org.hl7.fhir.r5.model.Period p) return p.getEnd();
        return null;
    }

    private static boolean isRange(final IBase value) {
        return value != null && "Range".equals(value.fhirType());
    }

    private static IBase getRangeLow(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Range r) return r.getLow();
        if (value instanceof org.hl7.fhir.dstu3.model.Range r) return r.getLow();
        if (value instanceof org.hl7.fhir.r4b.model.Range r) return r.getLow();
        if (value instanceof org.hl7.fhir.r5.model.Range r) return r.getLow();
        return null;
    }

    private static IBase getRangeHigh(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Range r) return r.getHigh();
        if (value instanceof org.hl7.fhir.dstu3.model.Range r) return r.getHigh();
        if (value instanceof org.hl7.fhir.r4b.model.Range r) return r.getHigh();
        if (value instanceof org.hl7.fhir.r5.model.Range r) return r.getHigh();
        return null;
    }

    private static boolean isCodeableConcept(final IBase value) {
        return value != null && "CodeableConcept".equals(value.fhirType());
    }

    private static List<IBaseCoding> getCodeableConceptCodings(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.CodeableConcept cc)
            return new java.util.ArrayList<>(cc.getCoding());
        if (value instanceof org.hl7.fhir.dstu3.model.CodeableConcept cc)
            return new java.util.ArrayList<>(cc.getCoding());
        if (value instanceof org.hl7.fhir.r4b.model.CodeableConcept cc)
            return new java.util.ArrayList<>(cc.getCoding());
        if (value instanceof org.hl7.fhir.r5.model.CodeableConcept cc)
            return new java.util.ArrayList<>(cc.getCoding());
        return java.util.Collections.emptyList();
    }

    private static IBaseCoding getCodeableConceptCodingFirstRep(final IBase value) {
        final List<IBaseCoding> codings = getCodeableConceptCodings(value);
        return codings.isEmpty() ? null : codings.get(0);
    }

    private static String getCodeableConceptText(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.CodeableConcept cc) return cc.getText();
        if (value instanceof org.hl7.fhir.dstu3.model.CodeableConcept cc) return cc.getText();
        if (value instanceof org.hl7.fhir.r4b.model.CodeableConcept cc) return cc.getText();
        if (value instanceof org.hl7.fhir.r5.model.CodeableConcept cc) return cc.getText();
        return null;
    }

    private static boolean isIdentifier(final IBase value) {
        return value != null && "Identifier".equals(value.fhirType());
    }

    private static String getIdentifierValue(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Identifier id) return id.getValue();
        if (value instanceof org.hl7.fhir.dstu3.model.Identifier id) return id.getValue();
        if (value instanceof org.hl7.fhir.r4b.model.Identifier id) return id.getValue();
        if (value instanceof org.hl7.fhir.r5.model.Identifier id) return id.getValue();
        return null;
    }

    private static String getIdentifierSystem(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Identifier id) return id.getSystem();
        if (value instanceof org.hl7.fhir.dstu3.model.Identifier id) return id.getSystem();
        if (value instanceof org.hl7.fhir.r4b.model.Identifier id) return id.getSystem();
        if (value instanceof org.hl7.fhir.r5.model.Identifier id) return id.getSystem();
        return null;
    }

    private static String getIdentifierTypeText(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Identifier id) return id.getType().getText();
        if (value instanceof org.hl7.fhir.dstu3.model.Identifier id) return id.getType().getText();
        if (value instanceof org.hl7.fhir.r4b.model.Identifier id) return id.getType().getText();
        if (value instanceof org.hl7.fhir.r5.model.Identifier id) return id.getType().getText();
        return null;
    }

    private static IBaseCoding getIdentifierTypeCodingFirstRep(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Identifier id)
            return id.hasType() && id.getType().hasCoding() ? id.getType().getCodingFirstRep() : null;
        if (value instanceof org.hl7.fhir.dstu3.model.Identifier id)
            return id.hasType() && id.getType().hasCoding() ? id.getType().getCodingFirstRep() : null;
        if (value instanceof org.hl7.fhir.r4b.model.Identifier id)
            return id.hasType() && id.getType().hasCoding() ? id.getType().getCodingFirstRep() : null;
        if (value instanceof org.hl7.fhir.r5.model.Identifier id)
            return id.hasType() && id.getType().hasCoding() ? id.getType().getCodingFirstRep() : null;
        return null;
    }

    /** Returns [display, assignerIdSystem, assignerIdValue] or null if no assigner. */
    private static String[] getIdentifierAssignerInfo(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Identifier id) {
            if (!id.hasAssigner()) return null;
            final org.hl7.fhir.r4.model.Reference assigner = id.getAssigner();
            final String display = assigner.getDisplay();
            if (!assigner.hasIdentifier()) return new String[]{display, null, null};
            return new String[]{display, assigner.getIdentifier().getSystem(), assigner.getIdentifier().getValue()};
        }
        if (value instanceof org.hl7.fhir.dstu3.model.Identifier id) {
            if (!id.hasAssigner()) return null;
            final org.hl7.fhir.dstu3.model.Reference assigner = id.getAssigner();
            final String display = assigner.getDisplay();
            if (!assigner.hasIdentifier()) return new String[]{display, null, null};
            return new String[]{display, assigner.getIdentifier().getSystem(), assigner.getIdentifier().getValue()};
        }
        if (value instanceof org.hl7.fhir.r4b.model.Identifier id) {
            if (!id.hasAssigner()) return null;
            final org.hl7.fhir.r4b.model.Reference assigner = id.getAssigner();
            final String display = assigner.getDisplay();
            if (!assigner.hasIdentifier()) return new String[]{display, null, null};
            return new String[]{display, assigner.getIdentifier().getSystem(), assigner.getIdentifier().getValue()};
        }
        if (value instanceof org.hl7.fhir.r5.model.Identifier id) {
            if (!id.hasAssigner()) return null;
            final org.hl7.fhir.r5.model.Reference assigner = id.getAssigner();
            final String display = assigner.getDisplay();
            if (!assigner.hasIdentifier()) return new String[]{display, null, null};
            return new String[]{display, assigner.getIdentifier().getSystem(), assigner.getIdentifier().getValue()};
        }
        return null;
    }

    private static java.util.Date getDateValue(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.BaseDateTimeType dt) return dt.getValue();
        if (value instanceof org.hl7.fhir.dstu3.model.BaseDateTimeType dt) return dt.getValue();
        if (value instanceof org.hl7.fhir.r4b.model.BaseDateTimeType dt) return dt.getValue();
        if (value instanceof org.hl7.fhir.r5.model.BaseDateTimeType dt) return dt.getValue();
        return null;
    }

    private static String getAnnotationText(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Annotation a) return a.getText();
        if (value instanceof org.hl7.fhir.dstu3.model.Annotation a) return a.getText();
        if (value instanceof org.hl7.fhir.r4b.model.Annotation a) return a.getText();
        if (value instanceof org.hl7.fhir.r5.model.Annotation a) return a.getText();
        return null;
    }

    private static String getAddressText(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.Address a) return a.getText();
        if (value instanceof org.hl7.fhir.dstu3.model.Address a) return a.getText();
        if (value instanceof org.hl7.fhir.r4b.model.Address a) return a.getText();
        if (value instanceof org.hl7.fhir.r5.model.Address a) return a.getText();
        return null;
    }

    private static String getHumanNameText(final IBase value) {
        if (value instanceof org.hl7.fhir.r4.model.HumanName n) return n.getNameAsSingleString();
        if (value instanceof org.hl7.fhir.dstu3.model.HumanName n) return n.getNameAsSingleString();
        if (value instanceof org.hl7.fhir.r4b.model.HumanName n) return n.getNameAsSingleString();
        if (value instanceof org.hl7.fhir.r5.model.HumanName n) return n.getNameAsSingleString();
        return null;
    }

    /** Wraps a plain string as a version-agnostic IPrimitiveType for use as IBase. */
    private static IPrimitiveType<String> primitiveStringValue(final String value) {
        return new org.hl7.fhir.r4.model.StringType(value);
    }
}
