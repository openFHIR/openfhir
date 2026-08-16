package com.syntaric.openfhir.util;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Range;
import org.junit.jupiter.api.Test;

/**
 * A Range must be copied onto the target element, otherwise a dosage stored in openEHR as a
 * DV_INTERVAL (e.g. {@code Dosage.doseAndRate.doseRange}) is silently dropped on the way back to FHIR.
 */
class FhirInstancePopulatorRangeTest {

    @Test
    void populateElement_RangeToRange() {
        var fhirInstancePopulator = new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(),
                new NoOpTerminologyTranslator());

        final Range input = createTestRange();
        final Range populated = new Range();

        fhirInstancePopulator.handleSpecificTypePopulation(populated, input, null);

        assertSoftly(softly -> {
            softly.assertThat(populated.getLow().getValue()).isEqualByComparingTo(input.getLow().getValue());
            softly.assertThat(populated.getLow().getUnit()).isEqualTo(input.getLow().getUnit());
            softly.assertThat(populated.getLow().getSystem()).isEqualTo(input.getLow().getSystem());
            softly.assertThat(populated.getHigh().getValue()).isEqualByComparingTo(input.getHigh().getValue());
            softly.assertThat(populated.getHigh().getUnit()).isEqualTo(input.getHigh().getUnit());
            softly.assertThat(populated.getHigh().getSystem()).isEqualTo(input.getHigh().getSystem());
        });
    }

    @Test
    void populateElement_RangeWithOnlyLowBound() {
        var fhirInstancePopulator = new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(),
                new NoOpTerminologyTranslator());

        final Range input = new Range().setLow(quantity(500d, "mg"));
        final Range populated = new Range();

        fhirInstancePopulator.handleSpecificTypePopulation(populated, input, null);

        assertSoftly(softly -> {
            softly.assertThat(populated.hasLow()).isTrue();
            softly.assertThat(populated.getLow().getValue()).isEqualByComparingTo(input.getLow().getValue());
            softly.assertThat(populated.hasHigh()).isFalse();
        });
    }

    private Range createTestRange() {
        return new Range()
                .setLow(quantity(500d, "mg"))
                .setHigh(quantity(1000d, "mg"));
    }

    private Quantity quantity(final Double value, final String unit) {
        final Quantity quantity = new Quantity();
        quantity.setValue(value);
        quantity.setUnit(unit);
        quantity.setCode(unit);
        quantity.setSystem("http://unitsofmeasure.org");
        return quantity;
    }
}
