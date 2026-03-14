package com.syntaric.openfhir.mapping.helpers;

import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class MedicationOrderHelpersTest extends GenericHelpersTest {

    final String MODEL_MAPPINGS = "/medication_order/";
    final String CONTEXT_MAPPING = "/medication_order/medication-order.context.yml";
    final String HELPER_LOCATION = "/medication_order/";
    final String OPT = "medication order.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
    }

    @Test
    public void assertMedicationOrderHelpers() {
        final String templateId = context.getContext().getTemplate().getId();
        final String start = context.getContext().getStart();

        final Map<String, List<MappingHelper>> allHelpersPerArchetype = helpersCreator.constructHelpers(templateId, start,
                                                                                                        context.getContext().getArchetypes(),
                                                                                                        webTemplate);
        final List<MappingHelper> allHelpers = allHelpersPerArchetype.get("openEHR-EHR-INSTRUCTION.medication_order.v2");

        // Chain: medication_order (4 helpers), with therapeutic-direction containing dosage as nested child,
        // and dosage containing directionDuration + doseQuantityValue as nested children.
        Assert.assertEquals(4, allHelpers.size());

        // --- medication ---
        final MappingHelper medication = allHelpers.get(0);
        Assert.assertEquals("medication", medication.getMappingName());
        Assert.assertEquals("medication.as(Reference).resolve()", medication.getFhir());
        Assert.assertEquals("MedicationRequest.medication.as(Reference).resolve()", medication.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-INSTRUCTION.medication_order.v2]", medication.getOpenEhr());

        // --- note ---
        final MappingHelper note = allHelpers.get(1);
        Assert.assertEquals("note", note.getMappingName());
        Assert.assertEquals("note.text", note.getFhir());
        Assert.assertEquals("MedicationRequest.note.text", note.getFullFhirPath());
        Assert.assertEquals(
                "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[at0044]",
                note.getOpenEhr());
        Assert.assertEquals("medication_order/medication_order/order[n]/additional_instruction[n]",
                            note.getFullOpenEhrFlatPath());
        Assert.assertTrue(note.getPossibleRmTypes().contains("DV_TEXT"));

        // --- time ---
        final MappingHelper time = allHelpers.get(2);
        Assert.assertEquals("time", time.getMappingName());
        Assert.assertEquals("authoredOn", time.getFhir());
        Assert.assertEquals("MedicationRequest.authoredOn", time.getFullFhirPath());
        Assert.assertEquals(
                "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[at0113]/items[at0012]",
                time.getOpenEhr());
        Assert.assertEquals("medication_order/medication_order/order[n]/order_details/order_start_date_time",
                            time.getFullOpenEhrFlatPath());
        Assert.assertTrue(time.getPossibleRmTypes().contains("DV_DATE_TIME"));

        // --- therapeutic-direction (has dosage as nested child) ---
        final MappingHelper therapeuticDirection = allHelpers.get(3);
        Assert.assertEquals("therapeutic-direction", therapeuticDirection.getMappingName());
        Assert.assertEquals("MedicationRequest", therapeuticDirection.getFhir());
        Assert.assertEquals("MedicationRequest", therapeuticDirection.getFullFhirPath());
        Assert.assertEquals(
                "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]",
                therapeuticDirection.getOpenEhr());
        Assert.assertEquals("medication_order/medication_order/order[n]/therapeutic_direction",
                            therapeuticDirection.getFullOpenEhrFlatPath());
//        Assert.assertTrue(therapeuticDirection.getPossibleRmTypes().contains("CLUSTER"));
        Assert.assertEquals(1, therapeuticDirection.getChildren().size());

        // --- dosage (child of therapeutic-direction, has directionDuration + doseQuantityValue as children) ---
        final MappingHelper dosage = therapeuticDirection.getChildren().get(0);
        Assert.assertEquals("dosage", dosage.getMappingName());
        Assert.assertEquals("dosageInstruction", dosage.getFhir());
        Assert.assertEquals("MedicationRequest.dosageInstruction", dosage.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-CLUSTER.therapeutic_direction.v1/items[openEHR-EHR-CLUSTER.dosage.v1]",
                            dosage.getOpenEhr());
        Assert.assertEquals(
                "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]",
                dosage.getFullOpenEhrPath());
        Assert.assertEquals("medication_order/medication_order/order[n]/therapeutic_direction/dosage",
                            dosage.getFullOpenEhrFlatPath());
//        Assert.assertTrue(dosage.getPossibleRmTypes().contains("CLUSTER"));
        Assert.assertEquals(2, dosage.getChildren().size());

        // --- directionDuration (first child of dosage) ---
        final MappingHelper directionDuration = dosage.getChildren().get(0);
        Assert.assertEquals("directionDuration", directionDuration.getMappingName());
        Assert.assertEquals("additionalInstruction", directionDuration.getFhir());
        Assert.assertEquals("MedicationRequest.dosageInstruction.additionalInstruction",
                            directionDuration.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-CLUSTER.therapeutic_direction.v1/items[at0066]/value",
                            directionDuration.getOpenEhr());
        Assert.assertEquals(
                "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[at0066]/value",
                directionDuration.getFullOpenEhrPath());
        Assert.assertEquals("medication_order/medication_order/order[n]/therapeutic_direction/direction_duration/coded_text_value",
                            directionDuration.getFullOpenEhrFlatPath());
        Assert.assertTrue(directionDuration.getPossibleRmTypes().contains("DV_CODED_TEXT"));

        // --- doseQuantityValue (second child of dosage) ---
        final MappingHelper doseQuantityValue = dosage.getChildren().get(1);
        Assert.assertEquals("doseQuantityValue", doseQuantityValue.getMappingName());
        Assert.assertEquals("doseAndRate.dose", doseQuantityValue.getFhir());
        Assert.assertEquals("MedicationRequest.dosageInstruction.doseAndRate.dose",
                            doseQuantityValue.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-CLUSTER.dosage.v1/items[at0144]", doseQuantityValue.getOpenEhr());
        Assert.assertEquals(
                "openEHR-EHR-INSTRUCTION.medication_order.v2/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]/items[at0144]",
                doseQuantityValue.getFullOpenEhrPath());
        Assert.assertEquals("medication_order/medication_order/order[n]/therapeutic_direction/dosage/dose_amount",
                            doseQuantityValue.getFullOpenEhrFlatPath());
        Assert.assertTrue(doseQuantityValue.getPossibleRmTypes().contains("DV_QUANTITY"));
    }

}
