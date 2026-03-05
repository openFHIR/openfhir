package com.syntaric.openfhir.mapping.kds.studienteilnahme;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class StudienteilnahmeToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/studienteilnahme/studienteilnahme.context.yaml";
    final String OPT = "/kds/studienteilnahme/Studienteilnahme.opt";

    final String BUNDLE = "/kds/studienteilnahme/toOpenEHR/input/studienteilnahme_bundle.json";
    final String COMPOSITION_BUNDLE = "/kds/studienteilnahme/toOpenEHR/output/Composition-studienteilnahme_bundle.json";

    final String[] FHIR_INPUTS = {
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-1-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-2-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-3-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-4-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-5-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-6-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-7-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-8-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-9-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-10-consent-1.json"
    };

    final String[] OPENEHR_OUTPUTS = {
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-consent-1.json",
            "/kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-consent-1.json"
    };

    @SneakyThrows
    @Override
    public void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/studienteilnahme_bundle.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-studienteilnahme_bundle.json
     */
    @Test
    public void assertToOpenEHRBundle() {
        final Composition composition = toOpenEhr.fhirToCompositionRm(context, getTestBundle(BUNDLE), operationaltemplate);
        assertCompositionIgnoringActionTime(composition, COMPOSITION_BUNDLE);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/studienteilnahme_bundle.json
     */
    @Test
    public void assertToOpenEHRFlatFieldsFromBundle() {
        final Bundle testBundle = getTestBundle(BUNDLE);
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("245", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/einwilligungserklärung/ism_transition/current_state|code").getAsString());
        Assert.assertEquals("2024-08-22T10:30:00", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/context/start_time").getAsString());
        Assert.assertEquals("2023-07-22T10:30:00", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/einwilligungserklärung/studienteilnahme/beginn_der_teilnahme").getAsString());
        Assert.assertEquals("2024-08-22T10:30:00", jsonObject.getAsJsonPrimitive(
                "studienteilnahme/einwilligungserklärung/studienteilnahme/ende_der_teilnahme").getAsString());
    }

    private void assertToOpenEHR(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_INPUTS[index]), operationaltemplate);
        assertCompositionIgnoringActionTime(composition, OPENEHR_OUTPUTS[index]);
    }

    @SneakyThrows
    private void assertCompositionIgnoringActionTime(Composition composition, String expectedPath) {
        final JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        final JSONObject expected = new JSONObject(getFile(expectedPath));
        if (actual.has("content")) {
            actual.getJSONArray("content").getJSONObject(0).remove("time");
        }
        if (expected.has("content")) {
            expected.getJSONArray("content").getJSONObject(0).remove("time");
        }
        JSONAssert.assertEquals(expected, actual, true);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-1-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-consent-1.json
     */
    @Test
    public void assertToOpenEHR1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-2-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-consent-1.json
     */
    @Test
    public void assertToOpenEHR2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-3-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-consent-1.json
     */
    @Test
    public void assertToOpenEHR3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-4-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-consent-1.json
     */
    @Test
    public void assertToOpenEHR4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-5-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-consent-1.json
     */
    @Test
    public void assertToOpenEHR5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-6-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-consent-1.json
     */
    @Test
    public void assertToOpenEHR6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-7-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-consent-1.json
     */
    @Test
    public void assertToOpenEHR7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-8-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-consent-1.json
     */
    @Test
    public void assertToOpenEHR8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-9-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-consent-1.json
     */
    @Test
    public void assertToOpenEHR9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/input/Consent-mii-exa-test-data-patient-10-consent-1.json
     * Expected: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-consent-1.json
     */
    @Test
    public void assertToOpenEHR10() {
        assertToOpenEHR(9);
    }
}
