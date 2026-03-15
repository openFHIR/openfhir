package com.syntaric.openfhir.mapping.kds.studienteilnahme;

import static org.junit.Assert.assertEquals;

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
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.DateTimeType;
import org.junit.Assert;
import org.junit.Test;

public class StudienteilnahmeToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/studienteilnahme/studienteilnahme.context.yaml";
    final String OPT = "/kds/studienteilnahme/Studienteilnahme.opt";
    final String FLAT = "/kds/studienteilnahme/toOpenEHR/output/studienteilnahme.flat.json";

    final String OPENEHR_COMPOSITION_BUNDLE = "/kds/studienteilnahme/toOpenEHR/output/Composition-studienteilnahme_bundle.json";
    final String FHIR_BUNDLE = "/kds/studienteilnahme/toFHIR/output/Consent-studienteilnahme_bundle.json";

    final String[] OPENEHR_COMPOSITIONS = {
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

    final String[] FHIR_CONSENTS = {
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-1-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-2-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-3-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-4-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-5-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-6-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-7-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-8-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-9-consent-1.json",
            "/kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-10-consent-1.json"
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
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-studienteilnahme_bundle.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-studienteilnahme_bundle.json
     */
    @SneakyThrows
    @Test
    public void assertToFHIRBundle() {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITION_BUNDLE), Composition.class);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLE);
    }

    @SneakyThrows
    private void assertToFHIR(int index) {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[index]), Composition.class);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_CONSENTS[index]);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-1-consent-1.json
     */
    @Test
    public void assertToFHIR1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-2-consent-1.json
     */
    @Test
    public void assertToFHIR2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-3-consent-1.json
     */
    @Test
    public void assertToFHIR3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-4-consent-1.json
     */
    @Test
    public void assertToFHIR4() {
        assertToFHIR(3);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-5-consent-1.json
     */
    @Test
    public void assertToFHIR5() {
        assertToFHIR(4);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-6-consent-1.json
     */
    @Test
    public void assertToFHIR6() {
        assertToFHIR(5);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-7-consent-1.json
     */
    @Test
    public void assertToFHIR7() {
        assertToFHIR(6);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-8-consent-1.json
     */
    @Test
    public void assertToFHIR8() {
        assertToFHIR(7);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-9-consent-1.json
     */
    @Test
    public void assertToFHIR9() {
        assertToFHIR(8);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-consent-1.json
     * Expected: /kds/studienteilnahme/toFHIR/output/Consent-mii-exa-test-data-patient-10-consent-1.json
     */
    @Test
    public void assertToFHIR10() {
        assertToFHIR(9);
    }

    /**
     * Input: /kds/studienteilnahme/toOpenEHR/output/studienteilnahme.flat.json
     */
    @Test
    public void assertToFHIRFlatFields() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFile(FLAT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(compositionFromFlat), operationaltemplate);
        final List<Bundle.BundleEntryComponent> allConsents = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Consent)
                .collect(Collectors.toList());

        assertEquals(1, allConsents.size());

        final Consent consent = (Consent) allConsents.get(0).getResource();

        final DateTimeType periodStart = consent.getProvision().getPeriod().getStartElement();
        final DateTimeType periodEnd = consent.getProvision().getPeriod().getEndElement();
        Assert.assertEquals("2020-02-03T04:05:06+01:00", periodStart.getValueAsString());
        Assert.assertEquals("2024-02-03T04:05:06+01:00", periodEnd.getValueAsString());
    }
}
