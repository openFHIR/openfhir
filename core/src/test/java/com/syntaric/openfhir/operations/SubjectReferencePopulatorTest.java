package com.syntaric.openfhir.operations;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

public class SubjectReferencePopulatorTest {

    private final PatientResolverInterface noOpResolver = new NoOpPatientResolver();

    private Bundle bundleOf(final org.hl7.fhir.r4.model.Resource... resources) {
        final Bundle bundle = new Bundle();
        for (final org.hl7.fhir.r4.model.Resource resource : resources) {
            bundle.addEntry().setResource(resource);
        }
        return bundle;
    }

    @Test
    public void fillsEmptySubjectFromContextPatient() {
        final Observation observation = new Observation();
        final Condition condition = new Condition();
        final Bundle bundle = bundleOf(observation, condition);

        new SubjectReferencePopulator(noOpResolver).populate(bundle,
                MappingCallContext.builder().patientRef("Patient/ctx-1").build());

        Assert.assertEquals("Patient/ctx-1", observation.getSubject().getReference());
        Assert.assertEquals("Patient/ctx-1", condition.getSubject().getReference());
    }

    @Test
    public void populatedSubjectIsNeverOverwritten() {
        final Observation observation = new Observation();
        observation.setSubject(new Reference("Patient/original"));
        final Bundle bundle = bundleOf(observation);

        new SubjectReferencePopulator(noOpResolver).populate(bundle,
                MappingCallContext.builder().patientRef("Patient/ctx-1").build());

        Assert.assertEquals("Patient/original", observation.getSubject().getReference());
    }

    @Test
    public void onlySubjectAndPatientChildrenAreTouched() {
        final Observation observation = new Observation();
        final Bundle bundle = bundleOf(observation);

        new SubjectReferencePopulator(noOpResolver).populate(bundle,
                MappingCallContext.builder().patientRef("Patient/ctx-1").build());

        Assert.assertEquals("Patient/ctx-1", observation.getSubject().getReference());
        Assert.assertTrue(observation.getPerformer().isEmpty());
        Assert.assertTrue(observation.getFocus().isEmpty());
    }

    @Test
    public void resourcesWithoutSubjectOrPatientChildAreSkipped() {
        final Organization organization = new Organization();
        organization.setName("org");
        final Bundle bundle = bundleOf(organization);

        new SubjectReferencePopulator(noOpResolver).populate(bundle,
                MappingCallContext.builder().patientRef("Patient/ctx-1").build());

        // nothing to assert on a missing property other than it not blowing up and staying absent
        Assert.assertFalse(organization.hasExtension());
        Assert.assertEquals("org", organization.getName());
    }

    @Test
    public void contextPatientTakesPrecedenceOverResolver() {
        final Observation observation = new Observation();
        final Bundle bundle = bundleOf(observation);
        final PatientResolverInterface resolver = ehrId -> Optional.of("Patient/resolved");

        new SubjectReferencePopulator(resolver).populate(bundle,
                MappingCallContext.builder().patientRef("Patient/ctx-1").ehrId("ehr-1").build());

        Assert.assertEquals("Patient/ctx-1", observation.getSubject().getReference());
    }

    @Test
    public void resolverIsUsedWhenOnlyEhrIdPresent() {
        final Observation observation = new Observation();
        final MedicationRequest medicationRequest = new MedicationRequest();
        final Bundle bundle = bundleOf(observation, medicationRequest);
        final PatientResolverInterface resolver =
                ehrId -> "ehr-1".equals(ehrId) ? Optional.of("Patient/resolved") : Optional.empty();

        new SubjectReferencePopulator(resolver).populate(bundle,
                MappingCallContext.builder().ehrId("ehr-1").build());

        Assert.assertEquals("Patient/resolved", observation.getSubject().getReference());
        Assert.assertEquals("Patient/resolved", medicationRequest.getSubject().getReference());
    }

    @Test
    public void bundleStaysUntouchedWithoutAnyPatientSource() {
        final Observation observation = new Observation();
        final Bundle bundle = bundleOf(observation);

        new SubjectReferencePopulator(noOpResolver).populate(bundle, MappingCallContext.empty());
        new SubjectReferencePopulator(noOpResolver).populate(bundle,
                MappingCallContext.builder().ehrId("unresolvable").build());

        Assert.assertFalse(observation.hasSubject());
    }
}
