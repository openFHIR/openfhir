package com.syntaric.openfhir.mapping.kds.laborbericht;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Assert;
import org.junit.Test;

public class LaborberichtToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/laborbericht/KDS_laborbericht.context.yaml";
    final String HELPER_LOCATION = "/kds/laborbericht/";
    final String OPT = "/kds/laborbericht/KDS_Laborbericht.opt";
    final String BUNDLE = "/kds/laborbericht/toOpenEHR/input/KDS_Laborbericht_bundle.json";
    final String[] FHIR_DIAGNOSTIC_REPORTS = {
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-1-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-2-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-3-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-4-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-5-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-6-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-7-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-8-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-9-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-10-labreport-1.json"
    };

    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-labreport-1.json",
            "/kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-labreport-1.json"
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
    private void assertToOpenEHR(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_DIAGNOSTIC_REPORTS[index]),
                                              operationaltemplate);
        final String expectedCompositionLocation = OPENEHR_COMPOSITIONS[index];
        final String expectedComposition = IOUtils.toString(getClass().getResourceAsStream(expectedCompositionLocation));

        final String expectedFlatPath = new FlatJsonMarshaller().toFlatJson(
                new CanonicalJson().unmarshal(expectedComposition), webTemplate);
        final String createdFlatPath = new FlatJsonMarshaller().toFlatJson(composition, webTemplate);

        standardsAsserter.assertComposition(composition, expectedCompositionLocation, operationaltemplate);
    }

    private void assertToOpenEHRWihtoutOPTVal(int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_DIAGNOSTIC_REPORTS[index]),
                                              operationaltemplate);
        standardsAsserter.assertCompositionWihtoutOPTValidataion(composition, OPENEHR_COMPOSITIONS[index],
                                                                 operationaltemplate);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-1-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-labreport-1.json
     */
    @Test
    public void assertToOpenEHR1() {
        assertToOpenEHRWihtoutOPTVal(0); //has null flavour instead of value=
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-2-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-labreport-1.json
     */
    @Test
    public void assertToOpenEHR2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-3-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-labreport-1.json
     */
    @Test
    public void assertToOpenEHR3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-4-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-labreport-1.json
     */
    @Test
    public void assertToOpenEHR4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-5-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-labreport-1.json
     */
    @Test
    public void assertToOpenEHR5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-6-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-labreport-1.json
     */
    @Test
    public void assertToOpenEHR6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-7-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-labreport-1.json
     */
    @Test
    public void assertToOpenEHR7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-8-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-labreport-1.json
     */
    @Test
    public void assertToOpenEHR8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-9-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-labreport-1.json
     */
    @Test
    public void assertToOpenEHR9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/input/DiagnosticReport-mii-exa-test-data-patient-10-labreport-1.json
     * Expected: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-labreport-1.json
     */
    @Test
    public void assertToOpenEHR10() {
        assertToOpenEHR(9);
    }

    @Test
    public void toOpenEhr() {
        final Bundle testBundle = getTestBundle(BUNDLE);
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(context, testBundle, operationaltemplate);

        Assert.assertEquals("registered",
                            jsonObject.getAsJsonPrimitive("laborbericht/context/status|code").getAsString());
        Assert.assertEquals("Normal blood count",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/any_event:0/conclusion")
                                    .getAsString());
        Assert.assertEquals("2024-08-22T10:30:00",
                            jsonObject.getAsJsonPrimitive("laborbericht/laborbefund/any_event:0/time").getAsString());
        Assert.assertEquals("SP-987654", jsonObject.getAsJsonPrimitive(
                        "laborbericht/laborbefund/any_event:0/probenmaterial:0/external_identifier/identifier_value|id")
                .getAsString());
        Assert.assertEquals("2024-08-24T11:00:00", jsonObject.getAsJsonPrimitive(
                        "laborbericht/laborbefund/any_event:0/probenmaterial:0/collection_date_time/date_time_value")
                .getAsString());
        Assert.assertEquals("1234567", jsonObject.getAsJsonPrimitive(
                        "laborbericht/laborbefund/any_event:0/probenmaterial:0/specimen_collector_identifier|id")
                .getAsString());
        Assert.assertEquals("122555007",
                            jsonObject.getAsJsonPrimitive(
                                            "laborbericht/laborbefund/any_event:0/probenmaterial:0/specimen_type|code")
                                    .getAsString());
        Assert.assertEquals("http://snomed.info/sct", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/probenmaterial:0/specimen_type|terminology").getAsString());
        Assert.assertEquals("Venous blood specimen",
                            jsonObject.getAsJsonPrimitive(
                                            "laborbericht/laborbefund/any_event:0/probenmaterial:0/specimen_type|value")
                                    .getAsString());
        Assert.assertEquals("Sample collected in the morning.",
                            jsonObject.getAsJsonPrimitive(
                                            "laborbericht/laborbefund/any_event:0/probenmaterial:0/comment")
                                    .getAsString());
        Assert.assertEquals("2022-02-03T05:05:06", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/probenmaterial:0/date_time_received").getAsString());
        Assert.assertEquals("at0062", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/probenmaterial:0/adequacy_for_testing|code").getAsString());
        Assert.assertEquals("2022-02-03T05:05:06", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/result_status_time").getAsString());
        Assert.assertEquals("7.4", jsonObject.getAsJsonPrimitive(
                        "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/messwert:0/quantity_value|magnitude")
                .getAsString());
        Assert.assertEquals("g/dL", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/messwert:0/quantity_value|unit").getAsString());
        Assert.assertEquals("718-7", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/analyte_name|code").getAsString());
        Assert.assertEquals("http://loinc.org", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/analyte_name|terminology").getAsString());
        Assert.assertEquals("Hemoglobin [Mass/volume] in Blood", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/analyte_name|value").getAsString());
        Assert.assertEquals("H", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/interpretation|code").getAsString());
        Assert.assertEquals("http://hl7.org/fhir/ValueSet/observation-interpretation", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/interpretation|terminology").getAsString());
        Assert.assertEquals("Interpretation description", jsonObject.getAsJsonPrimitive(
                "laborbericht/laborbefund/any_event:0/pro_laboranalyt:0/interpretation|value").getAsString());
        Assert.assertEquals("FILL-12345",
                            jsonObject.getAsJsonPrimitive("laborbericht/context/id").getAsString());
    }

}
