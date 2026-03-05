package com.syntaric.openfhir.mapping.kds.diagnose;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Extension;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

@Ignore("discuss because based on the fhir connect mappings, Condition would have referenced Condition itself on most examples")
public class DiagnoseToOpenEHRTest extends KdsGenericTest {


    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING = "/kds/core/projects/org.highmed/KDS/diagnose/KDS_diagnose.context.yaml";
    final String OPT = "/kds/diagnose/KDS_Diagnose.opt";
    final String BUNDLE = "/kds/diagnose/toOpenEHR/input/KDS_Diagnose_bundle_whole.json";
    final String BUNDLE_SINGLE = "/kds/diagnose/toOpenEHR/input/KDS_Diagnose_bundle.json";
    final String[] FHIR_CONDITIONS = {
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-1-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-2-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-3-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-4-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-5-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-6-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-7-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-8-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-9-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-10-diagnose-1.json"
    };

    final String FLAT = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition.flat.json";
    final String COMPOSITION_SINGLE = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition_bundle.json";
    final String COMPOSITION_MULTIPLE = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition_bundle_whole.json";
    final String FLAT_MULTIPLE = "/kds/diagnose/toOpenEHR/output/KDS_Diagnose_multiple_Composition.flat.json"; // todo change to multiple
    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-diagnose-1.json",
            "/kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-diagnose-1.json"
    };


    @SneakyThrows
    @Override
    public void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream( OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/KDS_Diagnose_bundle.json
     * Expected: /kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition_bundle.json
     */
    @Test
    public void assertToOpenEHRBundle() {
        final Composition composition = toOpenEhr.fhirToCompositionRm(context, getTestBundle(BUNDLE_SINGLE), operationaltemplate);
        standardsAsserter.assertComposition(composition, COMPOSITION_SINGLE, operationaltemplate);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/KDS_Diagnose_bundle_whole.json
     * Expected: /kds/diagnose/toOpenEHR/output/KDS_Diagnose_Composition_bundle_whole.json
     */
    @Ignore
    @Test
    public void assertToOpenEHRBundleWhole() {
        final Composition composition = toOpenEhr.fhirToCompositionRm(context, getTestBundle(BUNDLE), operationaltemplate);
        standardsAsserter.assertComposition(composition, COMPOSITION_MULTIPLE, operationaltemplate);
    }

    private void assertToOpenEHR(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_CONDITIONS[index]), operationaltemplate);
        standardsAsserter.assertComposition(composition, OPENEHR_COMPOSITIONS[index], operationaltemplate);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-1-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-2-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-3-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-4-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-5-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-6-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-7-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-8-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-9-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-10-diagnose-1.json
     * Expected: /kds/diagnose/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-diagnose-1.json
     */
    @Test
    public void assertToOpenEHR10() {
        assertToOpenEHR(9);
    }

    private void assertCondition(final Condition condition, final boolean second) {
        // - name: "contextStartTime"
        final String expectedTime = second ? "2023-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
//        Assert.assertEquals(expectedTime, condition.getRecordedDateElement().getValueAsString());


        // - name: "fallIdentifikationIdentifier"
        if (!second) {
            Assert.assertEquals("VN", ((Encounter) condition.getEncounter().getResource()).getIdentifier().get(0).getType().getCodingFirstRep().getCode());
            Assert.assertEquals("Encounter/123", ((Encounter) condition.getEncounter().getResource()).getIdentifier().get(0).getValue());
        }

        // - name: "status"
        if (!second) {
            Assert.assertEquals("unconfirmed", condition.getVerificationStatus().getCodingFirstRep().getCode());
            Assert.assertEquals("http://hl7.org/fhir/ValueSet/condition-ver-status",
                                condition.getVerificationStatus().getCodingFirstRep().getSystem());
        }

        //   - name: "date"
        final List<Extension> assertedExtensions = condition.getExtensionsByUrl(
                "http://hl7.org/fhir/StructureDefinition/condition-assertedDate");
        Assert.assertEquals(1, assertedExtensions.size());
        final String expectedAssertedTime = second ? "3022-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
        Assert.assertEquals(expectedAssertedTime,
                            ((DateTimeType) assertedExtensions.get(0).getValue()).getValueAsString());

        // dateTime, onset
        final String expectedOnsetStartTime = second ? "3022-02-03T04:05:06+01:00" : "2022-02-03T04:05:06+01:00";
        Assert.assertEquals(expectedOnsetStartTime, condition.getOnsetPeriod().getStartElement().getValueAsString());

        //- name: "clinicalStatus"
        Assert.assertEquals((second ? "referenced_" : "") + "Active", condition.getClinicalStatus().getText());
        Assert.assertEquals((second ? "referenced_" : "") + "at0026",
                            condition.getClinicalStatus().getCodingFirstRep().getCode());

        //  - name: "lebensphase"
        Assert.assertEquals((second ? "referenced_" : "") + "43",
                            ((CodeableConcept) condition.getOnsetPeriod().getStartElement()
                                    .getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase")
                                    .get(0).getValue()).getCodingFirstRep().getCode());
        Assert.assertEquals((second ? "referenced_" : "") + "44",
                            ((CodeableConcept) condition.getOnsetPeriod().getEndElement()
                                    .getExtensionsByUrl("http://fhir.de/StructureDefinition/lebensphase")
                                    .get(0).getValue()).getCodingFirstRep().getCode());

//         - name: "schweregrad"
        Assert.assertEquals((second ? "referenced_" : "") + "42",
                            condition.getSeverity().getCodingFirstRep().getCode());
        Assert.assertEquals(
                (second ? "referenced_" : "")
                        + "No example for termínology '//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/condition-severity' available",
                condition.getSeverity().getText());

        // bodySite
        final List<CodeableConcept> bodySites = condition.getBodySite();
        Assert.assertEquals(1, bodySites.size());
        final CodeableConcept bodySite = bodySites.get(0);
        Assert.assertEquals(1, bodySite.getCoding().size());
        final List<Coding> snomedBodySiteCodings = bodySite.getCoding().stream()
                .filter(bsite -> bsite.getSystem()
                        .equals((second ? "referenced_" : "")
                                        + "//fhir.hl7.org//ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/body-site"))
                .toList();
        Assert.assertEquals(1, snomedBodySiteCodings.size());
        Assert.assertEquals((second ? "referenced_" : "") + "42", snomedBodySiteCodings.get(0).getCode());

        // bodySiteCluster; nothing to do here because the cluster is overwritten to be a unidiretional toopenehr only

//          - name: "problemDiagnose", - name: "problemDiagnoseNameCode"
        Assert.assertEquals(2, condition.getCode().getCoding().size());
        Coding icd10code = condition.getCode().getCoding().get(0);
        Assert.assertEquals((second ? "referenced_" : "") + "kodierte_diagnose value", icd10code.getCode());
//      - name: "problemDiagnoseText"
//        Assert.assertEquals((second ? "referenced_" : "") + "freitextbeschreibung value",
//                            condition.getCode().getText());
//         - name: "icd10ProblemDiagnose"

        icd10code = condition.getCode().getCoding().get(1);
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", icd10code.getSystem());

//        - name: "codeIcd10Diagnosesicherheit"
        final CodeableConcept diagnosessicherheit = (CodeableConcept) icd10code.getExtensionsByUrl(
                "http://fhir.de/StructureDefinition/icd-10-gm-diagnosesicherheit").stream()
                .map(Extension::getValue).filter(value -> !value.isEmpty()).findAny().orElse(null);
        Assert.assertEquals((second ? "referenced_" : "") + "diagnosesicherheit",
                            diagnosessicherheit.getCodingFirstRep().getCode());
        Assert.assertEquals(
                (second ? "referenced_" : "")
                        + "//fhir.hl7.org//ValueSet/$expand?url=https://fhir.kbv.de/ValueSet/KBV_VS_SFHIR_ICD_DIAGNOSESICHERHEIT",
                diagnosessicherheit.getCodingFirstRep().getSystem());

        // - name: "mehrfachcodierung"
        final CodeableConcept mehrfachcodierung = (CodeableConcept) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/icd-10-gm-mehrfachcodierungs-kennzeichen").getValue();
        Assert.assertEquals("!", mehrfachcodierung.getCodingFirstRep().getCode());
        Assert.assertEquals("http://fhir.de/ValueSet/icd-10-gm-mehrfachcodierungs-kennzeichen", mehrfachcodierung.getCodingFirstRep().getSystem());
        Assert.assertEquals("!", mehrfachcodierung.getCodingFirstRep().getDisplay());

        // - name: "seitenlokalisation"
        final CodeableConcept seitenlokalisation = (CodeableConcept) icd10code.getExtensionByUrl(
                "http://fhir.de/StructureDefinition/seitenlokalisation").getValue();
        Assert.assertEquals((second ? "referenced_" : "") + "at0003", seitenlokalisation.getCodingFirstRep().getCode());
        Assert.assertEquals((second ? "referenced_" : "") + "local",
                            seitenlokalisation.getCodingFirstRep().getSystem());
        Assert.assertEquals((second ? "referenced_" : "") + "Left",
                            seitenlokalisation.getCodingFirstRep().getDisplay());


        Assert.assertEquals(3, icd10code.getExtension().size());
    }

    @Test
    public void toOpenEhr_single() {
        final Bundle testBundle = getTestBundle(BUNDLE_SINGLE);
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("2022-02-03T01:00:00", jsonObject.get("diagnose/context/start_time").getAsString());


        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose|code").getAsString());
        Assert.assertEquals("Secondary malignant neoplasm of lymph node", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose|terminology").getAsString());
        Assert.assertEquals("G", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit",
                            jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Confirmed diagnosis",
                            jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("at0002", jsonObject.get(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code").getAsString());
        Assert.assertEquals("local", jsonObject.get(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|terminology").getAsString());
        Assert.assertEquals("†", jsonObject.get(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|value").getAsString());
        Assert.assertEquals("321667001", jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/name_der_körperstelle|code")
                .getAsString());
        Assert.assertEquals("http://snomed.info/sct (20230131)",
                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/name_der_körperstelle|terminology")
                                    .getAsString());
        Assert.assertEquals("Respiratory tract, Upper lobe, bronchus or lung",
                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/name_der_körperstelle|value")
                                    .getAsString());
//        Assert.assertEquals("Secondary malignant neoplasm of lymph node",
//                            jsonObject.get("diagnose/diagnose:0/freitextbeschreibung").getAsString());
        Assert.assertEquals("Patient confirmed for secondary malignant neoplasm of lymph node.",
                            jsonObject.get("diagnose/diagnose:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("2024-12-24T16:13:43",
                            jsonObject.get("diagnose/diagnose:0/klinisch_relevanter_zeitraum_zeitpunkt_des_auftretens")
                                    .getAsString());
        Assert.assertEquals("41847000", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("Adulthood",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("271872005", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("Old age",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("24484000", jsonObject.get("diagnose/diagnose:0/schweregrad|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-severity",
                            jsonObject.get("diagnose/diagnose:0/schweregrad|terminology").getAsString());
        Assert.assertEquals("Severe", jsonObject.get("diagnose/diagnose:0/schweregrad|value").getAsString());
        Assert.assertEquals("encounter-id-1245",
                            jsonObject.get("diagnose/context/fallidentifikation/fall-kennung|code").getAsString());
        Assert.assertEquals("2025-02-03T05:05:06",
                            jsonObject.get("diagnose/diagnose:0/feststellungsdatum").getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|code")
                .getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical",
                            jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology")
                                    .getAsString());


    }

    public JsonObject toOpenEhr() {

        final Bundle testBundle = getTestBundle(BUNDLE);
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("2022-02-03T01:00:00", jsonObject.get("diagnose/context/start_time").getAsString());
        Assert.assertEquals("C34.1", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose|code").getAsString());
        Assert.assertEquals("Secondary malignant neoplasm of lymph node", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/diagnose:0/kodierte_diagnose|terminology").getAsString());
        Assert.assertEquals("G", jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit",
                            jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Confirmed diagnosis",
                            jsonObject.get("diagnose/diagnose:0/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("at0002", jsonObject.get(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code").getAsString());
        Assert.assertEquals("local", jsonObject.get(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|terminology").getAsString());
        Assert.assertEquals("†", jsonObject.get(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|value").getAsString());
        Assert.assertEquals("321667001", jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/name_der_körperstelle|code")
                .getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/name_der_körperstelle|terminology")
                                    .getAsString());
        Assert.assertEquals("Respiratory tract, Upper lobe, bronchus or lung",
                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/name_der_körperstelle|value")
                                    .getAsString());
//        Assert.assertEquals("Secondary malignant neoplasm of lymph node",
//                            jsonObject.get("diagnose/diagnose:0/freitextbeschreibung").getAsString());
        Assert.assertEquals("Patient confirmed for secondary malignant neoplasm of lymph node.",
                            jsonObject.get("diagnose/diagnose:0/diagnoseerläuterung").getAsString());
        Assert.assertEquals("41847000", jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("Adulthood",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("367640001", jsonObject.get("diagnose/diagnose:0/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("Old age",
                            jsonObject.get("diagnose/diagnose:0/lebensphase/ende|value").getAsString());
        Assert.assertEquals("24484000", jsonObject.get("diagnose/diagnose:0/schweregrad|code").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-severity",
                            jsonObject.get("diagnose/diagnose:0/schweregrad|terminology").getAsString());
        Assert.assertEquals("Severe", jsonObject.get("diagnose/diagnose:0/schweregrad|value").getAsString());
        Assert.assertEquals("encounter-id-1245",
                            jsonObject.get("diagnose/context/fallidentifikation/fall-kennung").getAsString());
        Assert.assertEquals("2025-02-03T05:05:06",
                            jsonObject.get("diagnose/diagnose:0/feststellungsdatum").getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|code")
                .getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical",
                            jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|terminology")
                                    .getAsString());
        Assert.assertEquals("active", jsonObject.get("diagnose/diagnose:0/klinischer_status/klinischer_status|value")
                .getAsString());
//
//        Assert.assertEquals("L",
//                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/laterality|code").getAsString());
//        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/seitenlokalisation",
//                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/laterality|terminology")
//                                    .getAsString());
//        Assert.assertEquals("Left side",
//                            jsonObject.get("diagnose/diagnose:0/anatomische_lokalisation/laterality|value").getAsString());

        Assert.assertEquals("ref_C34.1", jsonObject.get("diagnose/diagnose:1/kodierte_diagnose|code").getAsString());
        Assert.assertEquals("Malignant neoplasm of upper lobe, bronchus or lung", jsonObject.get("diagnose/diagnose:1/kodierte_diagnose|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/icd-10-gm", jsonObject.get("diagnose/diagnose:1/kodierte_diagnose|terminology").getAsString());
        Assert.assertEquals("V", jsonObject.get("diagnose/diagnose:1/diagnosesicherheit|code").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dimdi/diagnosesicherheit",
                            jsonObject.get("diagnose/diagnose:1/diagnosesicherheit|terminology").getAsString());
        Assert.assertEquals("Verdacht auf Diagnose",
                            jsonObject.get("diagnose/diagnose:1/diagnosesicherheit|value").getAsString());
        Assert.assertEquals("at0003", jsonObject.get(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code").getAsString());
        Assert.assertEquals("local", jsonObject.get(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|terminology").getAsString());
        Assert.assertEquals("*", jsonObject.get(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|value").getAsString());
        Assert.assertEquals("ref_368209003", jsonObject.get("diagnose/diagnose:1/anatomische_lokalisation/name_der_körperstelle|code")
                .getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:1/anatomische_lokalisation/name_der_körperstelle|terminology")
                                    .getAsString());
        Assert.assertEquals("Entire cardiovascular system",
                            jsonObject.get("diagnose/diagnose:1/anatomische_lokalisation/name_der_körperstelle|value")
                                    .getAsString());
//        Assert.assertEquals("Malignant neoplasm of upper lobe, bronchus or lung",
//                            jsonObject.get("diagnose/diagnose:1/freitextbeschreibung").getAsString());
        Assert.assertEquals(
                "ref_The patient has a history of high blood pressure, now presenting with severe hypertension.",
                jsonObject.get("diagnose/diagnose:1/diagnoseerläuterung").getAsString());
        Assert.assertEquals("ref_424144002",
                            jsonObject.get("diagnose/diagnose:1/lebensphase/beginn|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:1/lebensphase/beginn|terminology").getAsString());
        Assert.assertEquals("ref_Start of adulthood phase",
                            jsonObject.get("diagnose/diagnose:1/lebensphase/beginn|value").getAsString());
        Assert.assertEquals("ref_367640001", jsonObject.get("diagnose/diagnose:1/lebensphase/ende|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:1/lebensphase/ende|terminology").getAsString());
        Assert.assertEquals("ref_End of middle age phase",
                            jsonObject.get("diagnose/diagnose:1/lebensphase/ende|value").getAsString());
        Assert.assertEquals("ref_24484000", jsonObject.get("diagnose/diagnose:1/schweregrad|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.get("diagnose/diagnose:1/schweregrad|terminology").getAsString());
        Assert.assertEquals("ref_Severe", jsonObject.get("diagnose/diagnose:1/schweregrad|value").getAsString());
        Assert.assertEquals("2125-02-03T05:05:06",
                            jsonObject.get("diagnose/diagnose:1/feststellungsdatum").getAsString());
        Assert.assertEquals("ref_active", jsonObject.get("diagnose/diagnose:1/klinischer_status/klinischer_status|code")
                .getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/condition-clinical",
                            jsonObject.get("diagnose/diagnose:1/klinischer_status/klinischer_status|terminology")
                                    .getAsString());
        Assert.assertEquals("ref_active",
                            jsonObject.get("diagnose/diagnose:1/klinischer_status/klinischer_status|value")
                                    .getAsString());

        Assert.assertEquals("at0064",
                            jsonObject.get("diagnose/diagnose:1/klinischer_status/diagnoserolle|code")
                                    .getAsString());
        Assert.assertEquals("at0066",
                            jsonObject.get("diagnose/diagnose:0/klinischer_status/diagnoserolle|code")
                                    .getAsString());
        Assert.assertEquals("local",
                            jsonObject.get("diagnose/diagnose:0/klinischer_status/diagnoserolle|terminology")
                                    .getAsString());
        Assert.assertEquals("local",
                            jsonObject.get("diagnose/diagnose:1/klinischer_status/diagnoserolle|terminology")
                                    .getAsString());
        Assert.assertEquals("Nebendiagnose",
                            jsonObject.get("diagnose/diagnose:0/klinischer_status/diagnoserolle|value")
                                    .getAsString());
        Assert.assertEquals("Hauptdiagnose",
                            jsonObject.get("diagnose/diagnose:1/klinischer_status/diagnoserolle|value")
                                    .getAsString());

        return jsonObject;
    }
}
