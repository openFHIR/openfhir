package com.syntaric.openfhir.mapping.tofhir;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

/**
 * Reconstructs the FHIR {@code data-absent-reason} extension from an openEHR {@code null_flavour}
 * when mapping openEHR -> FHIR.
 *
 * <p>This is the counterpart of
 * {@link com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrNullFlavour}, which performs the same
 * translation in the opposite direction. An openEHR ELEMENT that carries a {@code null_flavour}
 * has no value, so nothing is produced by the ordinary value-extraction path; without this the
 * information would silently be dropped on the way back out to FHIR.
 *
 * <p>Because the resulting extension is attached to the FHIR primitive itself, it is serialized as
 * the primitive's sibling element ({@code _city}, {@code _line}, ...), as required by the FHIR spec.
 *
 * <p>The translation is generic, cross-resource plumbing: it needs no per-field mapping expression.
 */
@Slf4j
@Component
public class ToFhirNullFlavour {

    static final String NULL_FLAVOUR_NODE = "_null_flavour";

    /**
     * openEHR null flavour codes (openehr terminology group 'null flavours') mapped to the FHIR
     * <a href="http://hl7.org/fhir/ValueSet/data-absent-reason">data-absent-reason</a> codes.
     */
    private static final Map<String, String> NULL_FLAVOUR_CODE_TO_DATA_ABSENT_REASON = Map.of(
            "253", "unknown",
            "271", "unknown",
            "272", "masked",
            "273", "not-applicable");

    /**
     * Fallback for compositions that only carry the null flavour's textual value.
     */
    private static final Map<String, String> NULL_FLAVOUR_VALUE_TO_DATA_ABSENT_REASON = Map.of(
            "unknown", "unknown",
            "no information", "unknown",
            "masked", "masked",
            "not applicable", "not-applicable");

    private final OpenFhirStringUtils openFhirStringUtils;
    private final FhirInstanceCreatorUtility fhirInstanceCreatorUtility;

    @Autowired
    public ToFhirNullFlavour(final OpenFhirStringUtils openFhirStringUtils,
                             final FhirInstanceCreatorUtility fhirInstanceCreatorUtility) {
        this.openFhirStringUtils = openFhirStringUtils;
        this.fhirInstanceCreatorUtility = fhirInstanceCreatorUtility;
    }

