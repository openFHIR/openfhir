package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.fc.FhirConnectConst;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The attributes of the openEHR data value types that a mapping or a condition can address below a data value, and
 * where each one lives in the flat format: a pipe attribute ({@code |magnitude}, {@code |code}), or the node itself
 * when the attribute is the whole value in that format ({@code value} of a DV_TEXT, {@code magnitude} of a
 * DV_COUNT). This is the one place that knows them; the flat path, the pipe, the value read back and the AQL of
 * a path ending in an attribute all come from here.
 *
 * <p>An attribute is addressed below its holder in any of these spellings:
 * <ul>
 *     <li>strict AQL through an ELEMENT's value: {@code items[at0004]/value/magnitude},
 *     {@code items[at0130]/value/defining_code/code_string}</li>
 *     <li>the same without the ELEMENT's {@code value}: {@code items[at0130]/defining_code/code_string},
 *     {@code items[at0004]/units}; a bare {@code value} there is the ELEMENT's whole data value, not an
 *     attribute</li>
 *     <li>on a node that is a data value itself: {@code null_flavour/defining_code/code_string},
 *     {@code ism_transition/current_state/value}</li>
 *     <li>the flat pipe attribute: {@code items[at0130]/|code}</li>
 *     <li>an alias some attributes have: {@code defining_code} and {@code code_string} for
 *     {@code defining_code/code_string}, {@code terminology_id} for {@code defining_code/terminology_id} ...</li>
 * </ul>
 * Which attribute it is depends on the holder's type, so a spelling only becomes an attribute once
 * {@link AqlToFlatPathConverter} has found the holder in the template.
 *
 * <p>The flat attribute names are the ones the EHRbase SDK reads ({@code serialisation.flatencoding.std.umarshal
 * .rmunmarshaller.*RMUnmarshaller}), the RM paths those of the openEHR RM.
 */
public final class RmAttributePins {

    public enum Kind { TEXT, NUMBER, BOOLEAN }

    /**
     * @param rmPath           the attribute below the data value, as AQL ({@code defining_code/code_string})
     * @param flatAttribute    its pipe attribute in the flat format, without the pipe; empty when it is the node itself
     * @param aqlTail          appended to the path in a WHERE, when the attribute is an object ({@code terminology_id})
     * @param emittedOnElement the SDK reads the pipe attribute but writes it as the node itself
     *                         ({@code parsable_value|value} comes back as {@code parsable_value})
     * @param aliases          other spellings of {@code rmPath} ({@code code_string})
     */
    public record Attribute(String rmType, String rmPath, String flatAttribute, Kind kind, String aqlTail,
                            boolean emittedOnElement, List<String> aliases) {

        public boolean isElementItself() {
            return flatAttribute.isEmpty();
        }

        boolean isSpelled(final String spelling) {
            return rmPath.equals(spelling) || aliases.contains(spelling);
        }
    }

    /**
     * A path ending in a flat pipe attribute, split: {@code items[at0004]/|code} and {@code items[at0004]|code} are
     * {@code items[at0004]} and {@code code}.
     */
    public record PathPipe(String path, String pipe) {
    }

    /**
     * A path split into the node holding a data value and the attribute it addresses below it.
     *
     * @param holderPath   the ELEMENT, or the node that is a data value itself ({@code null_flavour})
     * @param rmPath       the attribute as written below the data value, an alias included; null for a pipe
     * @param pipe         the flat pipe attribute as written ({@code code} of {@code /|code}); null otherwise
     * @param throughValue written through the ELEMENT's value ({@code items[at0004]/value/magnitude})
     */
    public record Pin(String holderPath, String rmPath, String pipe, boolean throughValue) {
    }

    private static final String VALUE = "value";

    private static final Map<String, List<Attribute>> BY_TYPE = new LinkedHashMap<>();

