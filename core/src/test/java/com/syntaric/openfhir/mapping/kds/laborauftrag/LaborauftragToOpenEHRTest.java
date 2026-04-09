package com.syntaric.openfhir.mapping.kds.laborauftrag;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.junit.Test;

public class LaborauftragToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING = "/kds/core/projects/org.highmed/KDS/laborauftrag/KDS_laborauftrag.context.yaml";
    final String OPT = "/kds/laborauftrag/KDS_Laborauftrag.opt";

    final String BUNDLE = "/kds/laborauftrag/toOpenEHR/input/KDS_Laborauftrag_bundle.json";
    final String[] FHIR_SERVICE_REQUESTS = {
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-1-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-2-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-3-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-4-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-5-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-6-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-7-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-8-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-9-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-10-labrequest-1.json"
    };

    final String COMPOSITION_BUNDLE = "/kds/laborauftrag/toOpenEHR/output/Composition-KDS_Laborauftrag_bundle.json";
    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-labrequest-1.json",
            "/kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-labrequest-1.json"
    };

    @SneakyThrows
    @Override
    public void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/KDS_Laborauftrag_bundle.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-KDS_Laborauftrag_bundle.json
     */
    @Test
    public void assertToOpenEHRBundle() {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(BUNDLE), webTemplate);
        standardsAsserter.assertComposition(composition, COMPOSITION_BUNDLE, operationaltemplate);
    }

    private void assertToOpenEHR(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_SERVICE_REQUESTS[index]),
                        webTemplate);
        standardsAsserter.assertComposition(composition, OPENEHR_COMPOSITIONS[index], operationaltemplate);
    }

    private void assertToOpenEHRWihtoutOPTVal(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_SERVICE_REQUESTS[index]),
                        webTemplate);
        standardsAsserter.assertCompositionWihtoutOPTValidataion(composition, OPENEHR_COMPOSITIONS[index],
                                                                 operationaltemplate);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-1-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR1() {
        assertToOpenEHRWihtoutOPTVal(0); // testdata has no Pracitioner and in the templte its required.
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-2-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-3-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-4-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-5-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-6-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-7-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-8-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-9-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/input/ServiceRequest-mii-exa-test-data-patient-10-labrequest-1.json
     * Expected: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-labrequest-1.json
     */
    @Test
    public void assertToOpenEHR10() {
        assertToOpenEHR(9);
    }

    @SneakyThrows
    public JsonObject toOpenEhr() {
        final Bundle testBundle = getTestBundle(BUNDLE);
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, webTemplate);


        Assert.assertEquals("123456-0_KH", jsonObject.getAsJsonPrimitive(
                        "leistungsanforderung/laborleistung/auftrags-id_des_anfordernden_einsendenden_systems_plac|id")
                .getAsString());
        Assert.assertEquals("completed",
                            jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/status_der_anfrage|code")
                                    .getAsString());
        Assert.assertEquals("2345-7", jsonObject.getAsJsonPrimitive(
                "leistungsanforderung/laborleistung/aktuelle_aktivität/name_der_laborleistung|code").getAsString());
        Assert.assertEquals("http://loinc.org", jsonObject.getAsJsonPrimitive(
                        "leistungsanforderung/laborleistung/aktuelle_aktivität/name_der_laborleistung|terminology")
                .getAsString());
        Assert.assertEquals("Blood Glucose Test", jsonObject.getAsJsonPrimitive(
                "leistungsanforderung/laborleistung/aktuelle_aktivität/name_der_laborleistung|value").getAsString());
//        Assert.assertEquals("order",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/art_der_laborleistung_kategorie|code").getAsString());
//        Assert.assertEquals("http://terminology.hl7.org/ValueSet/observation-category",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/art_der_laborleistung_kategorie|terminology").getAsString());
//        Assert.assertEquals("Laboratory",   jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/aktuelle_aktivität/art_der_laborleistung_kategorie|value").getAsString());
        Assert.assertEquals("order", jsonObject.getAsJsonPrimitive(
                "leistungsanforderung/laborleistung/aktuelle_aktivität/intention|code").getAsString());
        Assert.assertEquals("Sample collected in the morning.", jsonObject.getAsJsonPrimitive(
                "leistungsanforderung/laborleistung/aktuelle_aktivität/kommentar").getAsString());
        Assert.assertEquals("Example Hospital",
                            jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/einsender/namenszeile")
                                    .getAsString());
        Assert.assertEquals("ORG-001",
                            jsonObject.getAsJsonPrimitive("leistungsanforderung/laborleistung/einsender/identifier|id")
                                    .getAsString());

        Assert.assertEquals("SP-987654", jsonObject.getAsJsonPrimitive(
                        "leistungsanforderung/laborleistung/aktuelle_aktivität/probe:0/laborprobenidentifikator|id")
                .getAsString());
        Assert.assertEquals("2024-08-24T11:00:00", jsonObject.getAsJsonPrimitive(
                        "leistungsanforderung/laborleistung/aktuelle_aktivität/probe:0/zeitpunkt_der_probenentnahme/date_time_value")
                .getAsString());
        Assert.assertEquals("example-practitioner", jsonObject.getAsJsonPrimitive(
                        "leistungsanforderung/laborleistung/aktuelle_aktivität/probe:0/identifikator_des_probenehmers|id")
                .getAsString());


        return jsonObject;
    }

}
