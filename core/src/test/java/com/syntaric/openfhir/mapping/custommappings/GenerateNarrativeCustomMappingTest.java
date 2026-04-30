package com.syntaric.openfhir.mapping.custommappings;

import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class GenerateNarrativeCustomMappingTest {

    private final GenerateNarrativeCustomMapping mapping = new GenerateNarrativeCustomMapping();

    @Test
    public void testMappingCode() {
        Assert.assertTrue(mapping.mappingCodes().contains("generateNarrative"));
        Assert.assertEquals(1, mapping.mappingCodes().size());
    }

    @Test
    public void testGenerateNarrative_r4Patient() {
        final Patient patient = new Patient();
        patient.addName(new HumanName().setFamily("Smith").addGiven("John"));

        final MappingHelper mappingHelper = Mockito.mock(MappingHelper.class);
        Mockito.when(mappingHelper.getGeneratingFhirRoot()).thenReturn(patient);
        Mockito.when(mappingHelper.getOriginalFhirPath()).thenReturn("$resource");

        final var result = mapping.applyOpenEhrToFhirMapping(
                mappingHelper, null, null, null, null, null, null, null, null);

        Assert.assertNull(result); // always returns null, side-effect is on the resource
        Assert.assertNotNull(patient.getText());
        Assert.assertNotNull(patient.getText().getStatus());
        Assert.assertFalse(patient.getText().getDivAsString().isEmpty());
    }

    @Test
    public void testGenerateNarrative_nonResource_returnsNull() {
        final MappingHelper mappingHelper = Mockito.mock(MappingHelper.class);
        Mockito.when(mappingHelper.getGeneratingFhirResource()).thenReturn(new HumanName());

        final var result = mapping.applyOpenEhrToFhirMapping(
                mappingHelper, null, null, null, null, null, null, null, null);

        Assert.assertNull(result);
    }
}
