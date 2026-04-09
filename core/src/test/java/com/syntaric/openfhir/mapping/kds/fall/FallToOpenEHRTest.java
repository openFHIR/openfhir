package com.syntaric.openfhir.mapping.kds.fall;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.junit.Test;

public class FallToOpenEHRTest extends KdsGenericTest {


    // mappings / config
    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING =
            "/kds/core/projects/org.highmed/KDS/fall/KDS_fall_einfach.context.yaml";
    final String OPT =
            "/kds/fall/KDS_Fall_einfach.opt";

    // ===== INPUT =====
    final String FALL_EINFACH =
            "/kds/fall/toOpenEHR/input/KDS_Fall_einfach.Bundle.json";

    final String[] FHIR_ENCOUNTERS = {
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-1-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-1-encounter-2.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-2-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-3-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-4-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-5-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-6-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-7-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-8-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-9-encounter-1.json",
            "/kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-10-encounter-1.json"
    };

    // ===== OUTPUT =====
    final String FALL_EINFACH_COMPOSITION =
            "/kds/fall/toOpenEHR/output/KDS_fall_flat.json";

    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-encounter-2.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-encounter-1.json",
            "/kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-encounter-1.json"
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

    private void assertToOpenEHR(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(
                        context,
                        getTestBundle(FHIR_ENCOUNTERS[index]),
                        webTemplate
                );
        standardsAsserter.assertComposition(composition, OPENEHR_COMPOSITIONS[index], operationaltemplate);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-1-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-encounter-1.json
     */
    @Test
    public void assertToOpenEHR1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-1-encounter-2.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-encounter-2.json
     */
    @Test
    public void assertToOpenEHR2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-2-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-encounter-1.json
     */
    @Test
    public void assertToOpenEHR3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-3-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-encounter-1.json
     */
    @Test
    public void assertToOpenEHR4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-4-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-encounter-1.json
     */
    @Test
    public void assertToOpenEHR5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-5-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-encounter-1.json
     */
    @Test
    public void assertToOpenEHR6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-6-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-encounter-1.json
     */
    @Test
    public void assertToOpenEHR7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-7-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-encounter-1.json
     */
    @Test
    public void assertToOpenEHR8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-8-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-encounter-1.json
     */
    @Test
    public void assertToOpenEHR9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-9-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-encounter-1.json
     */
    @Test
    public void assertToOpenEHR10() {
        assertToOpenEHR(9);
    }

    /**
     * Input: /kds/fall/toOpenEHR/input/Encounter-mii-exa-test-data-patient-10-encounter-1.json
     * Expected: /kds/fall/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-encounter-1.json
     */
    @Test
    public void assertToOpenEHR11() {
        assertToOpenEHR(10);
    }

    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(FALL_EINFACH);

        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, webTemplate);

        Assert.assertEquals("einrichtungskontakt",
                jsonObject.get("kds_fall_einfach/context/falltyp|code").getAsString());
        Assert.assertEquals("local_terms",
                jsonObject.get("kds_fall_einfach/context/falltyp|terminology").getAsString());
        Assert.assertEquals("AMB", jsonObject.get("kds_fall_einfach/context/fallklasse|code").getAsString());
        Assert.assertEquals("ambulatory", jsonObject.get("kds_fall_einfach/context/fallklasse|value").getAsString());
        Assert.assertEquals("http://terminology.hl7.org/CodeSystem/v3-ActCode",
                jsonObject.get("kds_fall_einfach/context/fallklasse|terminology").getAsString());
        Assert.assertEquals("finished", jsonObject.get("kds_fall_einfach/context/fallart|code").getAsString());
        Assert.assertEquals("VN-encounter-id-12345", jsonObject.get("kds_fall_einfach/context/fall-id").getAsString());
        Assert.assertEquals("Example Hospital",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/überweiser/namenszeile")
                        .getAsString());
        Assert.assertEquals("ORG-001", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/überweiser/identifier:0/identifier_value|id").getAsString());
        Assert.assertEquals("01", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_ersteundzweitestelle|code").getAsString());
        Assert.assertEquals("Krankenhausbehandlung, vollstationär", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_ersteundzweitestelle|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundErsteUndZweiteStelle", jsonObject.get(
                        "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_ersteundzweitestelle|terminology")
                .getAsString());
        Assert.assertEquals("0",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_dritte_stelle|code")
                        .getAsString());
        Assert.assertEquals("Anderes", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_dritte_stelle|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundDritteStelle", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_dritte_stelle|terminology").getAsString());
        Assert.assertEquals("1",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_vierte_stelle|code")
                        .getAsString());
        Assert.assertEquals("Normalfall", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_vierte_stelle|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dkgev/AufnahmegrundVierteStelle", jsonObject.get(
                "kds_fall_einfach/institutionsaufenthalt/aufnahmegrund_-_vierte_stelle|terminology").getAsString());
        Assert.assertEquals("E", jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmekategorie|code")
                .getAsString());
        Assert.assertEquals("Einweisung durch einen Arzt", jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmekategorie|value")
                .getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dgkev/Aufnahmeanlass",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmekategorie|terminology")
                        .getAsString());
        Assert.assertEquals("01",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/outcome:0|code").getAsString());
        Assert.assertEquals("Reguläre Entlassung",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/outcome:0|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/entlassungsgrund",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/outcome:0|terminology")
                        .getAsString());
        Assert.assertEquals("referral-diagnosis",
                jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosetyp|code").getAsString());
        Assert.assertEquals("Einweisungs-/Überweisungsdiagnos",
                jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosetyp|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dki-diagnosetyp",
                jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosetyp|terminology").getAsString());
        Assert.assertEquals("surgery-diagnosis",
                jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosesubtyp|code").getAsString());
        Assert.assertEquals("Operationsdiagnose",
                jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosesubtyp|value").getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/dki-diagnosesubtyp",
                jsonObject.get("kds_fall_einfach/problem_diagnose/diagnosesubtyp|terminology")
                        .getAsString());
//        Assert.assertEquals("Organization",
//                            jsonObject.get("kds_fall_einfach/context/_health_care_facility|name").getAsString());
        Assert.assertEquals("2022-02-03T05:05:06", jsonObject.get("kds_fall_einfach/context/start_time").getAsString());
        Assert.assertEquals("2022-04-03T06:05:06", jsonObject.get("kds_fall_einfach/context/_end_time").getAsString());
//        Assert.assertEquals("Organization", jsonObject.get("kds_fall_einfach/composer|name").getAsString());
        Assert.assertEquals("2022-02-03T05:05:06",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/aufnahmedatum").getAsString());
        Assert.assertEquals("2022-04-03T06:05:06",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/entlassungsdatum").getAsString());
        Assert.assertEquals("zimmer-1-identifier",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/standort:0/zimmer").getAsString());
        Assert.assertEquals("station-1-identifier",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/standort:0/station").getAsString());
        Assert.assertEquals("bett-1-identifier",
                jsonObject.get("kds_fall_einfach/institutionsaufenthalt/standort:0/bett").getAsString());

        return jsonObject;
    }
}
