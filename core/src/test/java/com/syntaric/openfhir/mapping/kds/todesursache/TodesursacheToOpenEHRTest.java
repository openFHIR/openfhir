package com.syntaric.openfhir.mapping.kds.todesursache;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.junit.Assert;
import org.junit.Test;

public class TodesursacheToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/todesursache/KDS_Todesursache.context.yaml";
    final String OPT = "/kds/todesursache/KDS_Todesursache.opt";

    final String[] FHIR_INPUTS = {
            "/kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-1-todesursache-1.json",
            "/kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-2-todesursache-1.json",
            "/kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-8-todesursache-1.json"
    };

    final String[] OPENEHR_OUTPUTS = {
            "/kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-todesursache-1.json",
            "/kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-todesursache-1.json",
            "/kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-todesursache-1.json"
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
        standardsAsserter.assertComposition(composition, OPENEHR_OUTPUTS[index], operationaltemplate);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-1-todesursache-1.json
     * Expected: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-todesursache-1.json
     */
    @Test
    public void assertToOpenEHR_1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-2-todesursache-1.json
     * Expected: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-todesursache-1.json
     */
    @Test
    public void assertToOpenEHR_2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-8-todesursache-1.json
     * Expected: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-todesursache-1.json
     */
    @Test
    public void assertToOpenEHR_3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/input/Condition-mii-exa-test-data-patient-1-todesursache-1.json
     */
    @Test
    public void assertToOpenEHRDetailedFields() {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_INPUTS[0]), operationaltemplate);
        final JsonObject compositionJson =
                JsonParser.parseString(new CanonicalJson().marshal(composition)).getAsJsonObject();

        Assert.assertEquals("2024-02-22T00:00:00",
                compositionJson.getAsJsonObject("context")
                        .getAsJsonObject("start_time")
                        .get("value")
                        .getAsString());

        final JsonArray items = compositionJson
                .getAsJsonArray("content").get(0).getAsJsonObject()
                .getAsJsonObject("data")
                .getAsJsonArray("items");

        final JsonObject causeOfDeath = items.get(0).getAsJsonObject();
        Assert.assertEquals("A15.0",
                causeOfDeath.getAsJsonObject("value")
                        .getAsJsonObject("defining_code")
                        .get("code_string")
                        .getAsString());
    }
}
