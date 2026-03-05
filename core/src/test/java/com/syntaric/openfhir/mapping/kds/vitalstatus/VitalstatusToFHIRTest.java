package com.syntaric.openfhir.mapping.kds.vitalstatus;

import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

public class VitalstatusToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/vitalstatus/KDS_Vitalstatus.context.yaml";
    final String OPT = "/kds/vitalstatus/KDS_Vitalstatus.opt";

    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-vitalstatus-1.json",
            "/kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-vitalstatus-1.json"
    };

    final String[] FHIR_BUNDLES = {
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-1-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-2-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-5-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-6-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-7-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-8-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-9-vitalstatus-1.json",
            "/kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-10-vitalstatus-1.json"
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
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLES[index]);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-1-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-2-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-5-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-6-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_4() {
        assertToFHIR(3);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-7-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_5() {
        assertToFHIR(4);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-8-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_6() {
        assertToFHIR(5);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-9-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_7() {
        assertToFHIR(6);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-vitalstatus-1.json
     * Expected: /kds/vitalstatus/toFHIR/output/Observation-mii-exa-test-data-patient-10-vitalstatus-1.json
     */
    @Test
    public void assertToFHIR_8() {
        assertToFHIR(7);
    }

    /**
     * Input: /kds/vitalstatus/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-vitalstatus-1.json
     */
    @SneakyThrows
    @Test
    public void assertToFHIRDetailedFields() {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[0]),
                Composition.class);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);

        final List<Observation> observations = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Observation)
                .map(en -> (Observation) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, observations.size());
        final Observation observation = observations.get(0);
        Assert.assertEquals("67162-8", observation.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("survey", observation.getCategoryFirstRep().getCodingFirstRep().getCode());
        Assert.assertEquals("T", observation.getValueCodeableConcept().getCodingFirstRep().getCode());
    }
}
