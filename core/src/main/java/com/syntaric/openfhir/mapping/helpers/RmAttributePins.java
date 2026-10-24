package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.fc.FhirConnectConst;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Attributes of the openEHR data value types that a mapping can pin with a strict AQL path through an ELEMENT's
 * value ({@code items[at0004]/value/magnitude}, {@code items[at0130]/value/defining_code/code_string}), and where
 * each one lives in the flat format: a pipe attribute of the element ({@code |magnitude}, {@code |code}), or the
 * element itself when the attribute is the whole value in that format ({@code value} of a DV_TEXT, {@code magnitude}
 * of a DV_COUNT).
 *
 * <p>The flat attribute names are the ones the EHRbase SDK reads ({@code serialisation.flatencoding.std.umarshal
 * .rmunmarshaller.*RMUnmarshaller}), the RM paths those of the openEHR RM.
 */
public final class RmAttributePins {

    public enum Kind { TEXT, NUMBER, BOOLEAN }

    /**
     * @param rmPath        the attribute below the data value, as AQL ({@code defining_code/code_string})
     * @param flatAttribute its pipe attribute in the flat format, without the pipe; empty when it is the element itself
     * @param aqlTail       appended to the path in a WHERE, when the attribute is an object ({@code terminology_id})
     * @param emittedOnElement the SDK reads the pipe attribute but writes it as the element itself
     *                         ({@code parsable_value|value} comes back as {@code parsable_value})
     */
    public record Attribute(String rmType, String rmPath, String flatAttribute, Kind kind, String aqlTail,
                            boolean emittedOnElement) {

        public boolean isElementItself() {
            return flatAttribute.isEmpty();
        }
    }

    /** A path split into the ELEMENT and the attribute it pins below that ELEMENT's value. */
    public record Pin(String elementPath, String rmPath) {
    }

    private static final String VALUE = "]/value/";

    private static final Map<String, List<Attribute>> BY_TYPE = new LinkedHashMap<>();

    static {
        add(FhirConnectConst.DV_CODED_TEXT, "value", "value", Kind.TEXT);
        add(FhirConnectConst.DV_CODED_TEXT, "defining_code/code_string", "code", Kind.TEXT);
        add(FhirConnectConst.DV_CODED_TEXT, "defining_code/terminology_id", "terminology", Kind.TEXT, "/value");
        add(FhirConnectConst.DV_CODED_TEXT, "defining_code/terminology_id/value", "terminology", Kind.TEXT);
        add(FhirConnectConst.DV_TEXT, "value", "", Kind.TEXT);
        add(FhirConnectConst.DV_TEXT, "formatting", "formatting", Kind.TEXT);
        add(FhirConnectConst.DV_QUANTITY, "magnitude", "magnitude", Kind.NUMBER);
        add(FhirConnectConst.DV_QUANTITY, "units", "unit", Kind.TEXT);
        add(FhirConnectConst.DV_QUANTITY, "precision", "precision", Kind.NUMBER);
        add(FhirConnectConst.DV_QUANTITY, "units_system", "units_system", Kind.TEXT);
        add(FhirConnectConst.DV_QUANTITY, "units_display_name", "units_display_name", Kind.TEXT);
        add(FhirConnectConst.DV_COUNT, "magnitude", "", Kind.NUMBER);
        add(FhirConnectConst.DV_PROPORTION, "numerator", "numerator", Kind.NUMBER);
        add(FhirConnectConst.DV_PROPORTION, "denominator", "denominator", Kind.NUMBER);
        add(FhirConnectConst.DV_PROPORTION, "type", "type", Kind.NUMBER);
        add(FhirConnectConst.DV_PROPORTION, "precision", "precision", Kind.NUMBER);
        add(FhirConnectConst.DV_ORDINAL, "value", "ordinal", Kind.NUMBER);
        add(FhirConnectConst.DV_ORDINAL, "symbol/value", "value", Kind.TEXT);
        add(FhirConnectConst.DV_ORDINAL, "symbol/defining_code/code_string", "code", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "id", "id", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "type", "type", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "issuer", "issuer", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "assigner", "assigner", Kind.TEXT);
        addEmittedOnElement(FhirConnectConst.DV_PARSABLE, "value", "value");
        add(FhirConnectConst.DV_PARSABLE, "formalism", "formalism", Kind.TEXT);
        addEmittedOnElement(FhirConnectConst.DV_MULTIMEDIA, "uri/value", "url");
        add(FhirConnectConst.DV_MULTIMEDIA, "media_type/code_string", "mediatype", Kind.TEXT);
        add(FhirConnectConst.DV_MULTIMEDIA, "size", "size", Kind.NUMBER);
        add(FhirConnectConst.DV_MULTIMEDIA, "alternate_text", "alternatetext", Kind.TEXT);
        add(FhirConnectConst.DV_BOOL, "value", "", Kind.BOOLEAN);
        add(FhirConnectConst.DV_DATE_TIME, "value", "", Kind.TEXT);
        add(FhirConnectConst.DV_DATE, "value", "", Kind.TEXT);
        add(FhirConnectConst.DV_TIME, "value", "", Kind.TEXT);
        add(FhirConnectConst.DV_DURATION, "value", "", Kind.TEXT);
        add("DV_URI", "value", "", Kind.TEXT);
        add("DV_EHR_URI", "value", "", Kind.TEXT);
    }

