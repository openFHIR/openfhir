package com.syntaric.openfhir.mapping.kds.todesursache;

import com.nedap.archie.json.JacksonUtil;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.junit.Assert;
import org.junit.Test;

public class TodesursacheToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/todesursache/KDS_Todesursache.context.yaml";
    final String OPT = "/kds/todesursache/KDS_Todesursache.opt";

    final String[] OPENEHR_COMPOSITIONS = {
            "/kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-todesursache-1.json",
            "/kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-todesursache-1.json",
            "/kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-todesursache-1.json"
    };

    final String[] FHIR_BUNDLES = {
            "/kds/todesursache/toFHIR/output/Condition-mii-exa-test-data-patient-1-todesursache-1.json",
            "/kds/todesursache/toFHIR/output/Condition-mii-exa-test-data-patient-2-todesursache-1.json",
            "/kds/todesursache/toFHIR/output/Condition-mii-exa-test-data-patient-8-todesursache-1.json"
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
     * Input: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-todesursache-1.json
     * Expected: /kds/todesursache/toFHIR/output/Condition-mii-exa-test-data-patient-1-todesursache-1.json
     */
    @Test
    public void assertToFHIR_1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-todesursache-1.json
     * Expected: /kds/todesursache/toFHIR/output/Condition-mii-exa-test-data-patient-2-todesursache-1.json
     */
    @Test
    public void assertToFHIR_2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-todesursache-1.json
     * Expected: /kds/todesursache/toFHIR/output/Condition-mii-exa-test-data-patient-8-todesursache-1.json
     */
    @Test
    public void assertToFHIR_3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/todesursache/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-todesursache-1.json
     */
    @SneakyThrows
    @Test
    public void assertToFHIRDetailedFields() {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[0]),
                Composition.class);
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);

        final List<Condition> conditions = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof Condition)
                .map(en -> (Condition) en.getResource())
                .collect(Collectors.toList());

        Assert.assertEquals(1, conditions.size());

        final Condition condition = conditions.get(0);
        Assert.assertEquals("active", condition.getClinicalStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("confirmed", condition.getVerificationStatus().getCodingFirstRep().getCode());
        Assert.assertEquals("A15.0", condition.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("Tuberkulose einschließlich ihrer Folgezustände", condition.getNoteFirstRep().getText());
    }
}
