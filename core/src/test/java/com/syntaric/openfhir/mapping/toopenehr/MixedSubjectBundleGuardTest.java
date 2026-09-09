package com.syntaric.openfhir.mapping.toopenehr;

import static org.assertj.core.api.Assertions.assertThat;

import com.syntaric.openfhir.operations.MappingIssueCollector;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

/**
 * The guard warns — and only warns — when a Bundle's entries refer to more than one subject, because the
 * whole Bundle folds into a single Composition committed to a single EHR. Anything single-subject stays
 * silent, whatever notation the references use.
 */
class MixedSubjectBundleGuardTest {

    @Test
    void twoDistinctSubjects_warnsNamingBoth() {
        final Bundle bundle = bundleOf(
                observationWithSubject(new Reference("Patient/p1")),
                observationWithSubject(new Reference("Patient/p2")));

        final MappingIssueCollector collector = check(bundle);

        assertThat(collector.getIssues()).hasSize(1);
        final MappingIssueCollector.MappingIssue issue = collector.getIssues().get(0);
        assertThat(issue.severity()).isEqualTo(MappingIssueCollector.SEVERITY_WARNING);
        assertThat(issue.code()).isEqualTo(MixedSubjectBundleGuard.CODE_MULTIPLE_MATCHES);
        assertThat(issue.diagnostics())
                .contains("Patient/p1")
                .contains("Patient/p2")
                .contains("Partition the Bundle per subject");
    }

    @Test
    void singleSubjectAndOnePatientEntry_isClean() {
        final Patient patient = new Patient();
        patient.setId("Patient/p1");
        final Bundle bundle = bundleOf(
                patient,
                observationWithSubject(new Reference("Patient/p1")),
                observationWithSubject(new Reference("Patient/p1")));

        assertThat(check(bundle).isEmpty()).isTrue();
    }

    @Test
    void entriesWithoutSubjectAndOnePatientEntry_isClean() {
        final Patient patient = new Patient();
        patient.setId("Patient/p1");
        final Bundle bundle = bundleOf(patient, new Observation(), new Observation());

        assertThat(check(bundle).isEmpty()).isTrue();
    }

    @Test
    void twoPatientEntries_warnsEvenWithoutAnySubjectReferences() {
        final Patient first = new Patient();
        first.setId("Patient/p1");
        final Patient second = new Patient();
        second.setId("Patient/p2");
        final Bundle bundle = bundleOf(first, second, new Observation());

        final MappingIssueCollector collector = check(bundle);

        assertThat(collector.getIssues()).hasSize(1);
        assertThat(collector.getIssues().get(0).diagnostics())
                .contains("2 Patient entries")
                .contains("Patient/p1")
                .contains("Patient/p2");
    }

    @Test
    void identifierOnlyReferences_sameIdentityIsClean() {
        final Bundle bundle = bundleOf(
                observationWithSubject(identifierOnly("http://hospital.example/mrn", "12345")),
                observationWithSubject(identifierOnly("http://hospital.example/mrn", "12345")));

        assertThat(check(bundle).isEmpty()).isTrue();
    }

    @Test
    void identifierOnlyReferences_differentIdentityWarns() {
        final Bundle bundle = bundleOf(
                observationWithSubject(identifierOnly("http://hospital.example/mrn", "12345")),
                observationWithSubject(identifierOnly("http://hospital.example/mrn", "67890")));

        final MappingIssueCollector collector = check(bundle);

        assertThat(collector.getIssues()).hasSize(1);
        assertThat(collector.getIssues().get(0).diagnostics())
                .contains("http://hospital.example/mrn|12345")
                .contains("http://hospital.example/mrn|67890");
    }

    /** The same subject written absolute, relative or with a leading '#' counts once, not thrice. */
    @Test
    void absoluteRelativeAndHashReferences_normalizeToTheSameSubject() {
        final Bundle bundle = bundleOf(
                observationWithSubject(new Reference("https://fhir.example.org/base/Patient/p1")),
                observationWithSubject(new Reference("Patient/p1")),
                observationWithSubject(new Reference("#Patient/p1")));

        assertThat(check(bundle).isEmpty()).isTrue();
    }

    @Test
    void displayOnlyReferences_haveNoIdentityAndStayClean() {
        final Reference displayOnly = new Reference();
        displayOnly.setDisplay("Jane Doe");
        final Bundle bundle = bundleOf(
                observationWithSubject(displayOnly),
                observationWithSubject(new Reference("Patient/p1")));

        assertThat(check(bundle).isEmpty()).isTrue();
    }

    @Test
    void searchsetBundle_getsTheSplitTheSearchsetHint() {
        final Bundle bundle = bundleOf(
                observationWithSubject(new Reference("Patient/p1")),
                observationWithSubject(new Reference("Patient/p2")));
        bundle.setType(Bundle.BundleType.SEARCHSET);

        final MappingIssueCollector collector = check(bundle);

        assertThat(collector.getIssues()).hasSize(1);
        assertThat(collector.getIssues().get(0).diagnostics()).contains("searchset");
    }

    @Test
    void nonSearchsetBundle_getsNoSearchsetHint() {
        final Bundle bundle = bundleOf(
                observationWithSubject(new Reference("Patient/p1")),
                observationWithSubject(new Reference("Patient/p2")));
        bundle.setType(Bundle.BundleType.COLLECTION);

        assertThat(check(bundle).getIssues().get(0).diagnostics()).doesNotContain("searchset");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private MappingIssueCollector check(final Bundle bundle) {
        final MappingIssueCollector collector = new MappingIssueCollector();
        MixedSubjectBundleGuard.check(bundle, collector);
        return collector;
    }

    private Bundle bundleOf(final Resource... resources) {
        final Bundle bundle = new Bundle();
        for (final Resource resource : resources) {
            bundle.addEntry().setResource(resource);
        }
        return bundle;
    }

    private Observation observationWithSubject(final Reference subject) {
        final Observation observation = new Observation();
        observation.setSubject(subject);
        return observation;
    }

    private Reference identifierOnly(final String system, final String value) {
        return new Reference().setIdentifier(new Identifier().setSystem(system).setValue(value));
    }
}
