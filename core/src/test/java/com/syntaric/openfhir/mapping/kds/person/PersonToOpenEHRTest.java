package com.syntaric.openfhir.mapping.kds.person;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class PersonToOpenEHRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/person/person.context.yaml";
    final String OPT = "/kds/person/KDS_Person.opt";
    final String LEGACY_INPUT_BUNDLE = "/kds/person/toOpenEHR/input/kds_person_bundle.json";

    final String[] FHIR_INPUTS = {
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-1.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-2.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-3.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-4.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-5.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-6.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-7.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-8.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-9.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-10.json",
            "/kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-11.json"
    };

    final String[] OPENEHR_OUTPUTS = {
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-1.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-2.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-3.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-4.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-5.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-6.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-7.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-8.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-9.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-10.json",
            "/kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-11.json"
    };

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @SneakyThrows
    private void assertToOpenEHR(final int index) {
        final Composition composition =
                toOpenEhr.fhirToCompositionRm(context, getTestBundle(FHIR_INPUTS[index]), operationaltemplate);
        final JSONObject actual = new JSONObject(new CanonicalJson().marshal(composition));
        final JSONObject expected = new JSONObject(getFile(OPENEHR_OUTPUTS[index]));
        removeContextStartTime(actual);
        removeContextStartTime(expected);

        JSONAssert.assertEquals(expected, actual, true);
    }

    private void removeContextStartTime(final JSONObject jsonObject) {
        if (!jsonObject.has("context")) {
            return;
        }
        final JSONObject context = jsonObject.optJSONObject("context");
        if (context == null || !context.has("start_time")) {
            return;
        }
        final JSONObject startTime = context.optJSONObject("start_time");
        if (startTime != null) {
            startTime.remove("value");
        }
    }

    private void assertFlatEqualsIfPresent(final JsonObject jsonObject, final String key, final String expected) {
        if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull()) {
            return;
        }
        Assert.assertEquals(expected, jsonObject.getAsJsonPrimitive(key).getAsString());
    }

    /**
     * Input: /kds/person/kds_person_bundle.json
     * Expected: legacy assertions from former PersonTest.toOpenEhr
     */
    @Test
    public void assertToOpenEHrLegacyFlatDetails() {
        final JsonObject jsonObject = toOpenEhr.fhirToFlatJsonObject(
                context, getTestBundle(LEGACY_INPUT_BUNDLE), operationaltemplate);

        Assert.assertFalse(jsonObject.entrySet().isEmpty());

        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/pid:0|id", "PID987654321");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/geburtsname/vollständiger_name",
                                  "Von Smith");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/geburtsname/namensart|code", "maiden");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/geburtsname/familienname", "Von Smith");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/geburtsname/familienname-nachname", "Smith");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/geburtsname/familienname-namenszusatz",
                                  "Von");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/geburtsname/familienname-vorsatzwort", "zu");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/vollständiger_name", "John Doe");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/namensart|code", "official");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/vorname:0", "John");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/familienname", "John Doe");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/familienname-nachname", "John");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/familienname-namenszusatz", "Doe");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/name/familienname-vorsatzwort", "zu");

        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/gemeindeschlüssel",
                                  "Hamburg");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/bundesland|value",
                                  "Hamburg");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/postleitzahl", "20095");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/stadtteil", "Mitte");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/stadt", "Hamburg");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/land|value", "Germany");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/art|value", "both");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/straße:0", "123 Main St");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/hausnummer:0", "Apt 4B");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/straßenanschrift:0/adresszusatz:0",
                                  "Wohnung 3");

        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/gemeindeschlüssel", "Berlin");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/bundesland|value", "Berlin");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/stadt", "Berlin");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/postleitzahl", "10997");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/stadtteil", "Kreuzberg");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/land|value", "Germany");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/postfach/art|value", "postal");

        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/versicherten_id_gkv|id", "GKV123456789");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/person/versicherungsnummer_pkv|id", "PKV543210987");
        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/daten_zur_geburt/geburtsdatum", "1980-01-01");

        assertFlatEqualsIfPresent(jsonObject, "person/personendaten/kontaktperson/rolle_relationship:0|code",
                                  "emergency");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/kontaktperson/rolle_relationship:0|terminology",
                "http://hl7.org/fhir/ValueSet/contact-relationship");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/kontaktperson/rolle_relationship:0|value",
                "Emergency Contact");

        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/kontaktperson/elektronische_kommunikation:0/daten/text_value",
                "+1-555-1234");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/kontaktperson/elektronische_kommunikation:1/daten/text_value",
                "jane.doe@example.com");

        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/kontaktperson/organisation/namenszeile",
                "Example Health Clinic");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/kontaktperson/organisation/identifier:0/identifier_value|id",
                "ORG-12345");

        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|code",
                "16100001");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|terminology",
                "http://snomed.info/sct");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/angaben_zum_tod/angaben_zum_tod/todesdiagnose|value",
                "Cause of death");
        assertFlatEqualsIfPresent(
                jsonObject,
                "person/personendaten/angaben_zum_tod/angaben_zum_tod/sterbedatum",
                "2024-08-24T02:00:00");

        assertFlatEqualsIfPresent(
                jsonObject,
                "person/vitalstatus/vitalstatus",
                "The patient is recorded Dead. Cause of death is based on the patient's medical history.");
        assertFlatEqualsIfPresent(jsonObject, "person/vitalstatus/fhir_status_der_beobachtung/status", "final");
        assertFlatEqualsIfPresent(jsonObject, "person/vitalstatus/zeitpunkt_der_feststellung", "2024-08-21T16:30:00");

        Assert.assertEquals("male", jsonObject.getAsJsonPrimitive("person/geschlecht/administratives_geschlecht|code")
                .getAsString());
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-1.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-1.json
     */
    @Test
    public void assertToOpenEHR_1() {
        assertToOpenEHR(0);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-2.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-2.json
     */
    @Test
    @Ignore
    public void assertToOpenEHR_2() {
        assertToOpenEHR(1);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-3.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-3.json
     */
    @Test
    public void assertToOpenEHR_3() {
        assertToOpenEHR(2);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-4.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-4.json
     */
    @Test
    public void assertToOpenEHR_4() {
        assertToOpenEHR(3);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-5.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-5.json
     */
    @Test
    public void assertToOpenEHR_5() {
        assertToOpenEHR(4);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-6.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-6.json
     */
    @Test
    public void assertToOpenEHR_6() {
        assertToOpenEHR(5);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-7.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-7.json
     */
    @Test
    public void assertToOpenEHR_7() {
        assertToOpenEHR(6);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-8.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-8.json
     */
    @Test
    public void assertToOpenEHR_8() {
        assertToOpenEHR(7);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-9.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-9.json
     */
    @Test
    public void assertToOpenEHR_9() {
        assertToOpenEHR(8);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-10.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-10.json
     */
    @Test
    public void assertToOpenEHR_10() {
        assertToOpenEHR(9);
    }

    /**
     * Input: /kds/person/toOpenEHR/input/Patient-mii-exa-test-data-patient-11.json
     * Expected: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-11.json
     */
    @Test
    public void assertToOpenEHR_11() {
        assertToOpenEHR(10);
    }

}
