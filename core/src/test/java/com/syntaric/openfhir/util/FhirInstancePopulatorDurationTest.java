package com.syntaric.openfhir.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import java.math.BigDecimal;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

/**
 * openEHR carries a DV_DURATION as an ISO 8601 string, while FHIR's Duration is a Quantity with a
 * single UCUM time code. Without this conversion an element such as {@code PT0S} either lands in the
 * Duration as raw text or is dropped entirely.
 */
class FhirInstancePopulatorDurationTest {

    private final FhirInstancePopulator populator = new FhirInstancePopulator(
            new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator());

    /**
     * The case that motivated this mapping: zero is a value, not an absence, so it must populate
     * rather than be skipped by a truthiness check.
     */
    @Test
    void populateElement_zeroDuration() {
        final Duration populated = populate("PT0S");

        assertSoftly(softly -> {
            softly.assertThat(populated.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
            softly.assertThat(populated.getCode()).isEqualTo("s");
            softly.assertThat(populated.getUnit()).isEqualTo("s");
            softly.assertThat(populated.getSystem()).isEqualTo("http://unitsofmeasure.org");
            softly.assertThat(populated.hasValue()).isTrue();
        });
    }

    /**
     * A single-component duration keeps its authored unit instead of being normalised, so the
     * granularity the template expressed survives the trip to FHIR.
     */
    @Test
    void populateElement_singleComponentKeepsItsOwnUnit() {
        assertSoftly(softly -> {
            softly.assertThat(populate("PT30M").getCode()).isEqualTo("min");
            softly.assertThat(populate("PT30M").getValue()).isEqualByComparingTo(BigDecimal.valueOf(30));
            softly.assertThat(populate("PT8H").getCode()).isEqualTo("h");
            softly.assertThat(populate("PT8H").getValue()).isEqualByComparingTo(BigDecimal.valueOf(8));
            softly.assertThat(populate("PT45S").getCode()).isEqualTo("s");
            softly.assertThat(populate("P10D").getCode()).isEqualTo("d");
            softly.assertThat(populate("P10D").getValue()).isEqualByComparingTo(BigDecimal.valueOf(10));
        });
    }

    /**
     * Years, months and weeks have no fixed length in seconds, so they keep their own UCUM code
     * rather than being converted through an invented day count.
     */
    @Test
    void populateElement_calendarUnitsAreNotConvertedToSeconds() {
        assertSoftly(softly -> {
            softly.assertThat(populate("P1Y").getCode()).isEqualTo("a");
            softly.assertThat(populate("P1Y").getValue()).isEqualByComparingTo(BigDecimal.ONE);
            softly.assertThat(populate("P6M").getCode()).isEqualTo("mo");
            softly.assertThat(populate("P6M").getValue()).isEqualByComparingTo(BigDecimal.valueOf(6));
            softly.assertThat(populate("P3W").getCode()).isEqualTo("wk");
            softly.assertThat(populate("P3W").getValue()).isEqualByComparingTo(BigDecimal.valueOf(3));
        });
    }

    /**
     * Hours, minutes and seconds are fixed-length, so a compound time-only duration can be
     * normalised to seconds without losing anything.
     */
    @Test
    void populateElement_compoundTimeNormalisesToSeconds() {
        assertSoftly(softly -> {
            softly.assertThat(populate("PT1H30M").getValue()).isEqualByComparingTo(BigDecimal.valueOf(5400));
            softly.assertThat(populate("PT1H30M").getCode()).isEqualTo("s");
            softly.assertThat(populate("PT2M30S").getValue()).isEqualByComparingTo(BigDecimal.valueOf(150));
        });
    }

    /**
     * Sub-second precision would be lost by an integer-seconds conversion.
     */
    @Test
    void populateElement_fractionalSecondsArePreserved() {
        assertThat(populate("PT0.5S").getValue()).isEqualByComparingTo(BigDecimal.valueOf(0.5));
    }

    /**
     * A duration mixing calendar and clock components has no single honest unit, so it is left
     * unset rather than silently converted on a 30-day-month assumption.
     */
    @Test
    void populateElement_unconvertibleCompoundIsLeftUnset() {
        assertSoftly(softly -> {
            softly.assertThat(populate("P1Y6M").hasValue()).isFalse();
            softly.assertThat(populate("P1M15D").hasValue()).isFalse();
        });
    }

    @Test
    void populateElement_invalidOrBlankInputIsLeftUnset() {
        assertSoftly(softly -> {
            softly.assertThat(populate("not-a-duration").hasValue()).isFalse();
            softly.assertThat(populate("").hasValue()).isFalse();
            softly.assertThat(populate(null).hasValue()).isFalse();
        });
    }

    /**
     * The engine maps into any FHIR version, so a Duration target must be populated identically
     * whichever version's class it is: the conversion is shared and only the setters differ.
     */
    @Test
    void populateElement_appliesToEveryFhirVersion() {
        final org.hl7.fhir.dstu3.model.Duration stu3 = new org.hl7.fhir.dstu3.model.Duration();
        final org.hl7.fhir.r4b.model.Duration r4b = new org.hl7.fhir.r4b.model.Duration();
        final org.hl7.fhir.r5.model.Duration r5 = new org.hl7.fhir.r5.model.Duration();

        populator.handleSpecificTypePopulation(stu3, new StringType("PT30M"), null);
        populator.handleSpecificTypePopulation(r4b, new StringType("PT30M"), null);
        populator.handleSpecificTypePopulation(r5, new StringType("PT30M"), null);

        assertSoftly(softly -> {
            softly.assertThat(stu3.getValue()).isEqualByComparingTo(BigDecimal.valueOf(30));
            softly.assertThat(stu3.getCode()).isEqualTo("min");
            softly.assertThat(stu3.getSystem()).isEqualTo("http://unitsofmeasure.org");
            softly.assertThat(r4b.getValue()).isEqualByComparingTo(BigDecimal.valueOf(30));
            softly.assertThat(r4b.getCode()).isEqualTo("min");
            softly.assertThat(r4b.getSystem()).isEqualTo("http://unitsofmeasure.org");
            softly.assertThat(r5.getValue()).isEqualByComparingTo(BigDecimal.valueOf(30));
            softly.assertThat(r5.getCode()).isEqualTo("min");
            softly.assertThat(r5.getSystem()).isEqualTo("http://unitsofmeasure.org");
        });
    }

    /** Zero must not be dropped on the other versions either. */
    @Test
    void populateElement_zeroDurationOnEveryFhirVersion() {
        final org.hl7.fhir.dstu3.model.Duration stu3 = new org.hl7.fhir.dstu3.model.Duration();
        final org.hl7.fhir.r4b.model.Duration r4b = new org.hl7.fhir.r4b.model.Duration();
        final org.hl7.fhir.r5.model.Duration r5 = new org.hl7.fhir.r5.model.Duration();

        populator.handleSpecificTypePopulation(stu3, new StringType("PT0S"), null);
        populator.handleSpecificTypePopulation(r4b, new StringType("PT0S"), null);
        populator.handleSpecificTypePopulation(r5, new StringType("PT0S"), null);

        assertSoftly(softly -> {
            softly.assertThat(stu3.hasValue()).isTrue();
            softly.assertThat(stu3.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
            softly.assertThat(stu3.getCode()).isEqualTo("s");
            softly.assertThat(r4b.hasValue()).isTrue();
            softly.assertThat(r4b.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
            softly.assertThat(r4b.getCode()).isEqualTo("s");
            softly.assertThat(r5.hasValue()).isTrue();
            softly.assertThat(r5.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
            softly.assertThat(r5.getCode()).isEqualTo("s");
        });
    }

    /**
     * An unconvertible value must leave the element unset on the other versions too, rather than
     * falling through to a string setter that would write the raw ISO text into a Quantity.
     */
    @Test
    void populateElement_unconvertibleIsLeftUnsetOnEveryFhirVersion() {
        final org.hl7.fhir.dstu3.model.Duration stu3 = new org.hl7.fhir.dstu3.model.Duration();
        final org.hl7.fhir.r4b.model.Duration r4b = new org.hl7.fhir.r4b.model.Duration();
        final org.hl7.fhir.r5.model.Duration r5 = new org.hl7.fhir.r5.model.Duration();

        populator.handleSpecificTypePopulation(stu3, new StringType("P1Y6M"), null);
        populator.handleSpecificTypePopulation(r4b, new StringType("P1Y6M"), null);
        populator.handleSpecificTypePopulation(r5, new StringType("P1Y6M"), null);

        assertSoftly(softly -> {
            softly.assertThat(stu3.hasValue()).isFalse();
            softly.assertThat(r4b.hasValue()).isFalse();
            softly.assertThat(r5.hasValue()).isFalse();
        });
    }

    private Duration populate(final String isoDuration) {
        final Duration populated = new Duration();
        populator.handleSpecificTypePopulation(populated, new StringType(isoDuration), null);
        return populated;
    }
}
