package com.syntaric.openfhir.mapping;

import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import org.apache.commons.lang3.StringUtils;

/**
 * Where the engine was when something went wrong: the model mapper, archetype and mapping being executed plus the
 * two paths it was working between. Captured at the per-mapping loop of each engine so a failure can be reported
 * in the mapper author's terms instead of as an anonymous exception.
 *
 * <p>The template id is deliberately not part of this: it isn't known inside the mapping loops, and the response
 * a failure ends up in is already per template.
 *
 * @param direction    {@link FhirConnectConst#UNIDIRECTIONAL_TOFHIR} or
 *                     {@link FhirConnectConst#UNIDIRECTIONAL_TOOPENEHR}
 * @param modelMapper  the model mapper's metadata name
 * @param archetype    the archetype the model mapper maps
 * @param mappingName  the mapping's name inside the model mapper
 * @param openEhrPath  the mapping's openEHR path as written in the mapper, or the resolved flat path
 * @param fhirPath     the mapping's FHIR path as written in the mapper
 */
public record MappingContext(String direction, String modelMapper, String archetype,
                             String mappingName, String openEhrPath, String fhirPath) {

    public static MappingContext of(final MappingHelper helper, final String direction) {
        if (helper == null) {
            return new MappingContext(direction, null, null, null, null, null);
        }
        final String openEhrPath = StringUtils.isNotBlank(helper.getOriginalOpenEhrPath())
                ? helper.getOriginalOpenEhrPath()
                : helper.getFullOpenEhrFlatPath();
        final String fhirPath = StringUtils.isNotBlank(helper.getOriginalFhirPath())
                ? helper.getOriginalFhirPath()
                : helper.getFhir();
        return new MappingContext(direction, helper.getModelMetadataName(), helper.getArchetype(),
                helper.getMappingName(), openEhrPath, fhirPath);
    }

    /**
     * True when this context describes the openEHR → FHIR direction.
     */
    public boolean isToFhir() {
        return FhirConnectConst.UNIDIRECTIONAL_TOFHIR.equals(direction);
    }

    /**
     * Human-readable direction, e.g. {@code to FHIR}.
     */
    public String directionLabel() {
        return isToFhir() ? "to FHIR" : "to openEHR";
    }

    /**
     * Single wording shared by errors, warnings and log lines, e.g.
     * {@code mapping 'weight' of model mapper 'Body weight' (archetype 'openEHR-EHR-OBSERVATION.body_weight.v2',
     * openEHR 'weight', FHIR 'Observation.value')}. Every field may be null; unknown parts are labelled as such
     * rather than dropped so the output stays predictable.
     */
    public String describe() {
        return String.format("mapping '%s' of model mapper '%s' (archetype '%s', openEHR '%s', FHIR '%s')",
                orUnknown(mappingName), orUnknown(modelMapper), orUnknown(archetype),
                orUnknown(openEhrPath), orUnknown(fhirPath));
    }

    private static String orUnknown(final String value) {
        return StringUtils.isBlank(value) ? "?" : value;
    }
}