    static {
        add(FhirConnectConst.DV_CODED_TEXT, "value", "value", Kind.TEXT);
        add(FhirConnectConst.DV_CODED_TEXT, "defining_code/code_string", "code", Kind.TEXT, "", false,
            "defining_code", "code_string");
        add(FhirConnectConst.DV_CODED_TEXT, "defining_code/terminology_id", "terminology", Kind.TEXT, "/value", false,
            "terminology_id");
        add(FhirConnectConst.DV_CODED_TEXT, "defining_code/terminology_id/value", "terminology", Kind.TEXT, "", false,
            "terminology_id/value");
        add(FhirConnectConst.DV_CODED_TEXT, "formatting", "formatting", Kind.TEXT);
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
        add(FhirConnectConst.DV_ORDINAL, "value", "ordinal", Kind.NUMBER, "", false, "ordinal");
        add(FhirConnectConst.DV_ORDINAL, "symbol/value", "value", Kind.TEXT);
        add(FhirConnectConst.DV_ORDINAL, "symbol/defining_code/code_string", "code", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "id", "id", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "type", "type", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "issuer", "issuer", Kind.TEXT);
        add(FhirConnectConst.DV_IDENTIFIER, "assigner", "assigner", Kind.TEXT);
        add(FhirConnectConst.DV_PARSABLE, "value", "value", Kind.TEXT, "", true);
        add(FhirConnectConst.DV_PARSABLE, "formalism", "formalism", Kind.TEXT);
        add(FhirConnectConst.DV_MULTIMEDIA, "uri/value", "url", Kind.TEXT, "", true, "uri");
        add(FhirConnectConst.DV_MULTIMEDIA, "media_type/code_string", "mediatype", Kind.TEXT, "", false,
            "media_type");
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

    /**
     * The attribute a value is compared to when a query addresses a whole data value of the type: the number of a
     * quantity, the code of a coded text (a FHIR token search names a code), the text of a text ...
     */
    private static final Map<String, String> COMPARED = Map.ofEntries(
            Map.entry(FhirConnectConst.DV_QUANTITY, "magnitude"),
            Map.entry(FhirConnectConst.DV_COUNT, "magnitude"),
            Map.entry(FhirConnectConst.DV_CODED_TEXT, "defining_code/code_string"),
            Map.entry(FhirConnectConst.DV_ORDINAL, "value"),
            Map.entry(FhirConnectConst.DV_PROPORTION, "numerator"),
            Map.entry(FhirConnectConst.DV_IDENTIFIER, "id"),
            Map.entry(FhirConnectConst.DV_TEXT, "value"),
            Map.entry(FhirConnectConst.DV_BOOL, "value"),
            Map.entry(FhirConnectConst.DV_DATE_TIME, "value"),
            Map.entry(FhirConnectConst.DV_DATE, "value"),
            Map.entry(FhirConnectConst.DV_TIME, "value"),
            Map.entry(FhirConnectConst.DV_DURATION, "value"),
            Map.entry(FhirConnectConst.DV_PARSABLE, "value"),
            Map.entry(FhirConnectConst.DV_MULTIMEDIA, "uri/value"),
            Map.entry("DV_URI", "value"),
            Map.entry("DV_EHR_URI", "value"));

    /** Every spelling of every attribute, to recognise a path's attribute tail before the template is consulted. */
    private static final Set<String> SPELLINGS = BY_TYPE.values().stream()
            .flatMap(List::stream)
            .flatMap(a -> Stream.concat(Stream.of(a.rmPath()), a.aliases().stream()))
            .collect(Collectors.toSet());

    private RmAttributePins() {
    }

    private static void add(final String rmType, final String rmPath, final String flatAttribute, final Kind kind) {
        add(rmType, rmPath, flatAttribute, kind, "", false);
    }

    private static void add(final String rmType, final String rmPath, final String flatAttribute, final Kind kind,
                            final String aqlTail, final boolean emittedOnElement, final String... aliases) {
        BY_TYPE.computeIfAbsent(rmType, t -> new ArrayList<>())
                .add(new Attribute(rmType, rmPath, flatAttribute, kind, aqlTail, emittedOnElement, List.of(aliases)));
    }

    /** The attribute a helper built from a path ending in one addresses, null when it addresses none. */
    public static Attribute pinnedBy(final MappingHelper helper) {
        return helper.getPinnedAttribute();
    }

    /**
     * The ways {@code openEhrPath} can split into a holder and an attribute addressed below it, the longest
     * attribute first ({@code value/defining_code/code_string} before {@code defining_code/code_string}); empty when
     * the path does not end in a spelling of any attribute. Which split, if any, is the path's depends on the
     * holder's type in the template.
     */
    public static List<Pin> parse(final String openEhrPath) {
        if (openEhrPath == null) {
            return List.of();
        }
        final PathPipe pathPipe = pathPipe(openEhrPath);
        if (pathPipe != null) {
            return List.of(new Pin(pathPipe.path(), null, pathPipe.pipe(), false));
        }
        final List<String> segments = splitPath(openEhrPath);
        final List<Pin> pins = new ArrayList<>();
        // the attribute tail: the trailing segments that are RM attributes, not archetype nodes or $ references
        int firstOfTail = segments.size();
        while (firstOfTail > 1 && isAttributeSegment(segments.get(firstOfTail - 1))) {
            firstOfTail--;
        }
        for (int from = firstOfTail; from < segments.size(); from++) {
            final String holder = String.join("/", segments.subList(0, from));
            final String tail = String.join("/", segments.subList(from, segments.size()));
            if (tail.startsWith(VALUE + "/") && SPELLINGS.contains(tail.substring(VALUE.length() + 1))) {
                pins.add(new Pin(holder, tail.substring(VALUE.length() + 1), null, true));
            } else if (SPELLINGS.contains(tail)) {
                pins.add(new Pin(holder, tail, null, false));
            }
        }
        return pins;
    }

    /** The flat pipe attribute {@code openEhrPath} ends in, as its own segment or on the node; null when none. */
    public static PathPipe pathPipe(final String openEhrPath) {
        if (openEhrPath == null) {
            return null;
        }
        final int pipe = openEhrPath.lastIndexOf('|');
        // a pipe in a node predicate ([... and name/value='a|b']) is no attribute
        if (pipe <= 0 || pipe == openEhrPath.length() - 1 || pipe < openEhrPath.lastIndexOf(']')
                || openEhrPath.indexOf('/', pipe) >= 0) {
            return null;
        }
        final String path = openEhrPath.substring(0, pipe);
        return new PathPipe(path.endsWith("/") ? path.substring(0, path.length() - 1) : path,
                            openEhrPath.substring(pipe + 1));
    }

    private static boolean isAttributeSegment(final String segment) {
        return !segment.isEmpty() && !segment.contains("[") && !segment.startsWith("$") && !segment.startsWith("|");
    }

    /**
     * The attribute {@code pin} addresses on a holder of the given types, null when none of them has it: a bare
     * {@code value} on an ELEMENT is its whole data value, and an attribute through an ELEMENT's value needs an
     * ELEMENT. A DV_TEXT may also hold a DV_CODED_TEXT, its subtype, so a DV_TEXT has the coded text's attributes
     * too ({@code defining_code/code_string} of a coded value written to a text element).
     *
     * @param holderIsElement the holder is an ELEMENT, {@code types} the types its value may have; otherwise the
     *                        holder is the data value, of the single type in {@code types}
     */
    static Attribute resolve(final Pin pin, final boolean holderIsElement, final List<String> types) {
        if (types == null || (pin.throughValue() && !holderIsElement)
                || (holderIsElement && !pin.throughValue() && VALUE.equals(pin.rmPath()))) {
            return null;
        }
        return types.stream()
                .filter(Objects::nonNull)
                .flatMap(type -> FhirConnectConst.DV_TEXT.equals(type)
                        ? Stream.of(type, FhirConnectConst.DV_CODED_TEXT)
                        : Stream.of(type))
                .flatMap(type -> BY_TYPE.getOrDefault(type, List.of()).stream())
                .filter(a -> pin.pipe() != null ? a.flatAttribute().equals(pin.pipe()) : a.isSpelled(pin.rmPath()))
                .findFirst().orElse(null);
    }

    /**
     * The attribute spelled {@code spelling} on whichever data value type has it first, for a condition whose
     * holder's type is not known from the template.
     */
    static Attribute anyTypes(final String spelling) {
        return BY_TYPE.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.isSpelled(spelling))
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
        if (FhirConnectConst.DV_CODED_TEXT.equals(rmType) && !elementTypes.contains(rmType)) {
            // a coded text in the place of the element's DV_TEXT
            return typeSegment(elementTypes, FhirConnectConst.DV_TEXT);
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

    /**
     * The strict AQL path of {@code attribute} below {@code holderPath}, ready to compare in a WHERE: through the
     * ELEMENT's value, spelled the RM way whatever alias the mapping used
     * ({@code items[at0130]/value/defining_code/code_string}).
     */
    public static String aqlPath(final String holderPath, final boolean holderIsElement, final Attribute attribute) {
        return holderPath + (holderIsElement ? "/value/" : "/") + attribute.rmPath() + attribute.aqlTail();
    }

    /**
     * The attribute a query compares when it addresses a whole data value of {@code rmType}; null for a type that has
     * none (DV_INTERVAL ...) or that is no data value (EVENT, CLUSTER ...).
     */
    public static Attribute compared(final String rmType) {
        final String rmPath = rmType == null ? null : COMPARED.get(rmType);
        return rmPath == null ? null : attribute(rmType, rmPath);
    }

    /** The attribute {@code rmPath} (spelled the RM way) of {@code rmType}, null when the type has no such one. */
    public static Attribute attribute(final String rmType, final String rmPath) {
        return BY_TYPE.getOrDefault(rmType, List.of()).stream()
                .filter(a -> a.rmPath().equals(rmPath))
                .findFirst().orElse(null);
    }

    /** Every attribute per type, for tooling that offers them (Atlas path completion mirrors this table). */
    public static Map<String, List<Attribute>> all() {
        return Collections.unmodifiableMap(BY_TYPE);
    }

    /** Splits on the slashes outside of node predicates ({@code [... and name/value='a/b']}). */
    private static List<String> splitPath(final String path) {
        final List<String> segments = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        for (final char c : path.toCharArray()) {
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            }
            if (c == '/' && depth == 0) {
                segments.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        segments.add(current.toString());
        return segments;
    }
}
