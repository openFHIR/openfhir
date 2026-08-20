package com.syntaric.openfhir.mapping.eps;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import com.syntaric.openfhir.mapping.OptCompositionValidator;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhrPrePostProcessor;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.DeviceUseStatement;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;
import org.junit.Assert;
import org.junit.Test;

public class EpsPatientSummaryBidirectionalTest extends GenericTest {

    static final String RESOURCES = "/eps/";
    static final String CONTEXT_MAPPING = RESOURCES + "eps-patient-summary.context.yml";
    static final String OPT = "EPS Patient Summary.opt";
    static final String FLAT = "eps.example.flat.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(RESOURCES + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(RESOURCES).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhirToOpenEhrToFhir() {
        // Round 1: openEHR flat → FHIR
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(
                getFlat(RESOURCES + FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);

        assertAllergiesSection(bundle);
        assertProblemsSection(bundle);
        assertProceduresSection(bundle);
        assertMedicalDevicesSection(bundle);

        // Round 2: FHIR → openEHR flat → FHIR
        final JsonObject flatJson = toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate);

        // assert narratives
        Assert.assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Allergies and Intolerances</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Substance</th><th>Clinical Status</th><th>Verification Status</th><th>Type</th><th>Category</th><th>Criticality</th><th>Reactions</th><th>Notes</th></tr></thead><tbody><tr>  <!-- Substance (code): prefer text, fall back to first coding display --><td><span>Caries prophylactic agents</span></td>  <!-- Clinical status --><td><span>active</span><br/></td>  <!-- Verification status --><td><span>unconfirmed</span><br/></td>  <!-- Type (allergy | intolerance) --><td></td>  <!-- Category (food | medication | environment | biologic) --><td><span>food</span><br/></td>  <!-- Criticality --><td></td>  <!-- Reactions: substance + manifestations + severity --><td><span>Penicillin V</span>: <span>Bronchospasm</span><br/></td>  <!-- Notes --><td><span>a comment</span><br/></td></tr></tbody></table></div>", flatJson.get("eps_patient_summary/eps_allergies/container/fhir_narrative/narrative").getAsString());
        Assert.assertEquals("generated", flatJson.get("eps_patient_summary/eps_allergies/container/fhir_narrative/status").getAsString());

        Assert.assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Problem List</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Condition</th><th>Clinical Status</th><th>Verification Status</th><th>Severity</th><th>Onset</th><th>Abatement</th><th>Notes</th></tr></thead><tbody><tr>  <!-- Condition code: prefer text, fall back to first coding display --><td><span>Clinical finding (finding)</span></td>  <!-- Clinical status: first coding code --><td><span>active</span><br/><span>resolved</span><br/><span>remission</span><br/><span>recurrence</span><br/></td>  <!-- Verification status: first coding code --><td><span>unconfirmed</span><br/></td>  <!-- Severity: first coding display --><td><span>Mild</span><br/></td>  <!-- Onset --><td><span>2022-02-03T04:05:06</span></td>  <!-- Abatement --><td><span>2022-02-03T04:05:06</span></td>  <!-- Notes --><td><span>Lorem ipsum</span><br/></td></tr></tbody></table></div>", flatJson.get("eps_patient_summary/eps_problems/container/fhir_narrative/narrative").getAsString());
        Assert.assertEquals("generated", flatJson.get("eps_patient_summary/eps_problems/container/fhir_narrative/status").getAsString());

        Assert.assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Procedures</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Procedure</th><th>Status</th><th>Body Site</th><th>Performed</th><th>Outcome</th><th>Notes</th></tr></thead><tbody><tr>  <!-- Procedure code: prefer text, fall back to first coding display --><td><span>Appendectomy</span></td>  <!-- Status --><td></td>  <!-- Body site --><td><span>McBurney point area</span></td>  <!-- Performed --><td><span>2021-06-15T08:30:00</span></td>  <!-- Outcome: prefer text, fall back to codings --><td><span>Procedure successful</span></td>  <!-- Notes --><td><span>Patient tolerated procedure well</span><br/></td></tr></tbody></table></div>", flatJson.get("eps_patient_summary/eps_history_of_procedures/container/fhir_narrative/narrative").getAsString());
        Assert.assertEquals("generated", flatJson.get("eps_patient_summary/eps_history_of_procedures/container/fhir_narrative/status").getAsString());

