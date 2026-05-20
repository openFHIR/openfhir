package com.syntaric.openfhir.mapping.bloodpressure;

import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.nedap.archie.rm.datastructures.Element;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.nedap.archie.rm.generic.Participation;
import com.nedap.archie.rm.generic.PartyIdentified;
import com.syntaric.openfhir.mapping.GenericTest;
import java.io.IOException;
import java.util.List;
import lombok.SneakyThrows;
import openEHR.v1.template.OBSERVATION;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

public class BloodPressureBidirectionalTest extends GenericTest {

    final String MODEL_MAPPINGS = "/blood_pressure/";
    final String CONTEXT_MAPPING = "/blood_pressure/simple-blood-pressure.context.yml";
    final String HELPER_LOCATION = "/blood_pressure/";
    final String OPT = "Blood Pressure.opt";
    final String FLAT = "blood-pressure_flat.json";


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void bloodPressureToFhirToOpenEhr() throws IOException {
        // have a composition from config
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             webTemplate);

        // transform it to FHIR
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        BloodPressureToFhirTest.assertBloodPressureFhir(
                bundle); // this is being tested elsewhere but whatever.., why not

        Assert.assertEquals(2, ((Observation) bundle.getEntry().get(0).getResource()).getFocus().size());
        Assert.assertEquals("value0", ((Observation) bundle.getEntry().get(0).getResource()).getFocus().get(0).getIdentifier().getValue());
        Assert.assertEquals("name0", ((Observation) bundle.getEntry().get(0).getResource()).getFocus().get(0).getDisplay());
        Assert.assertEquals("value1", ((Observation) bundle.getEntry().get(0).getResource()).getFocus().get(1).getIdentifier().getValue());
        Assert.assertEquals("name1", ((Observation) bundle.getEntry().get(0).getResource()).getFocus().get(1).getDisplay());
        Assert.assertEquals("val1", ((Observation) bundle.getEntry().get(0).getResource()).getDataAbsentReason().getText());

        // transform it back to openEHR
        final Composition rmComposition = toOpenEhr.fhirToCompositionRm(context, bundle, webTemplate);

        List<Participation> otherParticipations = ((com.nedap.archie.rm.composition.Observation) rmComposition.getContent().get(0)).getOtherParticipations();
        Assert.assertEquals(3, otherParticipations.size());

        Assert.assertEquals("val1", otherParticipations.get(1).getFunction().getValue());
        Assert.assertEquals("name1", ((PartyIdentified) otherParticipations.get(1).getPerformer()).getName());
        Assert.assertEquals("assigner1", ((PartyIdentified) otherParticipations.get(1).getPerformer()).getIdentifiers().get(0).getAssigner());
        Assert.assertEquals("value1", otherParticipations.get(1).getPerformer().getExternalRef().getId().getValue());

        final String systolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value";
        final String diastolicPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value";

        final String interpretationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at1059]";
        final String descriptionPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/data[at0001]/events[at0006]/data[at0003]/items[at0033]";
        final String locationPath = "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]/protocol[at0011]/items[at0014]";

        final List<Object> objects = composition.itemsAtPath(systolicPath);
        if (objects.isEmpty()) {
            Assert.fail();
        }
        for (Object systolicValues : objects) {
            final Double systolicMagnitude = ((DvQuantity) systolicValues).getMagnitude();
            Assert.assertTrue(rmComposition.itemsAtPath(systolicPath).stream()
                                      .anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(systolicMagnitude)));
        }
        final List<Object> objects1 = composition.itemsAtPath(diastolicPath);
        if (objects1.isEmpty()) {
            Assert.fail();
        }
        for (Object diastolicValues : objects1) {
            final Double systolicMagnitude = ((DvQuantity) diastolicValues).getMagnitude();
            Assert.assertTrue(rmComposition.itemsAtPath(diastolicPath).stream()
                                      .anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(systolicMagnitude)));
        }
        final List<Object> objects2 = composition.itemsAtPath(interpretationPath);
        if (objects2.isEmpty()) {
            Assert.fail();
        }
        for (Object interpretationValues : objects2) {
            final String interpretationValue = ((DvText) ((Element) interpretationValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(interpretationPath).stream().anyMatch(
                    item -> ((DvText) ((Element) item).getValue()).getValue().equals(interpretationValue)));
        }
        final List<Object> objects3 = composition.itemsAtPath(descriptionPath);
        if (objects3.isEmpty()) {
            Assert.fail();
        }
        for (Object descriptionValues : objects3) {
            final String descriptionValue = ((DvText) ((Element) descriptionValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(descriptionPath).stream().anyMatch(
                    item -> ((DvText) ((Element) item).getValue()).getValue().equals(descriptionValue)));
        }
        final List<Object> objects4 = composition.itemsAtPath(locationPath);
        if (objects4.isEmpty()) {
            Assert.fail();
        }
        for (Object locationValues : objects4) {
            final String locationValue = ((DvText) ((Element) locationValues).getValue()).getValue();
            Assert.assertTrue(rmComposition.itemsAtPath(locationPath).stream().anyMatch(
                    item -> ((DvText) ((Element) item).getValue()).getValue().equals(locationValue)));
        }

    }


}
