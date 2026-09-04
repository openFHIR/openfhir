package com.syntaric.openfhir.mapping.operations;

import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import com.syntaric.openfhir.operations.MappingCallContext;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import com.syntaric.openfhir.operations.NoOpPatientResolver;
import com.syntaric.openfhir.operations.ProvenanceGenerator;
import com.syntaric.openfhir.operations.SubjectReferencePopulator;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Provenance;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Integration test of the {@code $tofhir}/{@code $toopenehr} context-semantics passes over a real mapped
 * bundle (blood pressure fixtures): Provenance generation, subject population and mapping-issue collection.
 */
public class FhirOperationsIntegrationTest extends GenericTest {

    final String MODEL_MAPPINGS = "/blood_pressure/";
    final String CONTEXT_MAPPING = "/blood_pressure/simple-blood-pressure.context.yml";
    final String HELPER_LOCATION = "/blood_pressure/";
    final String OPT = "Blood Pressure.opt";
    final String FLAT = "blood-pressure_flat.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    private Bundle mapBloodPressureToFhir() {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                webTemplate);
        return (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
    }

    @Test
    public void provenanceCoversEveryMappedEntry() {
        final Bundle bundle = mapBloodPressureToFhir();
        final int mappedEntries = bundle.getEntry().size();

        final MappingCallContext callContext = MappingCallContext.builder()
                .ehrId("ehr-1")
                .whoRef("Practitioner/mapper-1")
                .onBehalfOfRef("Organization/hospital-1")
                .build();
        final Provenance provenance = new ProvenanceGenerator("Device/openfhir-engine", "openFHIR engine")
                .generate(bundle, callContext, "Blood Pressure");

        Assert.assertEquals(mappedEntries + 1, bundle.getEntry().size());
        Assert.assertSame(provenance, bundle.getEntry().get(bundle.getEntry().size() - 1).getResource());
        Assert.assertEquals(mappedEntries, provenance.getTarget().size());

        // every target must be a resolvable, non-blank reference and unique
        final Set<String> targets = new HashSet<>();
        provenance.getTarget().forEach(t -> {
            Assert.assertNotNull(t.getReference());
            targets.add(t.getReference());
        });
        Assert.assertEquals(mappedEntries, targets.size());

        Assert.assertEquals("Practitioner/mapper-1", provenance.getAgentFirstRep().getWho().getReference());
        Assert.assertEquals("Organization/hospital-1", provenance.getAgentFirstRep().getOnBehalfOf().getReference());
        Assert.assertEquals("Blood Pressure", provenance.getEntity().get(0).getWhat().getIdentifier().getValue());
        Assert.assertEquals("ehr-1", provenance.getEntity().get(1).getWhat().getIdentifier().getValue());
    }

    @Test
    public void subjectPopulationFillsMappedObservations() {
        final Bundle bundle = mapBloodPressureToFhir();
        final Observation observation = (Observation) bundle.getEntry().get(0).getResource();
        Assert.assertFalse("fixture precondition: mapped Observation has no subject reference",
                observation.getSubject().hasReference());

        new SubjectReferencePopulator(new NoOpPatientResolver()).populate(bundle,
                MappingCallContext.builder().patientRef("Patient/p-1").build());

        Assert.assertEquals("Patient/p-1", observation.getSubject().getReference());
    }

    @Test
    public void toOpenEhrReportsWhenNothingCouldBeMapped() {
        // a Bundle without any Observation the blood-pressure mappers could start from
        final Bundle unmappable = new Bundle();
        final Patient patient = new Patient();
        patient.setId("p-1");
        unmappable.addEntry().setResource(patient);

        final MappingIssueCollector collector = new MappingIssueCollector();
        toOpenEhr.fhirToFlatJsonObject(context, unmappable, webTemplate, collector);

        Assert.assertFalse("gaps must be reported, not silently dropped", collector.isEmpty());
        Assert.assertEquals(MappingIssueCollector.SEVERITY_WARNING, collector.getIssues().get(0).severity());
        Assert.assertEquals(MappingIssueCollector.CODE_INCOMPLETE, collector.getIssues().get(0).code());
    }

    @Test
    public void toOpenEhrCollectsNothingOnCleanMapping() {
        final Bundle bundle = mapBloodPressureToFhir();

        final MappingIssueCollector collector = new MappingIssueCollector();
        final Composition composition = toOpenEhr.fhirToCompositionRm(context, bundle, webTemplate, collector);

        Assert.assertFalse(composition.getContent().isEmpty());
    }
}