        Assert.assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Medical Devices</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Device</th><th>Status</th><th>Body Site</th><th>Timing</th><th>Notes</th></tr></thead><tbody><tr>  <!-- Device: resolve reference and prefer deviceName, fall back to type codings --><td><span>Ceramic hip implant</span></td>  <!-- Status --><td></td>  <!-- Body site --><td><span>Left hip</span></td>  <!-- Timing --><td><span></span></td>  <!-- Notes --><td></td></tr></tbody></table></div>", flatJson.get("eps_patient_summary/eps_medical_devices/container/fhir_narrative/narrative").getAsString());
        Assert.assertEquals("generated", flatJson.get("eps_patient_summary/eps_medical_devices/container/fhir_narrative/status").getAsString());


        final Composition roundTwoComposition = new FlatJsonUnmarshaller().unmarshal(
                new Gson().toJson(flatJson), new OPTParser(operationaltemplate).parse());
        final Bundle roundTwoBundle = (Bundle) toFhir.compositionsToFhir(context, List.of(roundTwoComposition), webTemplate);

        new ToOpenEhrPrePostProcessor(new FhirContextRegistry()).postProcess(roundTwoComposition);

        OptCompositionValidator.assertValid(operationaltemplate, composition);
        OptCompositionValidator.assertValid(operationaltemplate, roundTwoComposition);

        assertAllergiesSection(roundTwoBundle);
        assertProblemsSection(roundTwoBundle);
        assertProceduresSection(roundTwoBundle);
        assertMedicalDevicesSection(roundTwoBundle);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Resource getBundleResource(final Bundle bundle, final String reference) {
        return bundle.getEntry().stream()
                .filter(e -> e.getFullUrl() != null && e.getFullUrl().contains(reference))
                .map(Bundle.BundleEntryComponent::getResource)
                .findFirst()
                .orElse(null);
    }

