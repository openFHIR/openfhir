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
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
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

    @Test
    public void reWalkingSingleValuedParent_reusesIt() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.statusReason.coding.code";

        final CodeType first = (CodeType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class, fhirPath, null, null, R4)).getReturning();
        first.setValue("first");
        final CodeType second = (CodeType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class, fhirPath, null, null, R4)).getReturning();
        second.setValue("second");

        // statusReason is 0..1 so it is continued into, coding is 0..* so it appends
        Assert.assertEquals(2, resource.getStatusReason().getCoding().size());
        Assert.assertEquals("first", resource.getStatusReason().getCoding().get(0).getCode());
        Assert.assertEquals("second", resource.getStatusReason().getCoding().get(1).getCode());
    }

    @Test
    public void reWalkingCastParent_reusesIt() {
        final Observation resource = new Observation();
        final String fhirPath = "Observation.value.as(CodeableConcept).coding.code";

        final CodeType first = (CodeType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                Observation.class, fhirPath, null, null, R4)).getReturning();
        first.setValue("first");
        final CodeType second = (CodeType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                Observation.class, fhirPath, null, null, R4)).getReturning();
        second.setValue("second");

        Assert.assertTrue(resource.getValue() instanceof CodeableConcept);
        Assert.assertEquals(2, resource.getValueCodeableConcept().getCoding().size());
        Assert.assertEquals("first", resource.getValueCodeableConcept().getCoding().get(0).getCode());
    }

    @Test
    public void castToDifferentChoiceType_stillReplaces() {
        final Observation resource = new Observation();

        final CodeType code = (CodeType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                Observation.class, "Observation.value.as(CodeableConcept).coding.code", null, null, R4))
                .getReturning();
        code.setValue("first");
        fhirInstanceCreator.instantiateAndSetElement(resource, Observation.class,
                "Observation.value.as(Quantity).value", null, null, R4);

        Assert.assertTrue(resource.getValue() instanceof Quantity);
    }

    @Test
    public void reWalkingThroughReference_stillReplacesIt() {
        // an identifier-only Reference (fallback when the target resource is not available) is superseded by
        // the resolved one — a $reference mapping means "point this at the resource built here"
        final MedicationRequest resource = new MedicationRequest();

        final StringType identifierValue = (StringType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(
                resource, MedicationRequest.class, "MedicationRequest.medication.as(Reference).identifier.value",
                null, null, R4)).getReturning();
        identifierValue.setValue("logical id");
        final StringType text = (StringType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class, "MedicationRequest.medication.resolve().code.text", null, "Medication", R4))
                .getReturning();
        text.setValue("medication text");

        final Reference reference = resource.getMedicationReference();
        Assert.assertFalse(reference.hasIdentifier());
        Assert.assertEquals("medication text", ((Medication) reference.getResource()).getCode().getText());

        // and a second resolve() walk builds a fresh resource rather than merging into the first
        fhirInstanceCreator.instantiateAndSetElement(resource, MedicationRequest.class,
                "MedicationRequest.medication.resolve().code.coding.code", null, "Medication", R4);
        final Medication second = (Medication) resource.getMedicationReference().getResource();
        Assert.assertNull(second.getCode().getText());
    }

    @Test
    public void reWalkingListParent_stillAppends() {
        final MedicationRequest resource = new MedicationRequest();
        final String fhirPath = "MedicationRequest.category.coding.code";

        final CodeType first = (CodeType) getLastReturn(fhirInstanceCreator.instantiateAndSetElement(resource,
                MedicationRequest.class, fhirPath, null, null, R4)).getReturning();
        first.setValue("first");
        fhirInstanceCreator.instantiateAndSetElement(resource, MedicationRequest.class, fhirPath, null, null, R4);

        // category is 0..* so a second walk is a second category, not a second coding on the first
        Assert.assertEquals(2, resource.getCategory().size());
        Assert.assertEquals(1, resource.getCategory().get(0).getCoding().size());
        Assert.assertEquals("first", resource.getCategory().get(0).getCodingFirstRep().getCode());
        Assert.assertEquals(1, resource.getCategory().get(1).getCoding().size());
    }

    private static final String R4 = "org.hl7.fhir.r4.model.";

    private com.syntaric.openfhir.util.FhirInstanceCreator.InstantiateAndSetReturn getLastReturn(final FhirInstanceCreator.InstantiateAndSetReturn instantiateAndSetReturn) {
        if (instantiateAndSetReturn.getInner() == null) {
            return instantiateAndSetReturn;
        }
        return getLastReturn(instantiateAndSetReturn.getInner());
    }
}