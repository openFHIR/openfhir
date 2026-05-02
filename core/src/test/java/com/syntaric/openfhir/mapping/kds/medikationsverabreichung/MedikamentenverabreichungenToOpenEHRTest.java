package com.syntaric.openfhir.mapping.kds.medikationsverabreichung;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.junit.Test;

public class MedikamentenverabreichungenToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/medikationsverabreichung/KDS_medikationsverabreichung.context.yaml";
    final String HELPER_LOCATION = "/kds/medikationsverabreichung/";
    final String OPT = "/kds/medikationsverabreichung/KDS_Medikamentenverabreichungen.opt";
    final String BUNDLE = "/kds/medikationsverabreichung/toOpenEHR/input/KDS_Medikamentenverabreichungen_Bundle.json";

    final String[] FHIR_INPUTS = {
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-3.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-3.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-3.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-2.json"
    };

    final String[] OPENEHR_OUTPUTS = {
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-3.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-3.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-3.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-2.json"
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
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_INPUTS[index]), webTemplate);
        standardsAsserter.assertCompositionWihtoutOPTValidataion(composition, OPENEHR_OUTPUTS[index], operationaltemplate);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_1() { assertToOpenEHR(0); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_2() { assertToOpenEHR(1); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_3() { assertToOpenEHR(2); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_4() { assertToOpenEHR(3); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-3.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-3.json
     */
    @Test
    public void assertToOpenEHR_5() { assertToOpenEHR(4); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_6() { assertToOpenEHR(5); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_7() { assertToOpenEHR(6); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-3.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-3.json
     */
    @Test
    public void assertToOpenEHR_8() { assertToOpenEHR(7); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_9() { assertToOpenEHR(8); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_10() { assertToOpenEHR(9); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_11() { assertToOpenEHR(10); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_12() { assertToOpenEHR(11); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_13() { assertToOpenEHR(12); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_14() { assertToOpenEHR(13); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_15() { assertToOpenEHR(14); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_16() { assertToOpenEHR(15); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_17() { assertToOpenEHR(16); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_18() { assertToOpenEHR(17); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-3.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-3.json
     */
    @Test
    public void assertToOpenEHR_19() { assertToOpenEHR(18); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_20() { assertToOpenEHR(19); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_21() { assertToOpenEHR(20); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-1.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-1.json
     */
    @Test
    public void assertToOpenEHR_22() { assertToOpenEHR(21); }
    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/input/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-2.json
     * Expected: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-2.json
     */
    @Test
    public void assertToOpenEHR_23() { assertToOpenEHR(22); }

    @Test
    public void kdsMedicationAdministrations_toOpenEhr_bundleFlat() {
        final Bundle testBundle = getTestBundle(BUNDLE);
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, webTemplate);

        if (jsonObject.has("kds_medikamentenverabreichungen/context/bericht_id")) {
            Assert.assertEquals("MA123456", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/bericht_id").getAsString());
        }

        Assert.assertEquals("232", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/setting|code").getAsString());
        Assert.assertEquals("openehr", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/setting|terminology").getAsString());
        Assert.assertEquals("secondary medical care", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/context/setting|value").getAsString());

        Assert.assertEquals("textab", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/kommentar").getAsString());

        Assert.assertEquals("20 mg orally once daily", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/dosierung_freitext").getAsString());
        Assert.assertEquals(250.0, jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/dosis|magnitude").getAsDouble(), 0);
        Assert.assertEquals("mg", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/dosierung/dosis|unit").getAsString());

        Assert.assertEquals("26643006", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/details_zur_verabreichung/verabreichungsweg|code").getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/details_zur_verabreichung/verabreichungsweg|terminology").getAsString());
        Assert.assertEquals("Oral", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/details_zur_verabreichung/verabreichungsweg|value").getAsString());

        Assert.assertEquals("UTA", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/darreichungsform|code").getAsString());
        Assert.assertEquals("https://fhir.kbv.de/CodeSystem/KBV_CS_SFHIR_BMP_DARREICHUNGSFORM", jsonObject.getAsJsonPrimitive("kds_medikamentenverabreichungen/arzneimittelanwendung:0/arzneimittel/darreichungsform|terminology").getAsString());
    }
}
