package com.syntaric.openfhir.mapping.toopenehr;

/**
 * Why a FHIR → openEHR mapping (or a whole walk over a list of mappings) ended the way it did. Distinguishes the
 * misses that are worth a warning from the ones that are the mapper working as designed, so that a Bundle fanned
 * out over slot mappings does not produce a warning for every slot that was never meant to match.
 */
public enum MappingOutcome {

    /**
     * Nothing to report: a gate (preprocessor condition, type/unidirectional check, empty/not-empty condition,
     * path-filtering condition, reference resource type) decided the mapping does not apply to this element, the
     * mapping has nothing to write in this direction, or the problem was already reported as its own issue.
     */
    NOT_APPLICABLE,

    /**
     * The mapping's FHIRPath evaluated fine but found nothing on the element, and no null-flavour applied.
     */
    NO_DATA,

    /**
     * The FHIRPath found data but no openEHR value came out of it, e.g. an unsupported type combination.
     */
    NOTHING_WRITTEN,

    /**
     * An openEHR value was written.
     */
    MAPPED;

    /**
     * The more significant of the two, in the order declared.
     */
    public MappingOutcome best(final MappingOutcome other) {
        return other == null || ordinal() >= other.ordinal() ? this : other;
    }

    /**
     * True for the outcomes a mapper author should hear about.
     */
    public boolean needsAttention() {
        return this == NO_DATA || this == NOTHING_WRITTEN;
    }
}
