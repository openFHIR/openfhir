package com.syntaric.openfhir.mapping.ips;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class IpsBidirectionalTest extends GenericTest {

    final String MODEL_MAPPINGS = "/ips/";
    final String CONTEXT_MAPPING = "/ips/ips.context.yml";
    final String HELPER_LOCATION = "/ips/";
    final String OPT = "International Patient Summary.opt";
    final String FLAT_TEXT_VALUE = "ips.flat.json";


    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhirToOpenEhrToFhir() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_TEXT_VALUE), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(compositionFromFlat), operationaltemplate);

        final org.hl7.fhir.r4.model.Composition composition = (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();
        Assert.assertEquals("http://hl7.org/fhir/uv/ips/StructureDefinition/Composition-uv-ips", composition.getMeta().getProfile().get(0).getValueAsString());

        assertProblemList(composition);
        assertAllergies(composition);

        JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, bundle, operationaltemplate);

        final Composition roundTwoCompositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                new Gson().toJson(jsonObject), new OPTParser(operationaltemplate).parse());
        final Bundle roundTwoBundle = toFhir.compositionsToFhir(context, List.of(roundTwoCompositionFromFlat), operationaltemplate);

        final org.hl7.fhir.r4.model.Composition roundTwoComposition = (org.hl7.fhir.r4.model.Composition) roundTwoBundle.getEntryFirstRep().getResource();

        assertProblemList(roundTwoComposition);
        assertAllergies(roundTwoComposition);
    }

    private void assertAllergies(org.hl7.fhir.r4.model.Composition composition) {
        final org.hl7.fhir.r4.model.Composition.SectionComponent section = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "48765-2".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Active Allergies and Intolerances section not found"));
        Assert.assertEquals("Active Allergies and Intolerances", section.getTitle());

        Assert.assertEquals("generated", section.getText().getStatusAsString());
        Assert.assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Hot flushes</div>", section.getText().getDivAsString());

        Assert.assertEquals("No known allergies", section.getEmptyReason().getText());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/list-empty-reason", section.getEmptyReason().getCodingFirstRep().getSystem());
        Assert.assertEquals("nilknown", section.getEmptyReason().getCodingFirstRep().getCode());

        final org.hl7.fhir.r4.model.Coding coding = section.getCode().getCoding().stream()
                .filter(c -> "http://loinc.org".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LOINC coding not found in Active Allergies section"));
        Assert.assertEquals("48765-2", coding.getCode());
        Assert.assertEquals("Allergies and Intolerances", coding.getDisplay());

        final AllergyIntolerance allergy = (AllergyIntolerance) section.getEntry().stream()
                .filter(e -> "#3".equals(e.getReference()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry reference '#3' not found"))
                .getResource();
        Assert.assertEquals("inactive", allergy.getClinicalStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("Inactive", allergy.getClinicalStatus().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical", allergy.getClinicalStatus().getCodingFirstRep().getSystem());
        Assert.assertEquals("confirmed", allergy.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("Confirmed", allergy.getVerificationStatus().getCodingFirstRep().getDisplay());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/allergyintolerance-verification", allergy.getVerificationStatus().getCodingFirstRep().getSystem());
        Assert.assertEquals("42", allergy.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/allergies-intolerances-uv-ips", allergy.getCode().getCodingFirstRep().getSystem());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/allergies-intolerances-uv-ips' available", allergy.getCode().getText());
        Assert.assertEquals("high", allergy.getCriticalityElement().getValueAsString());
//        Assert.assertEquals(AllergyIntolerance.AllergyIntoleranceCategory.FOOD, allergy.getCategory().get(0).getValue());

        Assert.assertEquals("2022-02-03T04:05:06+01:00", allergy.getLastOccurrenceElement().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:07+01:00", allergy.getOnsetDateTimeType().getValueAsString());
        Assert.assertEquals("Allergy", allergy.getTypeElement().getValueAsString());
        Assert.assertEquals("a random text", allergy.getNoteFirstRep().getText());

        AllergyIntolerance.AllergyIntoleranceReactionComponent reactionFirstRep = allergy.getReactionFirstRep();
        Assert.assertEquals("Lorem ipsum Specific Substance", reactionFirstRep.getSubstance().getText());
        Assert.assertEquals("142", reactionFirstRep.getManifestationFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/allergy-reaction-uv-ips", reactionFirstRep.getManifestationFirstRep().getCodingFirstRep().getSystem());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/allergy-reaction-uv-ips' available", reactionFirstRep.getManifestationFirstRep().getText());
        Assert.assertEquals("reaction description", reactionFirstRep.getDescription());
        Assert.assertEquals("2027-02-03T04:05:06+01:00", reactionFirstRep.getOnsetElement().getValueAsString());
        Assert.assertEquals("moderate", reactionFirstRep.getSeverityElement().getValueAsString());
        Assert.assertEquals("Exposure Lorem ipsum", reactionFirstRep.getExposureRoute().getText());
        Assert.assertEquals("Reaction text", reactionFirstRep.getNoteFirstRep().getText());

        jsonParser.encodeResourceToString(allergy);
    }

    private void assertProblemList(org.hl7.fhir.r4.model.Composition composition) {
        final org.hl7.fhir.r4.model.Composition.SectionComponent section = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "11450-4".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Active Problems section not found"));
        Assert.assertEquals("Active Problems", section.getTitle());

        Assert.assertEquals("No information about current problems", section.getEmptyReason().getText());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/list-empty-reason", section.getEmptyReason().getCodingFirstRep().getSystem());
        Assert.assertEquals("unavailable", section.getEmptyReason().getCodingFirstRep().getCode());

        final org.hl7.fhir.r4.model.Coding coding = section.getCode().getCoding().stream()
                .filter(c -> "http://loinc.org".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("LOINC coding not found in Active Problems section"));
        Assert.assertEquals("11450-4", coding.getCode());
        Assert.assertEquals("Problem list Reported", coding.getDisplay());

        Assert.assertEquals("generated", section.getText().getStatusAsString());
        Assert.assertEquals("<div xmlns=\"http://www.w3.org/1999/xhtml\">Hot flushes</div>", section.getText().getDivAsString());

        final Condition condition = (Condition) section.getEntry().stream()
                .filter(e -> "#1".equals(e.getReference()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry reference '#1' not found"))
                .getResource();

        final Condition condition2 = (Condition) section.getEntry().stream()
                .filter(e -> "#2".equals(e.getReference()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Entry reference '#2' not found"))
                .getResource();

        Assert.assertEquals("#1", condition.getId());

        final org.hl7.fhir.r4.model.Coding verificationCoding = condition.getVerificationStatus().getCoding().stream()
                .filter(c -> "http://terminology.hl7.org/CodeSystem/condition-ver-status".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("verificationStatus coding not found"));
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-ver-status", verificationCoding.getSystem());
        Assert.assertEquals("confirmed", verificationCoding.getCode());
        Assert.assertEquals("Confirmed", verificationCoding.getDisplay());

        final List<org.hl7.fhir.r4.model.Coding> clinicalStatuses = condition.getClinicalStatus().getCoding().stream()
                .filter(c -> "http://terminology.hl7.org/CodeSystem/condition-clinical".equals(c.getSystem()))
                .toList();
        Assert.assertEquals("active", clinicalStatuses.get(0).getCode());
        Assert.assertEquals("Active", clinicalStatuses.get(0).getDisplay());

        Assert.assertEquals("resolved", clinicalStatuses.get(1).getCode());
        Assert.assertEquals("Resolved", clinicalStatuses.get(1).getDisplay());

        Assert.assertEquals("remission", clinicalStatuses.get(2).getCode());
        Assert.assertEquals("Remission", clinicalStatuses.get(2).getDisplay());

        Assert.assertEquals("recurrence", clinicalStatuses.get(3).getCode());
        Assert.assertEquals("Recurrence", clinicalStatuses.get(3).getDisplay());

        final org.hl7.fhir.r4.model.Coding severityCoding = condition.getSeverity().getCoding().stream()
                .filter(c -> "local".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("severity coding not found"));
        Assert.assertEquals("at0047", severityCoding.getCode());
        Assert.assertEquals("Mild", severityCoding.getDisplay());
        Assert.assertEquals("Mild", condition.getSeverity().getText());

        final org.hl7.fhir.r4.model.Coding codeCoding = condition.getCode().getCoding().stream()
                .filter(c -> "//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/problems-uv-ips".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("code coding not found"));
        Assert.assertEquals("42", codeCoding.getCode());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/problems-uv-ips' available", codeCoding.getDisplay());
        Assert.assertEquals("No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/problems-uv-ips' available", condition.getCode().getText());

        Assert.assertEquals("2022-02-03T04:05:06+01:00", condition.getOnsetDateTimeType().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", condition.getAbatementDateTimeType().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", condition.getRecordedDateElement().getValueAsString());

        final Condition.ConditionStageComponent stage = condition.getStage().stream()
                .filter(s -> "Lorem ipsum".equals(s.getType().getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("stage not found"));
        Assert.assertEquals("Lorem ipsum", stage.getType().getText());

        condition.getNote().stream()
                .filter(n -> "Lorem ipsum".equals(n.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("note not found"));


        Assert.assertEquals("#2", condition2.getId());

        Assert.assertTrue(condition2.getVerificationStatus().isEmpty());

        final org.hl7.fhir.r4.model.Coding severityCoding2 = condition2.getSeverity().getCoding().stream()
                .filter(c -> "local".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("severity coding not found for condition2"));
        Assert.assertEquals("at0048", severityCoding2.getCode());
        Assert.assertEquals("Moderate", severityCoding2.getDisplay());
        Assert.assertEquals("Moderate", condition2.getSeverity().getText());


        final List<org.hl7.fhir.r4.model.Coding> clinicalStatuses2 = condition2.getClinicalStatus().getCoding().stream()
                .filter(c -> "http://terminology.hl7.org/CodeSystem/condition-clinical".equals(c.getSystem()))
                .toList();
        Assert.assertEquals("inactive", clinicalStatuses2.get(0).getCode());
        Assert.assertEquals("Inactive", clinicalStatuses2.get(0).getDisplay());

        Assert.assertEquals("relapse", clinicalStatuses2.get(1).getCode());
        Assert.assertEquals("Relapse", clinicalStatuses2.get(1).getDisplay());

        final org.hl7.fhir.r4.model.Coding codeCoding2 = condition2.getCode().getCoding().stream()
                .filter(c -> "http://snomed.info/sct".equals(c.getSystem()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("code coding not found for condition2"));
        Assert.assertEquals("161891005", codeCoding2.getCode());
        Assert.assertEquals("Chronic back pain", codeCoding2.getDisplay());
        Assert.assertEquals("Chronic back pain", condition2.getCode().getText());

        Assert.assertEquals("2020-06-15T10:00:00+02:00", condition2.getOnsetDateTimeType().getValueAsString());
        Assert.assertEquals("2024-01-01T00:00:00+01:00", condition2.getAbatementDateTimeType().getValueAsString());

        final Condition.ConditionStageComponent stage2 = condition2.getStage().stream()
                .filter(s -> "MRI confirmed".equals(s.getType().getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("stage not found for condition2"));
        Assert.assertEquals("MRI confirmed", stage2.getType().getText());

        condition2.getNote().stream()
                .filter(n -> "Managed with physiotherapy".equals(n.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("note not found for condition2"));
    }

}
