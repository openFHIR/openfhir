package com.syntaric.openfhir.mapping.tofhir;

import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.FhirInstanceCreator;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ToFhirMappingEngineInstantiateTest {

    private ToFhirInstantiator toFhirInstantiator;

    @Before
    public void setUp() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(
                openFhirStringUtils);
        final FhirInstanceCreator fhirInstanceCreator = new FhirInstanceCreator(openFhirStringUtils,
                                                                                fhirInstanceCreatorUtility);
        toFhirInstantiator = new ToFhirInstantiator(fhirInstanceCreator);
    }

    @Test
    public void testInstantiation_overwritting() {
        final MappingHelper mappingHelper = new MappingHelper();
        final Condition generatingFhirResource = new Condition();
        mappingHelper.setGeneratingFhirResource(generatingFhirResource);
        mappingHelper.setGeneratingFhirRoot(generatingFhirResource);
        mappingHelper.setOriginalFhirPath("$resource.code.coding.code");
        mappingHelper.setFhir("code.coding.code");
        final Object firstObj = toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1,
                Spec.Version.R4.modelPackage());
        Assert.assertNotNull(generatingFhirResource.getCode().getCodingFirstRep().getCodeElement());
        ((CodeType) firstObj).setValue("test");

        // now if we do it again, we should have 1 code because Condition.code was overwritten
        toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertEquals(1, generatingFhirResource.getCode().getCoding().size());
        Assert.assertNull(generatingFhirResource.getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void testInstantiation_appending_code() {
        final MappingHelper mappingHelper = new MappingHelper();
        final Condition generatingFhirResource = new Condition();
        final CodeableConcept codeableConcept = new CodeableConcept();
        generatingFhirResource.setCode(codeableConcept);

        mappingHelper.setGeneratingFhirResource(generatingFhirResource);
        mappingHelper.setGeneratingFhirRoot(codeableConcept);
        mappingHelper.setOriginalFhirPath("coding.code");
        mappingHelper.setFhir("coding.code");
        final Object firstObj = toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertNotNull(generatingFhirResource.getCode().getCodingFirstRep().getCodeElement());
        ((CodeType) firstObj).setValue("test");

        // now if we do it again, we should have 2 codes because Condition.coding should be appended to as it's a list
        toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertEquals(2, generatingFhirResource.getCode().getCoding().size());
        Assert.assertEquals("test", generatingFhirResource.getCode().getCodingFirstRep().getCode());
    }

    @Test
    public void testInstantiation_appending_array() {
        final MappingHelper mappingHelper = new MappingHelper();
        final Patient generatingFhirResource = new Patient();

        mappingHelper.setGeneratingFhirResource(generatingFhirResource);
        mappingHelper.setGeneratingFhirRoot(generatingFhirResource);
        mappingHelper.setOriginalFhirPath("$resource.name.given");
        mappingHelper.setFhir("name.given");
        toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertFalse(generatingFhirResource.getNameFirstRep().getGiven().isEmpty());
        generatingFhirResource.getNameFirstRep().getGiven().get(0).setValue("name");

        // now if we do it again, we should have 2 HumanNames
        toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertEquals(2, generatingFhirResource.getName().size());
        Assert.assertEquals("name", generatingFhirResource.getNameFirstRep().getGiven().get(0).getValueAsString());
    }

    @Test
    public void testInstantiation_appending_simpleArray() {
        final MappingHelper mappingHelper = new MappingHelper();
        final Patient generatingFhirResource = new Patient();
        final HumanName humanName = new HumanName();
        generatingFhirResource.addName(humanName);

        mappingHelper.setGeneratingFhirResource(generatingFhirResource);
        mappingHelper.setGeneratingFhirRoot(humanName);
        mappingHelper.setOriginalFhirPath("given");
        mappingHelper.setFhir("given");
        toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertFalse(generatingFhirResource.getNameFirstRep().getGiven().isEmpty());
        generatingFhirResource.getNameFirstRep().getGiven().get(0).setValue("name");

        // now if we do it again, we should have 1 HumanNames and the first humen name should have 2 givens
        toFhirInstantiator.instantiateElement(mappingHelper, null, null, -1, Spec.Version.R4.modelPackage());
        Assert.assertEquals(1, generatingFhirResource.getName().size());
        Assert.assertEquals(2, generatingFhirResource.getNameFirstRep().getGiven().size());
        Assert.assertEquals("name", generatingFhirResource.getNameFirstRep().getGiven().get(0).getValueAsString());
    }

    @Test
    public void testInstantiation_appending() {
        final MappingHelper mappingHelper = new MappingHelper();
        final Encounter encounter = new Encounter();

        mappingHelper.setGeneratingFhirResource(encounter);
        mappingHelper.setGeneratingFhirRoot(encounter);
        mappingHelper.setOriginalFhirPath("$resource.diagnosis.condition.as(Reference).resolve().code");
        mappingHelper.setFhir("diagnosis.condition.as(Reference).resolve().code");
        toFhirInstantiator.instantiateElement(mappingHelper, null, "Condition", -1, Spec.Version.R4.modelPackage());
        Assert.assertEquals(1, encounter.getDiagnosis().size());
        ((Condition) encounter.getDiagnosis().get(0).getCondition().getResource()).getCode().setText("test");

        toFhirInstantiator.instantiateElement(mappingHelper, null, "Condition", -1, Spec.Version.R4.modelPackage());
        Assert.assertEquals(2, encounter.getDiagnosis().size());
        Assert.assertEquals("test", ((Condition) encounter.getDiagnosis().get(0).getCondition().getResource())
                .getCode().getText());
    }
}
