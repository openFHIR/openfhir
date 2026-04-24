package com.syntaric.openfhir.mapping.kds.medikationseintrag;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import java.io.IOException;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class MedikationseintragToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/medikationseintrag/KDS_medikationseintrag.context.yaml";
    final String HELPER_LOCATION = "/kds/medikationseintrag/";
    final String OPT = "/kds/medikationseintrag/KDS_Medikationseintrag.opt";
    final String FLAT = "/kds/medikationseintrag/toOpenEHR/output/KDS_Medikationseintrag.flat.json";

    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medstatement-3.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-3.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-4.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-5.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-3.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-4.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medstatement-3.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medstatement-3.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medstatement-2.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medstatement-1.json",
            "/kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medstatement-2.json"
    };

    final String[] FHIR_BUNDLES = {
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-1-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-1-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-1-medstatement-3.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-3.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-4.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-5.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-3.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-4.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-4-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-4-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-5-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-5-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-6-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-6-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-6-medstatement-3.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-7-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-7-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-8-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-8-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-8-medstatement-3.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-9-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-9-medstatement-2.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-10-medstatement-1.json",
            "/kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-10-medstatement-2.json"
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

    @SneakyThrows
    private void assertToFHIR(int index) {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[index]),
                Composition.class);
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLES[index]);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-1-medstatement-1.json
     */
    @Test
    public void assertToFHIR_1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-1-medstatement-2.json
     */
    @Test
    public void assertToFHIR_2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medstatement-3.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-1-medstatement-3.json
     */
    @Test
    public void assertToFHIR_3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-1.json
     */
    @Test
    public void assertToFHIR_4() {
        assertToFHIR(3);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-2.json
     */
    @Test
    public void assertToFHIR_5() {
        assertToFHIR(4);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-3.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-3.json
     */
    @Test
    public void assertToFHIR_6() {
        assertToFHIR(5);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-4.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-4.json
     */
    @Test
    public void assertToFHIR_7() {
        assertToFHIR(6);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medstatement-5.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-2-medstatement-5.json
     */
    @Test
    public void assertToFHIR_8() {
        assertToFHIR(7);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-1.json
     */
    @Test
    public void assertToFHIR_9() {
        assertToFHIR(8);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-2.json
     */
    @Test
    public void assertToFHIR_10() {
        assertToFHIR(9);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-3.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-3.json
     */
    @Test
    public void assertToFHIR_11() {
        assertToFHIR(10);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medstatement-4.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-3-medstatement-4.json
     */
    @Test
    public void assertToFHIR_12() {
        assertToFHIR(11);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-4-medstatement-1.json
     */
    @Test
    public void assertToFHIR_13() {
        assertToFHIR(12);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-4-medstatement-2.json
     */
    @Test
    public void assertToFHIR_14() {
        assertToFHIR(13);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-5-medstatement-1.json
     */
    @Test
    public void assertToFHIR_15() {
        assertToFHIR(14);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-5-medstatement-2.json
     */
    @Test
    public void assertToFHIR_16() {
        assertToFHIR(15);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-6-medstatement-1.json
     */
    @Test
    public void assertToFHIR_17() {
        assertToFHIR(16);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-6-medstatement-2.json
     */
    @Test
    public void assertToFHIR_18() {
        assertToFHIR(17);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medstatement-3.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-6-medstatement-3.json
     */
    @Test
    public void assertToFHIR_19() {
        assertToFHIR(18);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-7-medstatement-1.json
     */
    @Test
    public void assertToFHIR_20() {
        assertToFHIR(19);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-7-medstatement-2.json
     */
    @Test
    public void assertToFHIR_21() {
        assertToFHIR(20);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-8-medstatement-1.json
     */
    @Test
    public void assertToFHIR_22() {
        assertToFHIR(21);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-8-medstatement-2.json
     */
    @Test
    public void assertToFHIR_23() {
        assertToFHIR(22);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medstatement-3.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-8-medstatement-3.json
     */
    @Test
    public void assertToFHIR_24() {
        assertToFHIR(23);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-9-medstatement-1.json
     */
    @Test
    public void assertToFHIR_25() {
        assertToFHIR(24);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-9-medstatement-2.json
     */
    @Test
    public void assertToFHIR_26() {
        assertToFHIR(25);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medstatement-1.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-10-medstatement-1.json
     */
    @Test
    public void assertToFHIR_27() {
        assertToFHIR(26);
    }

    /**
     * Input: /kds/medikationseintrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medstatement-2.json
     * Expected: /kds/medikationseintrag/toFHIR/output/MedicationStatement-mii-exa-test-data-patient-10-medstatement-2.json
     */
    @Test
    public void assertToFHIR_28() {
        assertToFHIR(27);
    }

    @Test
    @Ignore // todo: unignor ewhen mplementing ratio back
    public void kdsMedicationList_toFhir_rateRatioFromRateAndDuration() throws IOException {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[8]),
                Composition.class);
        final FlatJsonMarshaller flatMarshaller = new FlatJsonMarshaller();
        final JsonObject flat = new Gson().fromJson(flatMarshaller.toFlatJson(composition, webTemplate), JsonObject.class);

        String rateMag = "medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/verabreichungsrate/quantity_value|magnitude";
        String rateUnit = "medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/verabreichungsrate/quantity_value|unit";
        String duration = "medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/verabreichungsdauer";

        flat.addProperty(rateMag, 50.0);
        flat.addProperty(rateUnit, "mg");
        flat.addProperty(duration, "PT3H");
        // This fixture can also contain a text-based rate; remove it so quantity+duration maps deterministically to Ratio.
        flat.remove("medikamentenliste/aussage_zur_medikamenteneinnahme:0/dosierung:0/verabreichungsrate/text_value");

        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(flat.toString(), webTemplate);
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);

        final MedicationStatement stmt = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationStatement)
                .map(en -> (MedicationStatement) en.getResource())
                .findFirst()
                .orElseThrow();

        Dosage dosageWithRateRatio = stmt.getDosage().stream()
                .filter(Dosage::hasDoseAndRate)
                .filter(d -> d.getDoseAndRateFirstRep().hasRateRatio())
                .findFirst()
                .orElseThrow();
        Dosage.DosageDoseAndRateComponent doseAndRate = dosageWithRateRatio.getDoseAndRateFirstRep();
        Assert.assertTrue(doseAndRate.hasRateRatio());
        Assert.assertEquals("150.0", doseAndRate.getRateRatio().getNumerator().getValue().toPlainString());
        Assert.assertEquals("mg", doseAndRate.getRateRatio().getNumerator().getUnit());
        Assert.assertEquals("3.0", doseAndRate.getRateRatio().getDenominator().getValue().toPlainString());
        Assert.assertEquals("h", doseAndRate.getRateRatio().getDenominator().getUnit());
    }


}
