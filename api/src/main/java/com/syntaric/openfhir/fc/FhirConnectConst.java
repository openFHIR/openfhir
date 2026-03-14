package com.syntaric.openfhir.fc;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility class containing all constants defined by the FHIR Connect specification
 */
public class FhirConnectConst {

    public static final String FHIR_RESOURCE_FC = "$resource";
    public static final String FHIR_BACKBONE_ELEMENT = "BackboneElement";
    public static final String FHIR_ROOT_FC = "$fhirRoot";
    public static final String THIS = "$this";
    public static final String OPENEHR_ARCHETYPE_FC = "$archetype";
    public static final String OPENEHR_COMPOSITION_FC = "$composition";
    public static final String OPENEHR_ROOT_FC = "$openehrRoot";

    public static final String OPENEHR_TYPE_NONE = "NONE";
    public static final String OPENEHR_TYPE_CLUSTER = "CLUSTER";
    public static final String REFERENCE = "$reference";
    public static final String DV_MULTIMEDIA = "DV_MULTIMEDIA";
    public static final String DV_QUANTITY = "DV_QUANTITY";
    public static final String EVENT_CONTEXT = "EVENT_CONTEXT";
    public static final String DV_PARTY_IDENTIFIED = "PARTY_IDENTIFIED";
    public static final String DV_PARTY_PROXY = "PARTY_PROXY";
    public static final String DV_EVENT = "EVENT";
    public static final String POINT_EVENT = "POINT_EVENT";
    public static final String INTERVAL_EVENT = "INTERVAL_EVENT";

    public static final String DV_ORDINAL = "DV_ORDINAL";
    public static final String DV_PROPORTION = "DV_PROPORTION";
    public static final String DV_COUNT = "DV_COUNT";
    public static final String DV_DATE_TIME = "DV_DATE_TIME";
    public static final String DV_TIME = "DV_TIME";
    public static final String DV_DATE = "DV_DATE";
    public static final String DV_DURATION = "DV_DURATION";
    public static final String DV_INTERVAL = "DV_INTERVAL";
    public static final String DV_CODED_TEXT = "DV_CODED_TEXT";
    public static final String ELEMENT = "ELEMENT";
    public static final String CODE_PHRASE = "CODE_PHRASE";
    public static final String DV_TEXT = "DV_TEXT";
    public static final String OPENEHR_CODE = "code";
    public static final String OPENEHR_TERMINOLOGY = "terminology";
    public static final String OPENEHR_VALUE = "value";
    public static final String DV_BOOL = "DV_BOOLEAN";
    public static final String DV_IDENTIFIER = "DV_IDENTIFIER";
    public static final String UNIDIRECTIONAL_TOFHIR = "openehr->fhir";
    public static final String UNIDIRECTIONAL_TOOPENEHR = "fhir->openehr";
    public static final String CONDITION_OPERATOR_ONE_OF = "one of";
    public static final String CONDITION_OPERATOR_NOT_OF = "not of";
    public static final String CONDITION_OPERATOR_EMPTY = "empty";
    public static final String CONDITION_OPERATOR_TYPE = "type";
    public static final String CONDITION_OPERATOR_NOT_EMPTY = "not empty";

    // leaf types below:



    public static String getLeafTypeForRmType(final String rmType) {
        if (rmType == null) {
            return null;
        }
        return switch (rmType) {
            case DV_TEXT -> LEAF_TYPE_TEXT_VALUE;
            case DV_CODED_TEXT  -> LEAF_TYPE_CODED_TEXT_VALUE;
            case DV_ORDINAL -> LEAF_TYPE_ORDINAL_VALUE;
            case DV_BOOL -> LEAF_TYPE_BOOLEAN_VALUE;
            case DV_IDENTIFIER -> LEAF_TYPE_IDENTIFIER_VALUE;
            case DV_DATE -> LEAF_TYPE_DATE_VALUE;
            case DV_TIME -> LEAF_TYPE_TIME_VALUE;
            case DV_DATE_TIME -> LEAF_TYPE_DATE_TIME_VALUE;
            case DV_DURATION -> LEAF_TYPE_DURATION_VALUE;
            case DV_COUNT -> LEAF_TYPE_COUNT_VALUE;
            case DV_QUANTITY -> LEAF_TYPE_QUANTITY_VALUE;
            case DV_PROPORTION -> LEAF_TYPE_PROPORTION_VALUE;
            case DV_MULTIMEDIA -> LEAF_TYPE_MULTIMEDIA_DATA;
            default -> rmType.replace("DV_", "").toLowerCase(Locale.ROOT) + "_value"; // is this ok for all?
        };
    }

    // DV_TEXT
    public static final String LEAF_TYPE_TEXT_VALUE = "text_value";

    // DV_CODED_TEXT
    public static final String LEAF_TYPE_CODED_TEXT_VALUE = "coded_text_value";

    // DV_BOOLEAN
    public static final String LEAF_TYPE_BOOLEAN_VALUE = "boolean_value";

    // DV_IDENTIFIER
    public static final String LEAF_TYPE_IDENTIFIER_VALUE = "identifier_value";

    // DV_URI
    public static final String LEAF_TYPE_URI_VALUE = "uri_value";

    // DV_EHR_URI
    public static final String LEAF_TYPE_EHR_URI_VALUE = "ehr_uri_value";

    // DV_DATE
    public static final String LEAF_TYPE_DATE_VALUE = "date_value";

    // DV_TIME
    public static final String LEAF_TYPE_TIME_VALUE = "time_value";

    // DV_DATE_TIME
    public static final String LEAF_TYPE_DATE_TIME_VALUE = "date_time_value";

    // DV_DURATION
    public static final String LEAF_TYPE_DURATION_VALUE = "duration_value";

    // DV_COUNT
    public static final String LEAF_TYPE_COUNT_VALUE = "count_value";

    // DV_QUANTITY
    public static final String LEAF_TYPE_QUANTITY_VALUE = "quantity_value";

    // DV_PROPORTION
    public static final String LEAF_TYPE_PROPORTION_VALUE = "proportion_value";

    // DV_ORDINAL
    public static final String LEAF_TYPE_ORDINAL_VALUE = "ordinal_value";

    // DV_PARSABLE
    public static final String LEAF_TYPE_PARSABLE_VALUE = "parsable_value";

    // DV_STATE
    public static final String LEAF_TYPE_STATE_VALUE = "state_value";

    // DV_MULTIMEDIA
    public static final String LEAF_TYPE_MULTIMEDIA_DATA = "multimedia_data";


    public static final List<String> OPENEHR_INVALID_PATH_RM_TYPES = Arrays.asList("HISTORY", "EVENT", "ITEM_TREE",
                                                                                   "POINT_EVENT", "POINT_INTERVAL");
    public static final List<String> OPENEHR_UNDERSCORABLES = Arrays.asList("health_care_facility",
                                                                            "end_time"); // certain keywords are reserved and as such need to be prefixed with an underscore when constructing flat paths so ehrbase (un)marshaller can handle it
    public static final Set<String> OPENEHR_CONSISTENT_SET = Set.of(DV_TEXT, DV_CODED_TEXT, "FEEDER_AUDIT");

}
