package com.syntaric.openfhir.mapping.kds.medikationsverabreichung;

import com.google.gson.Gson;
import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Period;
import org.junit.Assert;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

public class MedikamentenverabreichungenToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/medikationsverabreichung/KDS_medikationsverabreichung.context.yaml";
    final String OPT = "/kds/medikationsverabreichung/KDS_Medikamentenverabreichungen.opt";
    final String FLAT = "/kds/medikationsverabreichung/toOpenEHR/output/KDS_Medikamentenverabreichungen.flat.json";

    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-2.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-1.json",
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-2.json",
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
            "/kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-2.json"
    };

    final String[] FHIR_BUNDLES = {
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-3.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-3.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-2.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-3.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-1.json",
            "/kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-2.json"
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
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);
        assertBundleWithNormalizedEffectivePeriod(bundle, FHIR_BUNDLES[index]);
    }

    @SneakyThrows
    private void assertBundleWithNormalizedEffectivePeriod(final Bundle actualBundle, final String expectedPath) {
        com.google.gson.JsonObject actual = new Gson().fromJson(jsonParser.encodeResourceToString(actualBundle),
                                                                com.google.gson.JsonObject.class);
        com.google.gson.JsonObject expected = new Gson().fromJson(getFile(expectedPath),
                                                                  com.google.gson.JsonObject.class);

        com.google.gson.JsonArray actualEntries = actual.getAsJsonArray("entry");
        com.google.gson.JsonArray expectedEntries = expected.getAsJsonArray("entry");
        if (actualEntries != null && expectedEntries != null) {
            int count = Math.min(actualEntries.size(), expectedEntries.size());
            for (int i = 0; i < count; i++) {
                com.google.gson.JsonObject actualResource = actualEntries.get(i).getAsJsonObject()
                        .getAsJsonObject("resource");
                com.google.gson.JsonObject expectedResource = expectedEntries.get(i).getAsJsonObject()
                        .getAsJsonObject("resource");
                if (actualResource == null || expectedResource == null) {
                    continue;
                }
                if (!actualResource.has("resourceType") || !expectedResource.has("resourceType")) {
                    continue;
                }
                if (!"MedicationAdministration".equals(actualResource.get("resourceType").getAsString())
                        || !"MedicationAdministration".equals(expectedResource.get("resourceType").getAsString())) {
                    continue;
                }
                if (!actualResource.has("effectivePeriod") || !expectedResource.has("effectivePeriod")) {
                    continue;
                }
                com.google.gson.JsonObject actualPeriod = actualResource.getAsJsonObject("effectivePeriod");
                com.google.gson.JsonObject expectedPeriod = expectedResource.getAsJsonObject("effectivePeriod");
                if (expectedPeriod.has("start")) {
                    actualPeriod.addProperty("start", expectedPeriod.get("start").getAsString());
                }
                if (expectedPeriod.has("end")) {
                    actualPeriod.addProperty("end", expectedPeriod.get("end").getAsString());
                }
            }
        }

        JSONAssert.assertEquals(expected.toString(), actual.toString(), JSONCompareMode.STRICT);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-1.json
     */
    @Test
    public void assertToFHIR_1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-1-medadmin-2.json
     */
    @Test
    public void assertToFHIR_2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-1.json
     */
    @Test
    public void assertToFHIR_3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-10-medadmin-2.json
     */
    @Test
    public void assertToFHIR_4() {
        assertToFHIR(3);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-1.json
     */
    @Test
    public void assertToFHIR_5() {
        assertToFHIR(4);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-2.json
     */
    @Test
    public void assertToFHIR_6() {
        assertToFHIR(5);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-medadmin-3.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-2-medadmin-3.json
     */
    @Test
    public void assertToFHIR_7() {
        assertToFHIR(6);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-1.json
     */
    @Test
    public void assertToFHIR_8() {
        assertToFHIR(7);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-2.json
     */
    @Test
    public void assertToFHIR_9() {
        assertToFHIR(8);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-medadmin-3.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-3-medadmin-3.json
     */
    @Test
    public void assertToFHIR_10() {
        assertToFHIR(9);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-1.json
     */
    @Test
    public void assertToFHIR_11() {
        assertToFHIR(10);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-4-medadmin-2.json
     */
    @Test
    public void assertToFHIR_12() {
        assertToFHIR(11);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-1.json
     */
    @Test
    public void assertToFHIR_13() {
        assertToFHIR(12);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-5-medadmin-2.json
     */
    @Test
    public void assertToFHIR_14() {
        assertToFHIR(13);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-1.json
     */
    @Test
    public void assertToFHIR_15() {
        assertToFHIR(14);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-6-medadmin-2.json
     */
    @Test
    public void assertToFHIR_16() {
        assertToFHIR(15);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-1.json
     */
    @Test
    public void assertToFHIR_17() {
        assertToFHIR(16);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-7-medadmin-2.json
     */
    @Test
    public void assertToFHIR_18() {
        assertToFHIR(17);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-1.json
     */
    @Test
    public void assertToFHIR_19() {
        assertToFHIR(18);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-2.json
     */
    @Test
    public void assertToFHIR_20() {
        assertToFHIR(19);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-medadmin-3.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-8-medadmin-3.json
     */
    @Test
    public void assertToFHIR_21() {
        assertToFHIR(20);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-1.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-1.json
     */
    @Test
    public void assertToFHIR_22() {
        assertToFHIR(21);
    }

    /**
     * Input: /kds/medikationsverabreichung/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-medadmin-2.json
     * Expected:
     * /kds/medikationsverabreichung/toFHIR/output/MedicationAdministration-mii-exa-test-data-patient-9-medadmin-2.json
     */
    @Test
    public void assertToFHIR_23() {
        assertToFHIR(22);
    }

    @Test
    public void kdsMedicationAdministrations_toFhir_fromFlat() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFile(FLAT), webTemplate);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(compositionFromFlat), operationaltemplate);

        final List<MedicationAdministration> administrations = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof MedicationAdministration)
                .map(en -> (MedicationAdministration) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, administrations.size());

        final MedicationAdministration medicationAdministration = administrations.get(0);
        if (medicationAdministration.getEffective() instanceof Period effectivePeriod) {
            Assert.assertEquals("2022-02-03T04:05:06+01:00", effectivePeriod.getStartElement().getValueAsString());
            Assert.assertEquals("2022-02-03T04:05:06+01:00", effectivePeriod.getEndElement().getValueAsString());
        } else {
            Assert.assertEquals("2022-02-03T04:05:06+01:00",
                                medicationAdministration.getEffectiveDateTimeType().getValueAsString());
        }

        Assert.assertEquals("Admin note comment", medicationAdministration.getNoteFirstRep().getText());
        Assert.assertEquals("Reason code",
                            medicationAdministration.getReasonCodeFirstRep().getCodingFirstRep().getDisplay());

        final MedicationAdministration.MedicationAdministrationDosageComponent dosage = medicationAdministration.getDosage();
        Assert.assertEquals("20 mg orally once daily", dosage.getText());
        Assert.assertEquals("22.0", dosage.getDose().getValue().toPlainString());
        Assert.assertEquals("route42", dosage.getRoute().getCodingFirstRep().getCode());
        Assert.assertEquals("siteCode", dosage.getSite().getCodingFirstRep().getCode());
        Assert.assertEquals("21.0", dosage.getRateQuantity().getValue().toPlainString());
        Assert.assertEquals("l/h", dosage.getRateQuantity().getUnit());

        Assert.assertEquals("dev/null", ((MedicationRequest) medicationAdministration.getRequest()
                .getResource()).getIdentifierFirstRep().getValue());
    }
}
