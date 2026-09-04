package com.syntaric.openfhir.operations;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ProvenanceGeneratorTest {

    private ProvenanceGenerator generator;

    @Before
    public void setUp() {
        generator = new ProvenanceGenerator("Device/openfhir-engine", "openFHIR engine");
    }

    private Bundle threeEntryBundle() {
        final Bundle bundle = new Bundle();
        bundle.addEntry().setFullUrl("urn:uuid:11111111-1111-1111-1111-111111111111")
                .setResource(new Observation());
        final Patient withId = new Patient();
        withId.setId("pat-1");
        bundle.addEntry().setResource(withId);
        bundle.addEntry().setResource(new Observation()); // neither fullUrl nor id
        return bundle;
    }

    @Test
    public void targetsCoverAllEntriesAndAssignMissingFullUrls() {
        final Bundle bundle = threeEntryBundle();

        final Provenance provenance = generator.generate(bundle, MappingCallContext.empty(), "template-1");

        Assert.assertEquals(3, provenance.getTarget().size());
        Assert.assertEquals("urn:uuid:11111111-1111-1111-1111-111111111111",
                provenance.getTarget().get(0).getReference());
        Assert.assertEquals("Patient/pat-1", provenance.getTarget().get(1).getReference());
        // the id-less entry got a urn:uuid fullUrl assigned and targeted
        final String assigned = bundle.getEntry().get(2).getFullUrl();
        Assert.assertNotNull(assigned);
        Assert.assertTrue(assigned.startsWith("urn:uuid:"));
        Assert.assertEquals(assigned, provenance.getTarget().get(2).getReference());
    }

    @Test
    public void provenanceIsAppendedAsLastEntryWithFullUrl() {
        final Bundle bundle = threeEntryBundle();

        final Provenance provenance = generator.generate(bundle, MappingCallContext.empty(), null);

        Assert.assertEquals(4, bundle.getEntry().size());
        final Bundle.BundleEntryComponent last = bundle.getEntry().get(3);
        final Resource lastResource = last.getResource();
        Assert.assertSame(provenance, lastResource);
        Assert.assertTrue(last.getFullUrl().startsWith("urn:uuid:"));
        Assert.assertNotNull(provenance.getRecorded());
        // the Provenance itself must not be among its own targets
        Assert.assertEquals(3, provenance.getTarget().size());
    }

    @Test
    public void defaultAgentIsConfiguredDevice() {
        final Provenance provenance = generator.generate(threeEntryBundle(), MappingCallContext.empty(), null);

        Assert.assertEquals(1, provenance.getAgent().size());
        Assert.assertEquals("Device/openfhir-engine", provenance.getAgentFirstRep().getWho().getReference());
        Assert.assertEquals("openFHIR engine", provenance.getAgentFirstRep().getWho().getDisplay());
        Assert.assertFalse(provenance.getAgentFirstRep().hasOnBehalfOf());
    }

    @Test
    public void whoAndOnBehalfOfOverridesAreApplied() {
        final MappingCallContext context = MappingCallContext.builder()
                .whoRef("Practitioner/doc-1")
                .onBehalfOfRef("Organization/org-1")
                .build();

        final Provenance provenance = generator.generate(threeEntryBundle(), context, null);

        Assert.assertEquals("Practitioner/doc-1", provenance.getAgentFirstRep().getWho().getReference());
        Assert.assertEquals("Organization/org-1", provenance.getAgentFirstRep().getOnBehalfOf().getReference());
    }

    @Test
    public void sourceEntitiesCarryTemplateIdAndEhrId() {
        final MappingCallContext context = MappingCallContext.builder().ehrId("ehr-42").build();

        final Provenance provenance = generator.generate(threeEntryBundle(), context, "Growth chart");

        Assert.assertEquals(2, provenance.getEntity().size());
        Assert.assertEquals(Provenance.ProvenanceEntityRole.SOURCE, provenance.getEntity().get(0).getRole());
        Assert.assertEquals(ProvenanceGenerator.TEMPLATE_ID_IDENTIFIER_SYSTEM,
                provenance.getEntity().get(0).getWhat().getIdentifier().getSystem());
        Assert.assertEquals("Growth chart", provenance.getEntity().get(0).getWhat().getIdentifier().getValue());
        Assert.assertEquals(ProvenanceGenerator.EHR_ID_IDENTIFIER_SYSTEM,
                provenance.getEntity().get(1).getWhat().getIdentifier().getSystem());
        Assert.assertEquals("ehr-42", provenance.getEntity().get(1).getWhat().getIdentifier().getValue());
    }
}
