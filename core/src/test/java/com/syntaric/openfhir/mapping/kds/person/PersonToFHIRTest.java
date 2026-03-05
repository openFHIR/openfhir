package com.syntaric.openfhir.mapping.kds.person;

import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.Test;

public class PersonToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/person/person.context.yaml";
    final String OPT = "/kds/person/KDS_Person.opt";
    final String OPENEHR_FLAT = "/kds/person/KDS_Person.flat.json";

    final String[] OPENEHR_COMPOSITIONS = {
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

    final String[] FHIR_BUNDLES = {
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-1.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-2.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-3.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-4.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-5.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-6.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-7.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-8.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-9.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-10.json",
            "/kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-11.json"
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
    private void assertToFHIR(final int index) {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[index]),
                Composition.class);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLES[index]);
    }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-1.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-1.json
     */
    @Test
    public void assertToFHIR_1() { assertToFHIR(0); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-2.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-2.json
     */
    @Test
    public void assertToFHIR_2() { assertToFHIR(1); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-3.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-3.json
     */
    @Test
    public void assertToFHIR_3() { assertToFHIR(2); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-4.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-4.json
     */
    @Test
    public void assertToFHIR_4() { assertToFHIR(3); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-5.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-5.json
     */
    @Test
    public void assertToFHIR_5() { assertToFHIR(4); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-6.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-6.json
     */
    @Test
    public void assertToFHIR_6() { assertToFHIR(5); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-7.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-7.json
     */
    @Test
    public void assertToFHIR_7() { assertToFHIR(6); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-8.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-8.json
     */
    @Test
    public void assertToFHIR_8() { assertToFHIR(7); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-9.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-9.json
     */
    @Test
    public void assertToFHIR_9() { assertToFHIR(8); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-10.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-10.json
     */
    @Test
    public void assertToFHIR_10() { assertToFHIR(9); }

    /**
     * Input: /kds/person/toOpenEHR/output/Composition-mii-exa-test-data-patient-11.json
     * Expected: /kds/person/toFHIR/output/Patient-mii-exa-test-data-patient-11.json
     */
    @Test
    public void assertToFHIR_11() { assertToFHIR(10); }
}
