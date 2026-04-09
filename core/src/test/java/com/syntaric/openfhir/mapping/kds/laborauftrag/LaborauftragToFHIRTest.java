package com.syntaric.openfhir.mapping.kds.laborauftrag;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.Assert;
import org.junit.Test;

public class LaborauftragToFHIRTest extends KdsGenericTest {


    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING =
            "/kds/core/projects/org.highmed/KDS/laborauftrag/KDS_laborauftrag.context.yaml";
    final String OPT =
            "/kds/laborauftrag/KDS_Laborauftrag.opt";

    final String FLAT = "/kds/laborauftrag/toOpenEHR/output/KDS_Laborauftrag.flat.json";
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

    final String[] FHIR_BUNDLES = {
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-1-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-2-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-3-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-4-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-5-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-6-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-7-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-8-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-9-labrequest-1.json",
            "/kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-10-labrequest-1.json"
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


    @SneakyThrows
    private void assertToFHIR(int index) {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[index]),
                                                                                Composition.class);
        final Bundle bundle =
                toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLES[index]);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-1-labrequest-1.json
     */
    @Test
    public void assertToFHIR1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-2-labrequest-1.json
     */
    @Test
    public void assertToFHIR2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-3-labrequest-1.json
     */
    @Test
    public void assertToFHIR3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-4-labrequest-1.json
     */
    @Test
    public void assertToFHIR4() {
        assertToFHIR(3);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-5-labrequest-1.json
     */
    @Test
    public void assertToFHIR5() {
        assertToFHIR(4);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-6-labrequest-1.json
     */
    @Test
    public void assertToFHIR6() {
        assertToFHIR(5);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-7-labrequest-1.json
     */
    @Test
    public void assertToFHIR7() {
        assertToFHIR(6);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-8-labrequest-1.json
     */
    @Test
    public void assertToFHIR8() {
        assertToFHIR(7);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-9-labrequest-1.json
     */
    @Test
    public void assertToFHIR9() {
        assertToFHIR(8);
    }

    /**
     * Input: /kds/laborauftrag/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-labrequest-1.json
     * Expected: /kds/laborauftrag/toFHIR/output/ServiceRequest-mii-exa-test-data-patient-10-labrequest-1.json
     */
    @Test
    public void assertToFHIR10() {
        assertToFHIR(9);
    }

    @Test
    public void toFhir() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(getFile(FLAT),
                                                                                     new OPTParser(
                                                                                             operationaltemplate).parse());
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);
        final List<Bundle.BundleEntryComponent> allServiceRequests = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof ServiceRequest).collect(Collectors.toList());
        assertEquals(1, allServiceRequests.size());

        final ServiceRequest serviceRequest = (ServiceRequest) allServiceRequests.get(0).getResource();

        assertServiceRequest(serviceRequest);
    }

    private void assertServiceRequest(final ServiceRequest serviceRequest) {

        //  - name: "identifier"
        Assert.assertEquals("Medical record identifier", serviceRequest.getIdentifierFirstRep().getValue());

        //  - name: "status"
        Assert.assertEquals("completed", serviceRequest.getStatusElement().getValueAsString());

        //  - name: "code"
        Assert.assertEquals("2345-7", serviceRequest.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("Blood Glucose Test", serviceRequest.getCode().getText());

        //  - name: "category"
        Assert.assertEquals("laboratory", serviceRequest.getCategoryFirstRep().getCodingFirstRep().getCode());

        //  - name: "intent"
        Assert.assertEquals("order", serviceRequest.getIntentElement().getValueAsString());

        //  - name: "note"
        Assert.assertEquals("Sample collected in the morning.", serviceRequest.getNoteFirstRep().getText());

        //  - name: "organisation"
        //  - name: "org name"
        //  - name: "org id"
        final Organization org = (Organization) serviceRequest.getRequester().getResource();
        Assert.assertEquals("Einsender name", org.getName());
        Assert.assertEquals("Example Hospital", org.getIdentifierFirstRep().getValue());

        //  - name: "specimen"
        final List<Reference> specimenReferences = serviceRequest.getSpecimen();
        Assert.assertEquals(2, specimenReferences.size());
        final List<Specimen> specimens = specimenReferences.stream().map(spec -> (Specimen) spec.getResource())
                .toList();

        //  - name: "specimen identifier"
        //  - name: "specimen collection date time"
        //  - name: "specimen collector"
        final Specimen specimen1 = specimens.get(0);
        Assert.assertEquals("spec1", specimen1.getAccessionIdentifier().getValue());
//        Assert.assertEquals("2022-02-03T04:05:06+01:00",
//                            specimen1.getCollection().getCollectedPeriod().getStartElement().getValueAsString());
        Assert.assertEquals("probenehmers_id1", specimen1.getCollection().getCollector().getIdentifier().getValue());

        final Specimen specimen2 = specimens.get(1);
        Assert.assertEquals("spec2", specimen2.getAccessionIdentifier().getValue());
//        Assert.assertEquals("3022-02-03T04:05:06+01:00",
//                            specimen2.getCollection().getCollectedDateTimeType().getValueAsString());
        Assert.assertEquals("probenehmers_id2", specimen2.getCollection().getCollector().getIdentifier().getValue());

    }

}
