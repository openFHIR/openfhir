package com.syntaric.openfhir.mapping.medicationorder;

import ca.uhn.fhir.fhirpath.IFhirPathEvaluationContext;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.DvText;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.syntaric.openfhir.mapping.GenericTest;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;

public class MedicationOrderToOpenEhrTest extends GenericTest {

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
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void medicationOrder_RM() {
        final Bundle bundle = testMedicationMedicationRequestBundle();
        fhirPath.setEvaluationContext(new IFhirPathEvaluationContext() {
            @Override
            public IBase resolveReference(@Nonnull IIdType theReference, @Nullable IBase theContext) {
                return ((Reference) theContext).getResource();
            }
        });
        final List<Base> medicationText = fhirPath.evaluate(bundle,
                                                            "Bundle.entry.resource.ofType(MedicationRequest).medication.resolve().code.text",
                                                            Base.class);
        Assert.assertEquals("medication text", medicationText.get(0).toString());

        final Composition composition = toOpenEhr.fhirToCompositionRm(context, bundle, operationaltemplate);
        final String medicationTextPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[at0070]/value";
        final String doseAmountPath = "/content[openEHR-EHR-INSTRUCTION.medication_order.v2]/activities[at0001]/description[at0002]/items[openEHR-EHR-CLUSTER.therapeutic_direction.v1]/items[openEHR-EHR-CLUSTER.dosage.v1]/items[at0144]/value";
        Assert.assertEquals("medication text", ((DvText) composition.itemAtPath(medicationTextPath)).getValue());
        Assert.assertEquals(Double.valueOf(111.0),
                            ((DvQuantity) composition.itemAtPath(doseAmountPath)).getMagnitude());
    }

    public static Bundle testMedicationMedicationRequestBundle() {
        final Bundle bundle = new Bundle();
        final Bundle.BundleEntryComponent medicationEntry = new Bundle.BundleEntryComponent();
        final Medication medication = new Medication();
        final String medicationUuid = UUID.randomUUID().toString();
        medication.setId(medicationUuid);
        medication.setCode(new CodeableConcept().setText("medication text"));
        medicationEntry.setResource(medication);
        medicationEntry.setFullUrl("Medication/" + medicationUuid);
        bundle.addEntry(medicationEntry);

        final Bundle.BundleEntryComponent medicationRequestEntry = new Bundle.BundleEntryComponent();
        final MedicationRequest medicationRequest = new MedicationRequest();
        final Dosage dosage = new Dosage();
        final Dosage.DosageDoseAndRateComponent doseAndRate = new Dosage.DosageDoseAndRateComponent();
        doseAndRate.setDose(new Quantity(111).setUnit("unit"));
        dosage.addDoseAndRate(doseAndRate);
        medicationRequest.addDosageInstruction(dosage);
        final Reference value = new Reference("Medication/" + medicationUuid);
        value.setResource(medication);
        medicationRequest.setMedication(value);
        medicationRequestEntry.setResource(medicationRequest);
        bundle.addEntry(medicationRequestEntry);
        return bundle;
    }

}
