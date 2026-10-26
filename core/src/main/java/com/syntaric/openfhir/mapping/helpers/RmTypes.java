package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.fc.FhirConnectConst;
import java.util.List;
import java.util.Set;

/**
 * The RM type a mapping means among those the template allows its node. A mapping's {@code with.type} picks among
 * them ({@code QUANTITY} on an element that may hold a quantity or a text); it never adds a type the template does
 * not allow, and does not change the flat path.
 */
public final class RmTypes {

    private RmTypes() {
    }

    /**
     * The RM types a {@code with.type} value stands for: a FHIRConnect type ({@code QUANTITY}, {@code CODING} ...) or
     * an RM type itself ({@code DV_QUANTITY}); null when there is none.
     */
    static Set<String> namedBy(final String withType) {
        if (withType == null) {
            return null;
        }
        return switch (withType) {
            case "QUANTITY" -> Set.of(FhirConnectConst.DV_QUANTITY, FhirConnectConst.DV_COUNT,
                                      FhirConnectConst.DV_ORDINAL, FhirConnectConst.DV_PROPORTION);
            case "DATETIME" -> Set.of(FhirConnectConst.DV_DATE_TIME);
            case "TIME" -> Set.of(FhirConnectConst.DV_TIME);
            case "DATE" -> Set.of(FhirConnectConst.DV_DATE);
            case "CODEABLECONCEPT" -> Set.of(FhirConnectConst.DV_CODED_TEXT);
            case "CODING" -> Set.of(FhirConnectConst.CODE_PHRASE, FhirConnectConst.DV_CODED_TEXT);
            case "STRING" -> Set.of(FhirConnectConst.DV_TEXT);
            case "BOOL" -> Set.of(FhirConnectConst.DV_BOOL);
            case "IDENTIFIER" -> Set.of(FhirConnectConst.DV_IDENTIFIER);
            case "MEDIA" -> Set.of(FhirConnectConst.DV_MULTIMEDIA);
            case "PROPORTION" -> Set.of(FhirConnectConst.DV_PROPORTION);
            default -> Set.of(withType);
        };
    }

    /**
     * {@code types} narrowed to those {@code withType} names, in their order; all of them when it names none of them
     * (no {@code with.type}, {@code NONE}, or a type the template does not allow there).
     */
    static List<String> preferred(final List<String> types, final String withType) {
        final Set<String> named = namedBy(withType);
        if (types == null || named == null) {
            return types;
        }
        final List<String> narrowed = types.stream().filter(named::contains).toList();
        return narrowed.isEmpty() ? types : narrowed;
    }

    /** The helper's possible RM types narrowed by its {@code with.type}, see {@link #preferred(List, String)}. */
    public static List<String> preferred(final MappingHelper helper) {
        return preferred(helper.getPossibleRmTypes(), helper.getHardcodedType());
    }
}
