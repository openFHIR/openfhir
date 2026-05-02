package com.syntaric.openfhir.util;

import ca.uhn.fhir.context.FhirContext;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Annotation;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Test;

public class FhirInstanceCreatorTest {

    final com.syntaric.openfhir.util.OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
    private com.syntaric.openfhir.util.FhirInstanceCreator fhirInstanceCreator = new com.syntaric.openfhir.util.FhirInstanceCreator(openFhirStringUtils, new FhirInstanceCreatorUtility(openFhirStringUtils));

    private FhirPathR4 fhirPathR4 = new FhirPathR4(FhirContext.forR4());

    @Test
    public void testInstantiation() {
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category.coding.code", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof CodeType);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category.coding.display", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof StringType);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.category.coding", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.status", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof Enumeration);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.statusReason", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof CodeableConcept);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.doNotPerform", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof BooleanType);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.subject", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof Reference);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.groupIdentifier", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof Identifier);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.dosageInstruction", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.note", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof List);
        Assert.assertTrue(getLastReturn(fhirInstanceCreator.instantiateAndSetElement(new MedicationRequest(), MedicationRequest.class, "MedicationRequest.dispenseRequest", null, null, "org.hl7.fhir.r4.model.")).getReturning() instanceof MedicationRequest.MedicationRequestDispenseRequestComponent);
    }

    @Test
    public void testInstantiationAndSetting_note() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.note";
        final Object returning = fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null, null, "org.hl7.fhir.r4.model.").getReturning();
        final Annotation annotation = (Annotation) ((List) returning).get(0);
        annotation.setText("annotation text");
        final Optional<Annotation> evaluate = fhirPathR4.evaluateFirst(resource, fhirPath, Annotation.class);
        Assert.assertEquals("annotation text", evaluate.get().getText());

        // since this is actually a list, see if the first one is deleted when adding another one
        final Object returning1 = fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null, null, "org.hl7.fhir.r4.model.").getReturning();
        final Annotation secondAnnotation = (Annotation) ((List) returning1).get(1);
        secondAnnotation.setText("2annotation text2");
        final List<Annotation> evaluatedAll = fhirPathR4.evaluate(resource, fhirPath, Annotation.class);
        Assert.assertEquals(2, evaluatedAll.size());
        Assert.assertEquals("annotation text", evaluatedAll.get(0).getText());
        Assert.assertEquals("2annotation text2", evaluatedAll.get(1).getText());
    }

    @Test
    public void testInstantiationAndSetting_primitive() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.doNotPerform";
        final BooleanType doNotPerform = (BooleanType) fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class,
                fhirPath, null, null, "org.hl7.fhir.r4.model.").getReturning();
        doNotPerform.setValue(true);
        final Optional<BooleanType> evaluate = fhirPathR4.evaluateFirst(resource, fhirPath, BooleanType.class);
        Assert.assertEquals(true, evaluate.get().getValue());
    }

    @Test
    public void testInstantiationAndSetting_chainedFhirPath() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.category.coding.code";
        final com.syntaric.openfhir.util.FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn = fhirInstanceCreator.instantiateAndSetElement(resource,
                                                                                                                                                            MedicationRequest.class,
                                                                                                                                                            fhirPath, null, null, "org.hl7.fhir.r4.model.");
        final CodeType categoryDodingCode = (CodeType) getLastReturn(instantiateAndSetReturn).getReturning();
        categoryDodingCode.setValue("category coding code value");
        final Optional<CodeType> evaluate = fhirPathR4.evaluateFirst(resource, fhirPath, CodeType.class);
        Assert.assertEquals("category coding code value", evaluate.get().getCode());
    }

    @Test
    public void testInstantiationAndSetting_chainedFhirPath_resolve() {

        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.medication.resolve().code.text";
        final com.syntaric.openfhir.util.FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn = fhirInstanceCreator.instantiateAndSetElement(resource,
                                                                                                                                                            MedicationRequest.class,
                                                                                                                                                            fhirPath, null, "Medication", "org.hl7.fhir.r4.model.");
        StringType medicationText = (StringType) getLastReturn(instantiateAndSetReturn).getReturning();
        medicationText.setValue("This is medication text");

        final Medication medication = (Medication) resource.getMedicationReference().getResource();
        Assert.assertEquals("This is medication text", medication.getCode().getText());
    }

    private com.syntaric.openfhir.util.FhirInstanceCreator.InstantiateAndSetReturn getLastReturn(final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn) {
        if (instantiateAndSetReturn.getInner() == null) {
            return instantiateAndSetReturn;
        }
        return getLastReturn(instantiateAndSetReturn.getInner());
    }
}