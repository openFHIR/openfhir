package com.syntaric.openfhir.mapping.problemlist;

import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class ProblemListToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/problemlist/";
    final String CONTEXT_MAPPING = "/problemlist/problem_list_nl.context.yml";
    final String HELPER_LOCATION = "/problemlist/";
    final String OPT = "ProblemList.opt";
    final String FLAT_TEXT_VALUE = "flat_textvalue.json";
    final String FLAT_CODEABLE_CONCEPT = "flat_codeableconcept.json";


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized =  IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhir_textValue() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_TEXT_VALUE), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);

        final org.hl7.fhir.r4.model.Composition composition = (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();
        final Condition condition = (Condition) composition.getSectionFirstRep().getEntryFirstRep().getResource();
        final Observation observation = (Observation) condition.getEvidenceFirstRep().getDetailFirstRep().getResource();

        Assert.assertEquals("Fine", observation.getValueStringType().getValue());
    }

    @Test
    public void toFhir_codeableConcept() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_CODEABLE_CONCEPT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);

        final org.hl7.fhir.r4.model.Composition composition = (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();
        final Condition condition = (Condition) composition.getSectionFirstRep().getEntryFirstRep().getResource();
        final Observation observation = (Observation) condition.getEvidenceFirstRep().getDetailFirstRep().getResource();

        final CodeableConcept valueCodeableConcept = observation.getValueCodeableConcept();
        Assert.assertEquals("code", valueCodeableConcept.getCodingFirstRep().getCode());
        Assert.assertEquals("vall", valueCodeableConcept.getCodingFirstRep().getDisplay());
        Assert.assertEquals("term", valueCodeableConcept.getCodingFirstRep().getSystem());
        Assert.assertEquals("vall", valueCodeableConcept.getText());
    }
}
