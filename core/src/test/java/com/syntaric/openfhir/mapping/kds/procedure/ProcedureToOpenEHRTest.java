package com.syntaric.openfhir.mapping.kds.procedure;

import com.google.gson.JsonObject;
import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Action;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.nedap.archie.rm.datavalues.quantity.datetime.DvDateTime;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class ProcedureToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/procedure/procedure.context.yaml";
    final String OPT = "/kds/procedure/KDS_Prozedur.opt";

    final String[] FHIR_INPUTS = {
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-1-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-1-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-10-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-10-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-2-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-2-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-2-prozedur-3.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-3-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-3-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-3-prozedur-3.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-4-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-4-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-5-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-5-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-6-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-6-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-7-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-7-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-8-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-8-prozedur-2.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-8-prozedur-3.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-9-prozedur-1.json",
            "/kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-9-prozedur-2.json"
    };

    final String[] OPENEHR_OUTPUTS = {
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-prozedur-3.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-prozedur-3.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-prozedur-2.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-prozedur-3.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-prozedur-1.json",
            "/kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-prozedur-2.json"};

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
        assertCompositionIgnoringVolatileProcedureTime(composition, OPENEHR_OUTPUTS[index]);
    }

    @SneakyThrows
    private void assertCompositionIgnoringVolatileProcedureTime(final Composition composition,
                                                                final String expectedPath) {
        com.syntaric.openfhir.mapping.OptCompositionValidator.assertValid(operationaltemplate, composition);

        // Procedure mappings populate ACTION.time from runtime context; this value is intentionally non-deterministic.
        clearVolatileProcedureTimes(composition);

        final Composition expectedComposition = JacksonUtil.getObjectMapper()
                .readValue(getFile(expectedPath), Composition.class);
        clearVolatileProcedureTimes(expectedComposition);

        final String actual = new CanonicalJson().marshal(composition);
        final String expected = new CanonicalJson().marshal(expectedComposition);
        JSONAssert.assertEquals(new JSONObject(expected), new JSONObject(actual), true);
    }

    private void clearVolatileProcedureTimes(final Composition composition) {
        for (ContentItem contentItem : composition.getContent()) {
            if (contentItem instanceof Action action) {
                action.setTime((DvDateTime) null);
            }
        }
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/KDS_Prozedur_bundle.json
     * Expected: /kds/procedure/toOpenEHR/output/KDS_Prozedur.flat_textvalue.json
     */
    @Test
    public void assertToOpenEHRLegacyFlatDetails() {
        final JsonObject jsonObject = toOpenEhr
                .fhirToFlatJsonObject(
                        context, getTestBundle("/kds/procedure/toOpenEHR/input/KDS_Prozedur_bundle.json"),
                        operationaltemplate);

        Assert.assertEquals("5-470", jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/name_der_prozedur|code")
                .getAsString());
        Assert.assertEquals("Appendectomy",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/name_der_prozedur|value")
                                    .getAsString());
        Assert.assertEquals("http://fhir.de/CodeSystem/bfarm/ops (20200131)",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/name_der_prozedur|terminology")
                                    .getAsString());

        Assert.assertEquals("Procedure completed successfully with no complications.",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kommentar").getAsString());
        Assert.assertEquals("103693007",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kategorie_der_prozedur|code")
                                    .getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kategorie_der_prozedur|terminology")
                                    .getAsString());
        Assert.assertEquals("Diagnostic procedure",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/kategorie_der_prozedur|value")
                                    .getAsString());
        Assert.assertEquals("durchführungsabsicht",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/durchführungsabsicht|code")
                                    .getAsString());
        Assert.assertEquals("valuedurchführungsabsicht",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/durchführungsabsicht|value")
                                    .getAsString());
        Assert.assertEquals("Durchfuehrungsabsicht",
                            jsonObject.getAsJsonPrimitive("kds_prozedur/prozedur:0/durchführungsabsicht|terminology")
                                    .getAsString());
        Assert.assertEquals("818981001", jsonObject.getAsJsonPrimitive(
                        "kds_prozedur/prozedur:0/anatomische_lokalisation/name_der_körperstelle|code")
                .getAsString());
        Assert.assertEquals("http://snomed.info/sct",
                            jsonObject.getAsJsonPrimitive(
                                            "kds_prozedur/prozedur:0/anatomische_lokalisation/name_der_körperstelle|terminology")
                                    .getAsString());
        Assert.assertEquals("Abdomen", jsonObject.getAsJsonPrimitive(
                "kds_prozedur/prozedur:0/anatomische_lokalisation/name_der_körperstelle|value").getAsString());
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-1-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-1-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-10-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-10-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-2-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-2-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-2-prozedur-3.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-prozedur-3.json
     */
    @Test
    public void assertToOpenEHR_7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-3-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-3-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-3-prozedur-3.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-prozedur-3.json
     */
    @Test
    public void assertToOpenEHR_10() {
        assertToOpenEHR(9);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-4-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_11() {
        assertToOpenEHR(10);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-4-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_12() {
        assertToOpenEHR(11);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-5-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_13() {
        assertToOpenEHR(12);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-5-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_14() {
        assertToOpenEHR(13);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-6-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_15() {
        assertToOpenEHR(14);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-6-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_16() {
        assertToOpenEHR(15);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-7-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_17() {
        assertToOpenEHR(16);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-7-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_18() {
        assertToOpenEHR(17);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-8-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_19() {
        assertToOpenEHR(18);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-8-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_20() {
        assertToOpenEHR(19);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-8-prozedur-3.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-prozedur-3.json
     */
    @Test
    public void assertToOpenEHR_21() {
        assertToOpenEHR(20);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-9-prozedur-1.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-prozedur-1.json
     */
    @Test
    public void assertToOpenEHR_22() {
        assertToOpenEHR(21);
    }

    /**
     * Input: /kds/procedure/toOpenEHR/input/Procedure-mii-exa-test-data-patient-9-prozedur-2.json
     * Expected: /kds/procedure/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-prozedur-2.json
     */
    @Test
    public void assertToOpenEHR_23() {
        assertToOpenEHR(22);
    }

}
