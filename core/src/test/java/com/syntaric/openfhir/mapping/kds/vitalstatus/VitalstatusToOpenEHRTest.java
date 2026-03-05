package com.syntaric.openfhir.mapping.kds.vitalstatus;

import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class VitalstatusToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/vitalstatus/KDS_Vitalstatus.context.yaml";
    final String OPT = "/kds/vitalstatus/KDS_Vitalstatus.opt";

    final String[] FHIR_INPUTS = {
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-1-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-2-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-5-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-6-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-7-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-8-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-9-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-10-vitalstatus-1.json"
    };

    final String[] OPENEHR_OUTPUTS = {
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-vitalstatus-1.json"
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

    private void assertToOpenEHR(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_INPUTS[index]), operationaltemplate);
        assertCompositionIgnoringVolatileContextStartTime(composition, OPENEHR_OUTPUTS[index]);
    }

    @SneakyThrows
    private void assertCompositionIgnoringVolatileContextStartTime(Composition composition, String expectedPath) {
        com.syntaric.openfhir.mapping.OptCompositionValidator.assertValid(operationaltemplate, composition);

        final JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        final JSONObject expected = new JSONObject(getFile(expectedPath));
        actual.getJSONObject("context").remove("start_time");
        expected.getJSONObject("context").remove("start_time");
        JSONAssert.assertEquals(expected, actual, true);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-1-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-2-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-5-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-6-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-7-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-8-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-9-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-10-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-vitalstatus-1.json
     */
    @Test
    public void assertToOpenEHR_8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/input/Observation-mii-exa-test-data-patient-1-vitalstatus-1.json
     */
    @Test
    @SneakyThrows
    public void assertToOpenEHRDetailedFields() {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_INPUTS[0]), operationaltemplate);
        final JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        final JSONObject vitalStatusCode = actual.getJSONArray("content").getJSONObject(0)
                .getJSONObject("data")
                .getJSONArray("items").getJSONObject(0)
                .getJSONObject("value")
                .getJSONObject("defining_code");
        Assert.assertEquals("T", vitalStatusCode.getString("code_string"));
    }
}