    private static final Set<String> RM_PATHS = BY_TYPE.values().stream()
            .flatMap(List::stream)
            .map(Attribute::rmPath)
            .collect(Collectors.toSet());

    private RmAttributePins() {
    }

    private static void add(final String rmType, final String rmPath, final String flatAttribute, final Kind kind) {
        add(rmType, rmPath, flatAttribute, kind, "");
    }

    private static void add(final String rmType, final String rmPath, final String flatAttribute, final Kind kind,
                            final String aqlTail) {
        BY_TYPE.computeIfAbsent(rmType, t -> new java.util.ArrayList<>())
                .add(new Attribute(rmType, rmPath, flatAttribute, kind, aqlTail, false));
    }

    private static void addEmittedOnElement(final String rmType, final String rmPath, final String flatAttribute) {
        BY_TYPE.computeIfAbsent(rmType, t -> new java.util.ArrayList<>())
                .add(new Attribute(rmType, rmPath, flatAttribute, Kind.TEXT, "", true));
    }

    /** The attribute a helper built from a pinning path pins, null when it pins none. */
    public static Attribute pinnedBy(final MappingHelper helper) {
        final Pin pin = parse(helper.getFullOpenEhrPath());
        return pin == null ? null : attribute(helper.getPinnedRmType(), pin.rmPath());
    }

    /**
     * The ELEMENT and pinned attribute of {@code openEhrPath} when it ends in {@code <node>/value/<attribute>} for an
     * attribute some data value type has; null otherwise ({@code items[at0004]/value}, the whole value, included).
     */
    public static Pin parse(final String openEhrPath) {
        if (openEhrPath == null) {
            return null;
        }
        final int at = openEhrPath.lastIndexOf(VALUE);
        if (at < 0) {
            return null;
        }
        final String rmPath = openEhrPath.substring(at + VALUE.length());
        return RM_PATHS.contains(rmPath) ? new Pin(openEhrPath.substring(0, at + 1), rmPath) : null;
    }

    /** The attribute {@code rmPath} of {@code rmType}, null when that type has no such attribute. */
    public static Attribute attribute(final String rmType, final String rmPath) {
        if (rmType == null) {
            return null;
        }
        return BY_TYPE.getOrDefault(rmType, List.of()).stream()
                .filter(a -> a.rmPath().equals(rmPath))
                .findFirst().orElse(null);
    }

    /** The attribute {@code rmPath} of the first of the element's value types that has it. */
    public static Attribute resolve(final List<String> elementTypes, final String rmPath) {
        if (elementTypes == null) {
            return null;
        }
        return elementTypes.stream()
                .map(type -> attribute(type, rmPath))
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
    }

    /**
     * The flat segment naming {@code rmType} below a choice element ({@code quantity_value}), null when the element
     * holds a single type. A DV_TEXT next to a DV_CODED_TEXT is not a choice: the flat format merges it into the
     * coded text ({@code Filter.mergeDVText} in the SDK), whose attributes then sit on the element itself.
     */
    public static String typeSegment(final List<String> elementTypes, final String rmType) {
        if (elementTypes == null) {
            return null;
        }
        final List<String> distinct = elementTypes.stream()
                .distinct()
                .filter(t -> !(FhirConnectConst.DV_TEXT.equals(t) && elementTypes.contains(FhirConnectConst.DV_CODED_TEXT)))
                .toList();
        if (distinct.size() <= 1) {
            return null;
        }
        return rmType.replace("DV_", "").toLowerCase(Locale.ROOT) + "_value";
    }

    /** Every attribute per type, for tooling that offers them (Atlas path completion mirrors this table). */
    public static Map<String, List<Attribute>> all() {
        return java.util.Collections.unmodifiableMap(BY_TYPE);
    }
}