    private void assertAllergiesSection(final Bundle bundle) {
        final org.hl7.fhir.r4.model.Composition composition =
                (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();

        final org.hl7.fhir.r4.model.Composition.SectionComponent section = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "48765-2".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allergies and Intolerances section not found"));

        Assert.assertEquals("generated", section.getText().getStatusAsString());
        Assert.assertTrue(section.getText().getDivAsString().startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Allergies and Intolerances</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Substance</th><th>Clinical Status</th><th>Verification Status</th><th>Type</th><th>Category</th><th>Criticality</th><th>Reactions</th><th>Notes</th></tr></thead><tbody><tr>  <!-- Substance (code): prefer text, fall back to first coding display --><td><span>Caries prophylactic agents"));


        // Section metadata
        Assert.assertEquals("Allergies and Intolerances", section.getTitle());

        final Coding sectionCode = section.getCode().getCoding().stream()
                .filter(c -> "http://loinc.org".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LOINC coding not found in Allergies section"));
        Assert.assertEquals("48765-2", sectionCode.getCode());
        Assert.assertEquals("Allergies and adverse reactions Document", sectionCode.getDisplay());

        // emptyReason — nilknown from exclusion_global ("No known allergies")
        Assert.assertEquals("No known allergies", section.getEmptyReason().getText());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/list-empty-reason",
                section.getEmptyReason().getCodingFirstRep().getSystem());
        Assert.assertEquals("nilknown", section.getEmptyReason().getCodingFirstRep().getCode());

        // AllergyIntolerance entry
        Assert.assertFalse("Expected at least one entry in the allergies section", section.getEntry().isEmpty());
        final AllergyIntolerance allergy =
                (AllergyIntolerance) getBundleResource(bundle, section.getEntryFirstRep().getReference());
        Assert.assertNotNull("AllergyIntolerance resource not found in bundle", allergy);

        // substance (agentOrAllergen) — code + text
        final Coding substanceCoding = allergy.getCode().getCodingFirstRep();
        Assert.assertEquals("https://termgit.elga.gv.at/CodeSystem/atc-deutsch-wido", substanceCoding.getSystem());
        Assert.assertEquals("A01AA", substanceCoding.getCode());
        Assert.assertEquals("Caries prophylactic agents", substanceCoding.getDisplay());
        Assert.assertEquals("Caries prophylactic agents", allergy.getCode().getText());

        // verificationStatus (certainty) — at0064 = Unconfirmed
        Assert.assertEquals("unconfirmed", allergy.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("Unconfirmed", allergy.getVerificationStatus().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification",
                allergy.getVerificationStatus().getCodingFirstRep().getSystem());

        // clinicalStatus
        Assert.assertEquals("active", allergy.getClinicalStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("Active", allergy.getClinicalStatus().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
                allergy.getClinicalStatus().getCodingFirstRep().getSystem());

        // typeOfPropensity (reaction mechanism) — "Allergy" → allergy
        Assert.assertEquals("allergy", allergy.getTypeElement().getValueAsString());

        // criticality — "low"
        Assert.assertEquals("low", allergy.getCriticalityElement().getValueAsString());

        // onsetDate
        Assert.assertEquals("2022-02-03T04:05:06", allergy.getOnsetDateTimeType().getValueAsString());

        // description / comment
        Assert.assertEquals("a comment", allergy.getNoteFirstRep().getText());

        // reaction — manifestation, onset, severity, substance, description, exposureRoute, note
        Assert.assertFalse("Expected at least one reaction", allergy.getReaction().isEmpty());
        final AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = allergy.getReactionFirstRep();
        Assert.assertEquals("Bronchospasm", reaction.getManifestationFirstRep().getText());
        Assert.assertEquals("4386001", reaction.getManifestationFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("Bronchospasm", reaction.getManifestationFirstRep().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://www.snomed.org", reaction.getManifestationFirstRep().getCodingFirstRep().getSystem());
        Assert.assertEquals("2022-02-03T04:05:06", reaction.getOnsetElement().getValueAsString());
        Assert.assertEquals("mild", reaction.getSeverityElement().getValueAsString());
        Assert.assertEquals("Penicillin V", reaction.getSubstance().getText());
        Assert.assertEquals("Generalised urticaria within 30 minutes", reaction.getDescription());
        Assert.assertEquals("Oral", reaction.getExposureRoute().getText());
        Assert.assertEquals("Reaction occurred after first dose", reaction.getNoteFirstRep().getText());

        jsonParser.encodeResourceToString(allergy);
    }

    private void assertProblemsSection(final Bundle bundle) {
        final org.hl7.fhir.r4.model.Composition composition =
                (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();

        final org.hl7.fhir.r4.model.Composition.SectionComponent section = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "11450-4".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Problems section not found"));

        Assert.assertEquals("generated", section.getText().getStatusAsString());
        Assert.assertTrue(section.getText().getDivAsString().startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Problem List</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Condition</th><th>Clinical Status</th><th>Verification Status</th><th>Severity</th><th>Onset</th><th>Abatement</th><th>Notes</th></tr></thead><tbody><tr>"));

        // Section metadata
        Assert.assertEquals("Problems", section.getTitle());

        final Coding sectionCode = section.getCode().getCoding().stream()
                .filter(c -> "http://loinc.org".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LOINC coding not found in Problems section"));
        Assert.assertEquals("11450-4", sectionCode.getCode());
        Assert.assertEquals("Problem list Reported", sectionCode.getDisplay());

        // emptyReason — nilknown from exclusion_global ("No known problems")
        Assert.assertEquals("No known problems", section.getEmptyReason().getText());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/list-empty-reason",
                section.getEmptyReason().getCodingFirstRep().getSystem());
        Assert.assertEquals("nilknown", section.getEmptyReason().getCodingFirstRep().getCode());

        // Condition entry
        Assert.assertFalse("Expected at least one entry in the problems section", section.getEntry().isEmpty());
        final Condition condition =
                (Condition) getBundleResource(bundle, section.getEntryFirstRep().getReference());
        Assert.assertNotNull("Condition resource not found in bundle", condition);

        // problem name (code)
        Assert.assertEquals("Clinical finding (finding)", condition.getCode().getText());
        Assert.assertEquals("404684003", condition.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("http://www.snomed.org", condition.getCode().getCodingFirstRep().getSystem());
        Assert.assertEquals("Clinical finding (finding)", condition.getCode().getCodingFirstRep().getDisplay());

        // onsetDate
        Assert.assertEquals("2022-02-03T04:05:06", condition.getOnsetDateTimeType().getValueAsString());

        // endDate (abatement)
        Assert.assertEquals("2022-02-03T04:05:06", condition.getAbatementDateTimeType().getValueAsString());

        // severity
        final Coding severityCoding = condition.getSeverity().getCodingFirstRep();
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-severity", severityCoding.getSystem());
        Assert.assertEquals("mild", severityCoding.getCode());
        Assert.assertEquals("Mild", severityCoding.getDisplay());

        // diagnosisAssertionStatus → verificationStatus = unconfirmed (at0074 = Suspected)
        Assert.assertEquals("unconfirmed", condition.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("Unconfirmed", condition.getVerificationStatus().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-ver-status",
                condition.getVerificationStatus().getCodingFirstRep().getSystem());

        // bodySite — at0012 SNOMED coded
        Assert.assertFalse("Expected at least one body site", condition.getBodySite().isEmpty());
        final Coding bodySiteCoding = condition.getBodySiteFirstRep().getCodingFirstRep();
        Assert.assertEquals("http://www.snomed.org", bodySiteCoding.getSystem());
        Assert.assertEquals("53075003", bodySiteCoding.getCode());
        Assert.assertEquals("Distal phalanx of hallux", bodySiteCoding.getDisplay());

        // stage — summary from clinical_evidence at0005 (stage:0/evidence)
        Assert.assertFalse("Expected at least one stage", condition.getStage().isEmpty());
        Assert.assertEquals("stage result other", condition.getStageFirstRep().getSummary().getText());
        Assert.assertEquals("stage evidence", condition.getStageFirstRep().getType().getText());

        // evidence — code from clinical_evidence at0005 (clinical_evidence:0/result)
        Assert.assertFalse("Expected at least one evidence", condition.getEvidence().isEmpty());
        Assert.assertEquals("clinical evidence result other", condition.getEvidenceFirstRep().getCodeFirstRep().getText());

        // clinicalStatus — active (at0026) + resolved (at0084) + remission (at0090) + recurrence (at0096)
        final List<String> clinicalCodes = condition.getClinicalStatus().getCoding().stream()
                .map(Coding::getCode)
                .collect(java.util.stream.Collectors.toList());
        Assert.assertTrue("Expected active clinical status", clinicalCodes.contains("active"));
        Assert.assertTrue("Expected resolved clinical status", clinicalCodes.contains("resolved"));
        Assert.assertTrue("Expected remission clinical status", clinicalCodes.contains("remission"));
        Assert.assertTrue("Expected recurrence clinical status", clinicalCodes.contains("recurrence"));

        jsonParser.encodeResourceToString(condition);
    }

    private void assertProceduresSection(final Bundle bundle) {
        final org.hl7.fhir.r4.model.Composition composition =
                (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();

        final org.hl7.fhir.r4.model.Composition.SectionComponent section = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "47519-4".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Procedures section not found"));

        Assert.assertEquals("generated", section.getText().getStatusAsString());
        Assert.assertTrue(section.getText().getDivAsString().startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Procedures</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Procedure</th><th>Status</th><th>Body Site</th><th>Pe"));


        // Section metadata
        Assert.assertEquals("Procedures", section.getTitle());

        final Coding sectionCode = section.getCode().getCoding().stream()
                .filter(c -> "http://loinc.org".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LOINC coding not found in Procedures section"));
        Assert.assertEquals("47519-4", sectionCode.getCode());
        Assert.assertEquals("History of Procedures Document", sectionCode.getDisplay());

        // emptyReason — nilknown from exclusion_global ("No known procedures")
        Assert.assertEquals("No known procedures", section.getEmptyReason().getText());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/list-empty-reason",
                section.getEmptyReason().getCodingFirstRep().getSystem());
        Assert.assertEquals("nilknown", section.getEmptyReason().getCodingFirstRep().getCode());

        // Procedure entry
        Assert.assertFalse("Expected at least one entry in the procedures section", section.getEntry().isEmpty());
        final Procedure procedure =
                (Procedure) getBundleResource(bundle, section.getEntryFirstRep().getReference());
        Assert.assertNotNull("Procedure resource not found in bundle", procedure);

        Assert.assertEquals("stopped", procedure.getStatusElement().getCode());

        // code (procedure name)
        Assert.assertEquals("Appendectomy", procedure.getCode().getText());

        // performed date/time (action time)
        Assert.assertEquals("2021-06-15T08:30:00", procedure.getPerformedDateTimeType().getValueAsString());

        // reason
        Assert.assertFalse("Expected at least one reason", procedure.getReasonCode().isEmpty());
        Assert.assertEquals("Acute appendicitis", procedure.getReasonCodeFirstRep().getText());

        // outcome
        Assert.assertEquals("Procedure successful", procedure.getOutcome().getText());

        // complication
        Assert.assertFalse("Expected at least one complication", procedure.getComplication().isEmpty());
        Assert.assertEquals("Minor bleeding", procedure.getComplicationFirstRep().getText());

        // note
        Assert.assertEquals("Patient tolerated procedure well", procedure.getNoteFirstRep().getText());

        // bodySite
        Assert.assertFalse("Expected at least one body site", procedure.getBodySite().isEmpty());
        Assert.assertEquals("McBurney point area", procedure.getBodySiteFirstRep().getText());

        // usedReference → Device
        Assert.assertFalse("Expected at least one usedReference", procedure.getUsedReference().isEmpty());
        final Device device =
                (Device) getBundleResource(bundle, procedure.getUsedReferenceFirstRep().getReference());
        Assert.assertNotNull("Device resource not found in bundle", device);

        Assert.assertEquals("used device name", device.getDeviceNameFirstRep().getName());
        Assert.assertEquals("use code device type", device.getType().getText());

        Assert.assertEquals("use code device type", procedure.getUsedCodeFirstRep().getText());

        // focal → Device
        Assert.assertFalse("Expected at least one focalDevice", procedure.getFocalDevice().isEmpty());
        final Device focalDevice =
                (Device) getBundleResource(bundle, procedure.getFocalDeviceFirstRep().getManipulated().getReference());
        Assert.assertNotNull("Device resource not found in bundle", focalDevice);

        Assert.assertEquals("focal device name", focalDevice.getDeviceNameFirstRep().getName());
        jsonParser.encodeResourceToString(procedure);
    }

    private void assertMedicalDevicesSection(final Bundle bundle) {
        final org.hl7.fhir.r4.model.Composition composition =
                (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();

        final org.hl7.fhir.r4.model.Composition.SectionComponent section = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "46264-8".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Medical Devices section not found"));

        Assert.assertEquals("generated", section.getText().getStatusAsString());
        Assert.assertTrue(section.getText().getDivAsString().startsWith("<div xmlns=\"http://www.w3.org/1999/xhtml\"><h5>Medical Devices</h5><table class=\"hapiPropertyTable\"><thead><tr><th>Device</th><th>Status</th><th>Body S"));


        // Section metadata
        Assert.assertEquals("Medical Devices and Implants", section.getTitle());

        final Coding sectionCode = section.getCode().getCoding().stream()
                .filter(c -> "http://loinc.org".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LOINC coding not found in Medical Devices section"));
        Assert.assertEquals("46264-8", sectionCode.getCode());
        Assert.assertEquals("History of medical device use", sectionCode.getDisplay());

        // emptyReason — absence only (unavailable), no exclusion_global for devices
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/list-empty-reason",
                section.getEmptyReason().getCodingFirstRep().getSystem());
        Assert.assertEquals("unavailable", section.getEmptyReason().getCodingFirstRep().getCode());

        // DeviceUseStatement entry
        Assert.assertFalse("Expected at least one entry in the medical devices section", section.getEntry().isEmpty());
        final DeviceUseStatement dus =
                (DeviceUseStatement) getBundleResource(bundle, section.getEntryFirstRep().getReference());
        Assert.assertNotNull("DeviceUseStatement resource not found in bundle", dus);

        // status — hardcoded active
        Assert.assertEquals("active", dus.getStatusElement().getCode());

        // timing period
        Assert.assertEquals("2019-03-10T09:00:00", dus.getTimingPeriod().getStartElement().getValueAsString());
        Assert.assertEquals("2023-02-03T04:05:06", dus.getTimingPeriod().getEndElement().getValueAsString());

        // bodySite
        Assert.assertEquals("Left hip", dus.getBodySite().getText());

        // Device (via reference)
        final Device device = (Device) getBundleResource(bundle, dus.getDevice().getReference());
        Assert.assertNotNull("Device resource not found in bundle", device);

        Assert.assertEquals("Ceramic hip implant", device.getDeviceNameFirstRep().getName());
        Assert.assertEquals("Orthopaedic implant", device.getType().getText());
        Assert.assertEquals("Zimmer Biomet", device.getManufacturer());
        Assert.assertEquals("2025-02-03T04:05:06", device.getManufactureDateElement().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06", device.getExpirationDateElement().getValueAsString());
        Assert.assertEquals("LOT-HIP-2018", device.getLotNumber());
        Assert.assertEquals("HIP-SN-987654", device.getSerialNumber());
        Assert.assertEquals("Continuum-AC", device.getModelNumber());
        Assert.assertEquals("N/A", device.getVersionFirstRep().getValue());
        Assert.assertEquals("Implant functioning well", device.getNoteFirstRep().getText());
        Assert.assertEquals("HIP-OTHER-002", device.getIdentifierFirstRep().getValue());
        Assert.assertEquals("UDI-HIP-0987654321", device.getUdiCarrierFirstRep().getDeviceIdentifier());
        Assert.assertEquals("DISTINCT", device.getDistinctIdentifier());

        jsonParser.encodeResourceToString(dus);
    }
}
