package com.syntaric.openfhir.util;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.junit.jupiter.api.Test;

/**
 * A DV_DATE is parsed into a FHIR {@code date}, but FHIRConnect mappings routinely point it at a {@code dateTime}
 * element ({@code onsetDateTime}, {@code effectiveDateTime}, ...). A date is a valid dateTime at day precision, so it
 * must land there as written — without a time of day or an offset the source never had.
 */
class FhirInstancePopulatorDateTest {

    private final FhirInstancePopulator populator = new FhirInstancePopulator(
            new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator());

    @Test
    void populateElement_dateIntoDate() {
        final DateType populated = new DateType();
        populator.handleSpecificTypePopulation(populated, new DateType("2020-05-14"), null);

        assertSoftly(softly -> softly.assertThat(populated.getValueAsString()).isEqualTo("2020-05-14"));
    }

    @Test
    void populateElement_dateIntoDateTimeKeepsDayPrecision() {
        final DateTimeType populated = new DateTimeType();
        populator.handleSpecificTypePopulation(populated, new DateType("2020-05-14"), null);

        assertSoftly(softly -> {
            softly.assertThat(populated.getValueAsString()).isEqualTo("2020-05-14");
            softly.assertThat(populated.getPrecision()).isEqualTo(TemporalPrecisionEnum.DAY);
            softly.assertThat(populated.getTimeZone()).isNull();
        });
    }

    @Test
    void populateElement_dateIntoDateTimeOnEveryFhirVersion() {
        final org.hl7.fhir.dstu3.model.DateTimeType stu3 = new org.hl7.fhir.dstu3.model.DateTimeType();
        final org.hl7.fhir.r4b.model.DateTimeType r4b = new org.hl7.fhir.r4b.model.DateTimeType();
        final org.hl7.fhir.r5.model.DateTimeType r5 = new org.hl7.fhir.r5.model.DateTimeType();

        populator.handleSpecificTypePopulation(stu3, new DateType("2020-05-14"), null);
        populator.handleSpecificTypePopulation(r4b, new DateType("2020-05-14"), null);
        populator.handleSpecificTypePopulation(r5, new DateType("2020-05-14"), null);

        assertSoftly(softly -> {
            softly.assertThat(stu3.getValueAsString()).isEqualTo("2020-05-14");
            softly.assertThat(r4b.getValueAsString()).isEqualTo("2020-05-14");
            softly.assertThat(r5.getValueAsString()).isEqualTo("2020-05-14");
        });
    }

    @Test
    void populateElement_dateIntoDateOnEveryFhirVersion() {
        final org.hl7.fhir.dstu3.model.DateType stu3 = new org.hl7.fhir.dstu3.model.DateType();
        final org.hl7.fhir.r4b.model.DateType r4b = new org.hl7.fhir.r4b.model.DateType();
        final org.hl7.fhir.r5.model.DateType r5 = new org.hl7.fhir.r5.model.DateType();

        populator.handleSpecificTypePopulation(stu3, new DateType("2020-05-14"), null);
        populator.handleSpecificTypePopulation(r4b, new DateType("2020-05-14"), null);
        populator.handleSpecificTypePopulation(r5, new DateType("2020-05-14"), null);

        assertSoftly(softly -> {
            softly.assertThat(stu3.getValueAsString()).isEqualTo("2020-05-14");
            softly.assertThat(r4b.getValueAsString()).isEqualTo("2020-05-14");
            softly.assertThat(r5.getValueAsString()).isEqualTo("2020-05-14");
        });
    }
}