    /**
     * Adds a {@code data-absent-reason} extension to {@code instantiated} when the openEHR ELEMENT
     * that {@code mappingHelper} points at carries a {@code null_flavour} and produced no value.
     *
     * @param mappingHelper    mapping whose openEHR flat path identifies the ELEMENT
     * @param instantiated     the FHIR element instantiated for this mapping; may be a List, in
     *                         which case the last entry is used
     * @param extractedData    data extracted for this mapping; used to resolve the concrete
     *                         occurrence (e.g. {@code straßenanschrift:0}) and to detect that no
     *                         real value was extracted
     * @param flatJsonObject   the composition in flat-path format
     * @param modelPackage     FHIR model package of the version being generated
     * @return {@code true} when an extension was added
     */
    public boolean handleNullFlavour(final MappingHelper mappingHelper,
                                     final Object instantiated,
                                     final List<DataWithIndex> extractedData,
                                     final JsonObject flatJsonObject,
                                     final String modelPackage) {
        if (mappingHelper == null || instantiated == null || flatJsonObject == null) {
            return false;
        }
        final IBaseHasExtensions target = resolveExtensionTarget(instantiated);
        if (target == null || hasValue(target) || hasDataAbsentReason(target)) {
            return false;
        }
        final String dataAbsentReason = resolveDataAbsentReason(mappingHelper, extractedData, flatJsonObject);
        if (dataAbsentReason == null) {
            return false;
        }
        return addDataAbsentReasonExtension(target, dataAbsentReason, modelPackage);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // null_flavour lookup in the flat composition
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Finds the {@code _null_flavour} entry belonging to this mapping's ELEMENT and translates it
     * to a FHIR data-absent-reason code. Returns {@code null} when the ELEMENT has no null flavour
     * or the flavour is not translatable.
     */
    private String resolveDataAbsentReason(final MappingHelper mappingHelper,
                                           final List<DataWithIndex> extractedData,
                                           final JsonObject flatJsonObject) {
        final String elementPath = resolveElementPath(mappingHelper, extractedData);
        if (StringUtils.isBlank(elementPath)) {
            return null;
        }
        final String nullFlavourPrefix = elementPath + "/" + NULL_FLAVOUR_NODE;
        final String code = readFlatValue(flatJsonObject, nullFlavourPrefix + "|code");
        final String mappedByCode = code == null ? null : NULL_FLAVOUR_CODE_TO_DATA_ABSENT_REASON.get(code.trim());
        if (mappedByCode != null) {
            return mappedByCode;
        }
        final String value = readFlatValue(flatJsonObject, nullFlavourPrefix + "|value");
        if (value == null) {
            return null;
        }
        final String mappedByValue = NULL_FLAVOUR_VALUE_TO_DATA_ABSENT_REASON.get(value.trim().toLowerCase(Locale.ROOT));
        if (mappedByValue == null) {
            log.debug("openEHR null_flavour '{}' (code '{}') at '{}' has no FHIR data-absent-reason equivalent.",
                      value, code, nullFlavourPrefix);
        }
        return mappedByValue;
    }

    /**
     * Resolves the concrete, index-bearing flat path of the ELEMENT this mapping points at.
     *
     * <p>{@link MappingHelper#getFullOpenEhrFlatPath()} still carries the {@code [n]} occurrence
     * markers, so the path of an already extracted data point is preferred — it is resolved against
     * the composition and therefore has the real indices. Value leaf nodes appended during
     * extraction ({@code coded_text_value}, {@code quantity_value}, ...) are stripped, since a
     * {@code null_flavour} sits on the ELEMENT and not on its value.
     */
    private String resolveElementPath(final MappingHelper mappingHelper, final List<DataWithIndex> extractedData) {
        if (extractedData != null) {
            for (final DataWithIndex data : extractedData) {
                final String path = stripValueLeaf(data == null ? null : data.getFullOpenEhrPath());
                if (StringUtils.isNotBlank(path)) {
                    return path;
                }
            }
        }
        final String flatPath = mappingHelper.getFullOpenEhrFlatPath();
        if (StringUtils.isBlank(flatPath) || flatPath.contains(RECURRING_SYNTAX)) {
            // Without an extracted data point we cannot resolve which occurrence is meant.
            return null;
        }
        return stripValueLeaf(flatPath);
    }

    private String stripValueLeaf(final String flatPath) {
        if (StringUtils.isBlank(flatPath)) {
            return flatPath;
        }
        final String withoutPipe = flatPath.split("\\|")[0];
        final int lastSlash = withoutPipe.lastIndexOf('/');
        if (lastSlash < 0) {
            return withoutPipe;
        }
        final String leaf = withoutPipe.substring(lastSlash + 1);
        return "value".equals(leaf) || leaf.endsWith("_value") ? withoutPipe.substring(0, lastSlash) : withoutPipe;
    }

    private String readFlatValue(final JsonObject flatJsonObject, final String key) {
        final JsonElement element = flatJsonObject.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        final String value = element.getAsString();
        return StringUtils.isBlank(value) ? null : value;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // FHIR element handling
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Returns the element the extension has to be set on, or {@code null} when the instantiated
     * object is not a suitable target. For lists, the last entry is the one instantiated for the
     * current occurrence.
     *
     * <p>Only FHIR primitives are targeted. A primitive without a value serializes purely as its
     * sibling {@code _<field>} element, which is exactly the representation the FHIR spec
     * prescribes for a value that is absent for a known reason. Complex datatypes are deliberately
     * left alone: elements such as {@code Observation.value[x]} have a dedicated sibling
     * {@code dataAbsentReason} element that should be used instead of an extension, and populating
     * it requires knowing the owning element — which is not available at this point in the
     * mapping engine.
     */
    private IBaseHasExtensions resolveExtensionTarget(final Object instantiated) {
        Object candidate = instantiated;
        if (candidate instanceof List<?> list) {
            if (list.isEmpty()) {
                return null;
            }
            candidate = list.get(list.size() - 1);
        }
        if (!(candidate instanceof IPrimitiveType<?>)) {
            return null;
        }
        return candidate instanceof IBaseHasExtensions hasExtensions ? hasExtensions : null;
    }

    /**
     * A null flavour only describes an absent value; anything already carrying data must be left as is.
     */
    private boolean hasValue(final Object target) {
        return target instanceof IPrimitiveType<?> primitive && primitive.getValue() != null;
    }

    private boolean hasDataAbsentReason(final IBaseHasExtensions target) {
        return target.getExtension().stream()
                .anyMatch(extension -> OpenEhrPopulator.DATA_ABSENT_REASON_URL.equals(extension.getUrl()));
    }

    @SuppressWarnings("unchecked")
    private boolean addDataAbsentReasonExtension(final IBaseHasExtensions target,
                                                 final String dataAbsentReason,
                                                 final String modelPackage) {
        final IBaseExtension<?, ?> extension = newInstanceOf(modelPackage + "Extension", IBaseExtension.class);
        final IPrimitiveType<String> valueCode = newInstanceOf(modelPackage + "CodeType", IPrimitiveType.class);
        if (extension == null || valueCode == null) {
            return false;
        }
        valueCode.setValue(dataAbsentReason);
        extension.setUrl(OpenEhrPopulator.DATA_ABSENT_REASON_URL);
        ((IBaseExtension<?, IBaseDatatype>) extension).setValue((IBaseDatatype) valueCode);
        ((List<IBaseExtension<?, ?>>) target.getExtension()).add(extension);
        return true;
    }

    private <T> T newInstanceOf(final String className, final Class<T> expectedType) {
        final Class<?> clazz = fhirInstanceCreatorUtility.getClassForName(className);
        if (clazz == null) {
            return null;
        }
        final Object instance = fhirInstanceCreatorUtility.newInstance(clazz);
        if (!expectedType.isInstance(instance)) {
            log.warn("Expected {} to be a {} but got {}.", className, expectedType.getSimpleName(), instance);
            return null;
        }
        return expectedType.cast(instance);
    }
}
