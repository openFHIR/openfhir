package com.syntaric.openfhir.mapping.custommappings;

import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

public class GenerateNarrativeCustomMappingTest {

    private final GenerateNarrativeCustomMapping mapping = new GenerateNarrativeCustomMapping();

    // --- extractCode / extractArgument ---

    @Test
    public void testExtractCode_noArgument() {
        Assert.assertEquals("generateNarrative", CustomMapping.extractCode("generateNarrative"));
    }

    @Test
    public void testExtractCode_withArgument() {
        Assert.assertEquals("generateNarrative", CustomMapping.extractCode("generateNarrative($resource.subject)"));
    }

    @Test
    public void testExtractArgument_present() {
        Assert.assertEquals("$resource.subject", CustomMapping.extractArgument("generateNarrative($resource.subject)"));
    }

    @Test
    public void testExtractArgument_absent() {
        Assert.assertNull(CustomMapping.extractArgument("generateNarrative"));
    }

    @Test
    public void testExtractArgument_emptyParens() {
        Assert.assertNull(CustomMapping.extractArgument("generateNarrative()"));
    }

    // --- getResourceToGenerateNarrativeOf: $resource shorthand ---

    @Test
    public void testGetResources_dollarResource_returnsGeneratingFhirResource() {
        final Patient patient = new Patient();
        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirResource()).thenReturn(patient);

        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "$resource", Spec.Version.R4, null);
        Assert.assertSame(patient, result);
    }

    // --- getResourceToGenerateNarrativeOf: $fhirRoot shorthand ---

    @Test
    public void testGetResources_dollarFhirRoot_returnsGeneratingFhirRoot() {
        final Bundle bundle = new Bundle();
        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirRoot()).thenReturn(bundle);

        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "$fhirRoot", Spec.Version.R4, null);

        Assert.assertSame(bundle, result);
    }

    // --- getResourceToGenerateNarrativeOf: $resource.path evaluated from generatingFhirResource ---

    @Test
    public void testGetResources_dollarResourcePath_evaluatesFromGeneratingResource() {
        final Patient patient = new Patient();
        patient.addName(new HumanName().setFamily("Smith"));
        final Bundle bundle = new Bundle(); // root — should NOT be used

        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirResource()).thenReturn(patient);
        Mockito.when(helper.getGeneratingFhirRoot()).thenReturn(bundle);
        Mockito.when(helper.getOriginalOpenEhrPath()).thenReturn("someOpenEhrPath");

        // $resource.name resolves to HumanName — not an IBaseResource, so resolveReference
        // returns null for non-REFERENCE openEHR path. We instead use a path that yields a resource:
        // wrap patient in a Bundle and ask for Bundle.entry.resource via $resource path isn't possible
        // directly, so test with a contained resource instead.
        final Patient contained = new Patient();
        contained.setId("contained-1");
        patient.addContained(contained);

        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "$resource.contained", Spec.Version.R4, null);

        Assert.assertSame(contained, result);
    }

    // --- getResourceToGenerateNarrativeOf: plain fhir path evaluated from generatingFhirRoot ---

    @Test
    public void testGetResources_plainPath_evaluatesFromGeneratingFhirRoot() {
        final Patient patient = new Patient();
        final Bundle bundle = new Bundle();
        bundle.addEntry().setResource(patient);

        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirResource()).thenReturn(patient); // used for version detection
        Mockito.when(helper.getGeneratingFhirRoot()).thenReturn(bundle);
        Mockito.when(helper.getOriginalOpenEhrPath()).thenReturn("someOpenEhrPath");

        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "Bundle.entry.resource", Spec.Version.R4, null);

        Assert.assertSame(patient, result);
    }

    // --- getResourceToGenerateNarrativeOf: generatingFhirRoot is a List ---

    @Test
    public void testGetResources_rootIsList_iteratesEachEntry() {
        final Patient patient1 = new Patient();
        final Patient contained1 = new Patient();
        contained1.setId("c1");
        patient1.addContained(contained1);

        final Patient patient2 = new Patient();
        final Patient contained2 = new Patient();
        contained2.setId("c2");
        patient2.addContained(contained2);

        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirResource()).thenReturn(patient1); // version detection
        Mockito.when(helper.getGeneratingFhirRoot()).thenReturn(List.of(patient1, patient2));
        Mockito.when(helper.getOriginalOpenEhrPath()).thenReturn("someOpenEhrPath");

        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "Patient.contained", Spec.Version.R4, null);

        Assert.assertTrue(result instanceof Bundle);
        Assert.assertEquals(2, ((Bundle) result).getEntry().size());
    }

    // --- getResourceToGenerateNarrativeOf: BundleEntryComponent unwrapped via getResource() ---

    @Test
    public void testGetResources_bundleEntryComponent_unwrapsResource() {
        final Patient patient = new Patient();
        final Bundle bundle = new Bundle();
        bundle.addEntry().setResource(patient);

        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirResource()).thenReturn(patient);
        Mockito.when(helper.getGeneratingFhirRoot()).thenReturn(bundle);
        Mockito.when(helper.getOriginalOpenEhrPath()).thenReturn("someOpenEhrPath");

        // Bundle.entry yields BundleEntryComponent — resolveReference should extract .getResource()
        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "Bundle.entry", Spec.Version.R4, null);

        Assert.assertSame(patient, result);
    }

    // --- getResourceToGenerateNarrativeOf: IBaseReference with $reference openEHR path → resolve() ---

    @Test
    public void testGetResources_referenceWithDollarReference_resolves() {
        final Patient patient = new Patient();
        patient.setId("p1");
        final Reference ref = new Reference();
        ref.setResource(patient);

        final Bundle bundle = new Bundle();
        bundle.addEntry().setResource(patient);
        // Patient has a generalPractitioner reference — set it to point to the same patient for simplicity
        patient.addGeneralPractitioner(ref);

        final MappingHelper helper = Mockito.mock(MappingHelper.class);
        Mockito.when(helper.getGeneratingFhirResource()).thenReturn(patient);
        Mockito.when(helper.getGeneratingFhirRoot()).thenReturn(patient);
        Mockito.when(helper.getOriginalOpenEhrPath()).thenReturn("$reference");

        // Patient.generalPractitioner yields a Reference; with $reference openEHR path, resolve() is called
        final IBaseResource result = mapping.getResourceToGenerateNarrativeOf(helper, "Patient.generalPractitioner", Spec.Version.R4, null);

        Assert.assertSame(patient, result);
    }
}
