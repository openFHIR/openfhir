package com.syntaric.openfhir.util;

import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import java.util.Calendar;
import java.util.Date;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Identifier.IdentifierUse;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class FhirInstancePopulatorIdentifierTest {

    @Test
    void populateElement_IdentifierToIdentifier() {
        var fhirInstancePopulator = new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator());

        Identifier inputIdentifier = createTestIdentifier();
        var populatedIdentifier = new Identifier();

        fhirInstancePopulator.handleSpecificTypePopulation(populatedIdentifier, inputIdentifier, null);

        assertSoftly(softly -> {
            softly.assertThat(populatedIdentifier.getUse()).isEqualTo(inputIdentifier.getUse());
            softly.assertThat(populatedIdentifier.getType().getCodingFirstRep().getSystem()).isEqualTo(inputIdentifier.getType().getCodingFirstRep().getSystem());
            softly.assertThat(populatedIdentifier.getType().getCodingFirstRep().getCode()).isEqualTo(inputIdentifier.getType().getCodingFirstRep().getCode());
            softly.assertThat(populatedIdentifier.getType().getCodingFirstRep().getDisplay()).isEqualTo(inputIdentifier.getType().getCodingFirstRep().getDisplay());
            softly.assertThat(populatedIdentifier.getType().getText()).isEqualTo(inputIdentifier.getType().getText());
            softly.assertThat(populatedIdentifier.getSystem()).isEqualTo(inputIdentifier.getSystem());
            softly.assertThat(populatedIdentifier.getValue()).isEqualTo(inputIdentifier.getValue());
            softly.assertThat(populatedIdentifier.getPeriod().getStart()).isEqualTo(inputIdentifier.getPeriod().getStart());
            softly.assertThat(populatedIdentifier.getPeriod().getEnd()).isEqualTo(inputIdentifier.getPeriod().getEnd());
            softly.assertThat(populatedIdentifier.getAssigner().getReference()).isEqualTo(inputIdentifier.getAssigner().getReference());
        });
    }

    @Test
    void populateElement_IdentifierToStringType() {
        var fhirInstancePopulator = new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator());

        Identifier inputIdentifier = createTestIdentifier();
        var populatedString = new StringType();

        fhirInstancePopulator.handleSpecificTypePopulation(populatedString, inputIdentifier, null);

        assertEquals(inputIdentifier.getValue(), populatedString.getValue());
    }

    private Identifier createTestIdentifier() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MILLISECOND, 0);

        Date start = calendar.getTime();

        calendar.add(Calendar.YEAR, 1);
        Date end = calendar.getTime();

        return new Identifier()
                .setUse(IdentifierUse.OFFICIAL)
                .setType(
                        new CodeableConcept(
                                new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "SNO", "Serial Number"))
                                .setText("Serial Number"))
                .setSystem("https://test.local/sno")
                .setValue("0ae7175b-0e80-4cc5-893a-736078f5f1d1")
                .setPeriod(new Period().setStart(start).setEnd(end))
                .setAssigner(new Reference("Organization/af7e7210-60c6-4f62-9b5e-3f070fc10551"));
    }
}
