package com.syntaric.openfhir.mapping.ips;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.mapping.GenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class DiscreteIpsToFhirTest extends GenericTest {

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
    public void discreteToFhir() {
        final String discreteContentItems = getFile(HELPER_LOCATION + "ips.discrete.json");
        final JsonArray arrayOfContentItems = new Gson().fromJson(discreteContentItems, JsonArray.class);
        final List<ContentItem> contentItemList = new ArrayList<>();
        for (final JsonElement composition : arrayOfContentItems) {
            final String serializedComposition = composition.toString();
            contentItemList.add(new CanonicalJson().unmarshal(serializedComposition, ContentItem.class));
        }
        final Bundle bundle = toFhir.contentItemsToFhir(context, contentItemList, operationaltemplate);

        final org.hl7.fhir.r4.model.Composition composition =
                (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();

        final org.hl7.fhir.r4.model.Composition.SectionComponent problemSection = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "11450-4".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Active Problems section not found"));
        Assert.assertEquals(3, problemSection.getEntry().size());

        final List<Condition> conditions = problemSection.getEntry().stream()
                .map(e -> (Condition) e.getResource())
                .toList();
        Assert.assertEquals(3, conditions.size());

        final org.hl7.fhir.r4.model.Composition.SectionComponent allergySection = composition.getSection().stream()
                .filter(s -> s.getCode().getCoding().stream()
                        .anyMatch(c -> "http://loinc.org".equals(c.getSystem()) && "48765-2".equals(c.getCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Allergies and Intolerances section not found"));
        Assert.assertEquals(3, allergySection.getEntry().size());

        final List<AllergyIntolerance> allergies = allergySection.getEntry().stream()
                .map(e -> (AllergyIntolerance) e.getResource())
                .toList();
        Assert.assertEquals(3, allergies.size());

        assertCondition1And3(conditions.get(0));
        assertCondition2(conditions.get(1));
        assertCondition1And3(conditions.get(2));

        assertAllergy1(allergies.get(0));
        assertAllergy2(allergies.get(1));
        assertAllergy3(allergies.get(2));
    }

    private void assertAllergy1(final AllergyIntolerance allergy) {
        Assert.assertEquals("inactive", allergy.getClinicalStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("confirmed", allergy.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("high", allergy.getCriticalityElement().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:07+01:00", allergy.getOnsetDateTimeType().getValueAsString());

        final AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = allergy.getReactionFirstRep();
        Assert.assertEquals("Lorem ipsum Specific Substance", reaction.getSubstance().getText());
        Assert.assertEquals("reaction description", reaction.getDescription());
        Assert.assertEquals("moderate", reaction.getSeverityElement().getValueAsString());
        Assert.assertEquals("Exposure Lorem ipsum", reaction.getExposureRoute().getText());
        Assert.assertEquals("Reaction text", reaction.getNoteFirstRep().getText());
    }

    private void assertAllergy2(final AllergyIntolerance allergy) {
        Assert.assertEquals("active", allergy.getClinicalStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("unconfirmed", allergy.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("low", allergy.getCriticalityElement().getValueAsString());
        Assert.assertEquals("2021-05-10T08:00:01+02:00", allergy.getOnsetDateTimeType().getValueAsString());

        final AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = allergy.getReactionFirstRep();
        Assert.assertEquals("Amoxicillin Substance", reaction.getSubstance().getText());
        Assert.assertEquals("skin rash all over", reaction.getDescription());
        Assert.assertEquals("mild", reaction.getSeverityElement().getValueAsString());
        Assert.assertEquals("Oral route", reaction.getExposureRoute().getText());
        Assert.assertEquals("Mild reaction note", reaction.getNoteFirstRep().getText());
    }

    private void assertAllergy3(final AllergyIntolerance allergy) {
        Assert.assertEquals("active", allergy.getClinicalStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("refuted", allergy.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("low", allergy.getCriticalityElement().getValueAsString());
        Assert.assertEquals("2019-11-20T14:30:01+01:00", allergy.getOnsetDateTimeType().getValueAsString());

        final AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = allergy.getReactionFirstRep();
        Assert.assertEquals("Acetylsalicylic Substance", reaction.getSubstance().getText());
        Assert.assertEquals("severe systemic reaction", reaction.getDescription());
        Assert.assertEquals("severe", reaction.getSeverityElement().getValueAsString());
        Assert.assertEquals("Intravenous route", reaction.getExposureRoute().getText());
        Assert.assertEquals("Severe reaction note", reaction.getNoteFirstRep().getText());
    }

    private void assertCondition1And3(final Condition condition) {
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

        Assert.assertEquals("confirmed", condition.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-ver-status",
                condition.getVerificationStatus().getCodingFirstRep().getSystem());

        Assert.assertEquals("at0047", condition.getSeverity().getCodingFirstRep().getCode());
        Assert.assertEquals("Mild", condition.getSeverity().getCodingFirstRep().getDisplay());
        Assert.assertEquals("Mild", condition.getSeverity().getText());

        Assert.assertEquals("42", condition.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/problems-uv-ips",
                condition.getCode().getCodingFirstRep().getSystem());

        Assert.assertEquals("2022-02-03T04:05:06+01:00", condition.getOnsetDateTimeType().getValueAsString());
        Assert.assertEquals("2022-02-03T04:05:06+01:00", condition.getAbatementDateTimeType().getValueAsString());

        condition.getStage().stream()
                .filter(s -> "Lorem ipsum".equals(s.getType().getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("stage 'Lorem ipsum' not found"));

        condition.getNote().stream()
                .filter(n -> "Lorem ipsum".equals(n.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("note 'Lorem ipsum' not found"));
    }

    private void assertCondition2(final Condition condition) {
        final List<org.hl7.fhir.r4.model.Coding> clinicalStatuses = condition.getClinicalStatus().getCoding().stream()
                .filter(c -> "http://terminology.hl7.org/CodeSystem/condition-clinical".equals(c.getSystem()))
                .toList();
        Assert.assertEquals("inactive", clinicalStatuses.get(0).getCode());
        Assert.assertEquals("Inactive", clinicalStatuses.get(0).getDisplay());
        Assert.assertEquals("relapse", clinicalStatuses.get(1).getCode());
        Assert.assertEquals("Relapse", clinicalStatuses.get(1).getDisplay());

        Assert.assertTrue(condition.getVerificationStatus().isEmpty());

        Assert.assertEquals("at0048", condition.getSeverity().getCodingFirstRep().getCode());
        Assert.assertEquals("Moderate", condition.getSeverity().getCodingFirstRep().getDisplay());
        Assert.assertEquals("Moderate", condition.getSeverity().getText());

        Assert.assertEquals("161891005", condition.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("http://snomed.info/sct", condition.getCode().getCodingFirstRep().getSystem());
        Assert.assertEquals("Chronic back pain", condition.getCode().getText());

        Assert.assertEquals("2020-06-15T10:00:00+02:00", condition.getOnsetDateTimeType().getValueAsString());
        Assert.assertEquals("2024-01-01T00:00:00+01:00", condition.getAbatementDateTimeType().getValueAsString());

        condition.getStage().stream()
                .filter(s -> "MRI confirmed".equals(s.getType().getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("stage 'MRI confirmed' not found"));

        condition.getNote().stream()
                .filter(n -> "Managed with physiotherapy".equals(n.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("note 'Managed with physiotherapy' not found"));
    }
}
