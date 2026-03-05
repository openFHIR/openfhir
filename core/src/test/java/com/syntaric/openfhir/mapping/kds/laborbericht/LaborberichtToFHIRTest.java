package com.syntaric.openfhir.mapping.kds.laborbericht;

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
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Specimen;
import org.junit.Assert;
import org.junit.Test;

public class LaborberichtToFHIRTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/laborbericht/KDS_laborbericht.context.yaml";
    final String HELPER_LOCATION = "/kds/laborbericht/";
    final String OPT = "/kds/laborbericht/KDS_Laborbericht.opt";
    final String FLAT = "/kds/laborbericht/toOpenEHR/output/KDS_Laborbericht.flat.json";
    final String OPENEHR_COMPOSITION_BUNDLE = "/kds/laborbericht/toOpenEHR/output/Composition_Laborbericht_bundle.json";
    final String FHIR_BUNDLE_BUNDLE = "/kds/laborbericht/toFHIR/output/KDS_Laborbericht_bundle.json";

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

    final String[] FHIR_BUNDLES = {
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-1-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-2-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-3-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-4-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-5-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-6-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-7-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-8-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-9-labreport-1.json",
            "/kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-10-labreport-1.json"
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
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-KDS_Laborbericht_bundle.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-KDS_Laborbericht_bundle.json
     */
    @SneakyThrows
    @Test
    public void assertToFHIRBundle() {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITION_BUNDLE),
                                                                                Composition.class);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLE_BUNDLE);
    }

    @SneakyThrows
    private void assertToFHIR(int index) {
        final Composition composition = JacksonUtil.getObjectMapper().readValue(getFile(OPENEHR_COMPOSITIONS[index]),
                                                                                Composition.class);
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(composition), operationaltemplate);
        standardsAsserter.assertBundle(bundle, FHIR_BUNDLES[index]);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-1-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-1-labreport-1.json
     */
    @Test
    public void assertToFHIR1() {
        assertToFHIR(0);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-2-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-2-labreport-1.json
     */
    @Test
    public void assertToFHIR2() {
        assertToFHIR(1);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-3-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-3-labreport-1.json
     */
    @Test
    public void assertToFHIR3() {
        assertToFHIR(2);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-4-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-4-labreport-1.json
     */
    @Test
    public void assertToFHIR4() {
        assertToFHIR(3);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-5-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-5-labreport-1.json
     */
    @Test
    public void assertToFHIR5() {
        assertToFHIR(4);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-6-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-6-labreport-1.json
     */
    @Test
    public void assertToFHIR6() {
        assertToFHIR(5);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-7-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-7-labreport-1.json
     */
    @Test
    public void assertToFHIR7() {
        assertToFHIR(6);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-8-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-8-labreport-1.json
     */
    @Test
    public void assertToFHIR8() {
        assertToFHIR(7);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-9-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-9-labreport-1.json
     */
    @Test
    public void assertToFHIR9() {
        assertToFHIR(8);
    }

    /**
     * Input: /kds/laborbericht/toOpenEHR/output/Composition-mii-exa-test-data-patient-10-labreport-1.json
     * Expected: /kds/laborbericht/toFHIR/output/DiagnosticReport-mii-exa-test-data-patient-10-labreport-1.json
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
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(compositionFromFlat), operationaltemplate);
        final List<Bundle.BundleEntryComponent> allDiagnosticReports = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof DiagnosticReport).collect(Collectors.toList());
        assertEquals(1, allDiagnosticReports.size());

        final DiagnosticReport diagnosticReport = (DiagnosticReport) allDiagnosticReports.get(0).getResource();

        // - name: "Category"
        assertEquals(1, diagnosticReport.getCategory().size());

        //  - name: "Status"
        assertEquals("at0107", diagnosticReport.getStatusElement().getValueAsString());

        //  - name: "Conclusion"
        assertEquals("Normal blood count", diagnosticReport.getConclusion());

        // - name: "issued"

        // - name: "berichtId"
        assertEquals(1, diagnosticReport.getIdentifierFirstRep().getType().getCoding().size());
        assertEquals("FILL", diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
        assertEquals("http://terminology.hl7.org/CodeSystem/v2-0203",
                     diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getSystem());
        assertEquals("bericht_id", diagnosticReport.getIdentifierFirstRep().getValue());

        // Assert Specimen - name: "specimen"
        Specimen specimen = (Specimen) diagnosticReport.getSpecimenFirstRep().getResource();

        // - name: "identifier"
        assertEquals("SP-987654", specimen.getIdentifierFirstRep().getValue());

        //  - name: "collector"
        assertEquals("collectorId", specimen.getCollection().getCollector().getIdentifier().getValue());

        //  - name: "specimen type"
        assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());

        // - name: "type"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());
        Assert.assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());

        //   - name: "note"
        assertEquals("Sample collected in the morning.", specimen.getNoteFirstRep().getText());

        // - name: "descriptionOfSpecimen"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0493' available",
                specimen.getConditionFirstRep().getText());
        Assert.assertEquals("conditionCode", specimen.getConditionFirstRep().getCodingFirstRep().getCode());

        // - name: "identifierOfSpecimen"
        assertEquals("identifierOfSpecimen", specimen.getAccessionIdentifier().getValue());

        // - name: "dateReceived"
        assertEquals("2022-02-03T04:05:06+01:00", specimen.getReceivedTimeElement().getValueAsString());

        // specimen - name: "status"
        assertEquals("available", specimen.getStatusElement().getValueAsString());

        // basedOn, identifierInReference
        assertEquals("identifikation_der_laboranforderung",
                     diagnosticReport.getBasedOnFirstRep().getIdentifier().getValue());

        // Assert Observation  - name: "result"
        Observation observation = (Observation) diagnosticReport.getResultFirstRep().getResource();

        // - name: "issued"
        Assert.assertEquals("2022-02-03T04:05:06.000+01:00", observation.getIssuedElement().getValueAsString());

        //     - name: "analyteMeasurement"
        assertEquals(7.4, observation.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("mm", observation.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("718-7", observation.getCode().getCodingFirstRep().getCode());
        assertEquals(
                "//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips",
                observation.getCode().getCodingFirstRep().getSystem());
        assertEquals("Hemoglobin [Mass/volume] in Blood", observation.getCode().getText());

        // - name: "interpretation"
        assertEquals("142", observation.getInterpretationFirstRep().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/observation-interpretation' available",
                observation.getInterpretationFirstRep().getText());
    }

    @Test
    public void toFhir_multiples() {
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFile("/kds/laborbericht/toOpenEHR/output/KDS_Laborbericht_multiples.flat.json"),
                new OPTParser(operationaltemplate).parse());
        final Bundle bundle = toFhir.compositionsToFhir(context, List.of(compositionFromFlat), operationaltemplate);
        final List<Bundle.BundleEntryComponent> allDiagnosticReports = bundle.getEntry().stream()
                .filter(en -> en.getResource() instanceof DiagnosticReport).collect(Collectors.toList());
        assertEquals(1, allDiagnosticReports.size());

        final DiagnosticReport diagnosticReport = (DiagnosticReport) allDiagnosticReports.get(0).getResource();

        Assert.assertEquals(2, diagnosticReport.getSpecimen().size());
        Assert.assertEquals(2, diagnosticReport.getResult().size());

        // - name: "Category"
        assertEquals(1, diagnosticReport.getCategory().size());
        //  - name: "Status"
        assertEquals("at0107", diagnosticReport.getStatusElement().getValueAsString());

        //  - name: "Conclusion"
        assertEquals("Normal blood count", diagnosticReport.getConclusion());

        // - name: "issued"

        // - name: "berichtId"
        assertEquals(1, diagnosticReport.getIdentifierFirstRep().getType().getCoding().size());
        assertEquals("FILL", diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getCode());
        assertEquals("http://terminology.hl7.org/CodeSystem/v2-0203",
                     diagnosticReport.getIdentifierFirstRep().getType().getCodingFirstRep().getSystem());
        assertEquals("bericht_id", diagnosticReport.getIdentifierFirstRep().getValue());

        // Assert Specimen - name: "specimen"
        Specimen specimen = (Specimen) diagnosticReport.getSpecimenFirstRep().getResource();

        // - name: "identifier"
        assertEquals("SP-987654", specimen.getIdentifierFirstRep().getValue());

        //  - name: "collector"
        assertEquals("collectorId", specimen.getCollection().getCollector().getIdentifier().getValue());

        //  - name: "specimen type"
        assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());

        // - name: "type"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0487' available",
                specimen.getType().getText());
        Assert.assertEquals("probenartcode", specimen.getType().getCodingFirstRep().getCode());

        //   - name: "note"
        assertEquals("Sample collected in the morning.", specimen.getNoteFirstRep().getText());

        // - name: "descriptionOfSpecimen"
        Assert.assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://terminology.hl7.org/ValueSet/v2-0493' available",
                specimen.getConditionFirstRep().getText());
        Assert.assertEquals("conditionCode", specimen.getConditionFirstRep().getCodingFirstRep().getCode());

        // - name: "identifierOfSpecimen"
        assertEquals("identifierOfSpecimen", specimen.getAccessionIdentifier().getValue());

        // - name: "dateReceived"
        assertEquals("2022-02-03T04:05:06+01:00", specimen.getReceivedTimeElement().getValueAsString());

        // specimen - name: "status"
        assertEquals("available", specimen.getStatusElement().getValueAsString());

        Specimen specimen1 = (Specimen) diagnosticReport.getSpecimen().get(1).getResource();

        // - name: "identifier"
        assertEquals("1_SP-987654", specimen1.getIdentifierFirstRep().getValue());

        //  - name: "collector"
        assertEquals("1_collectorId", specimen1.getCollection().getCollector().getIdentifier().getValue());

        //  - name: "specimen type"
        assertEquals("1_probenartcode", specimen1.getType().getCodingFirstRep().getCode());

        // - name: "type"
        Assert.assertEquals("1_probenartcode", specimen1.getType().getCodingFirstRep().getCode());

        //   - name: "note"
        assertEquals("1_Sample collected in the morning.", specimen1.getNoteFirstRep().getText());

        // - name: "descriptionOfSpecimen"
        Assert.assertEquals("1_conditionCode", specimen1.getConditionFirstRep().getCodingFirstRep().getCode());

        // - name: "identifierOfSpecimen"
        assertEquals("1_identifierOfSpecimen", specimen1.getAccessionIdentifier().getValue());

        // - name: "dateReceived"
        assertEquals("3022-02-03T04:05:06+01:00", specimen1.getReceivedTimeElement().getValueAsString());

        // specimen - name: "status"
        assertEquals("unsatisfactory", specimen1.getStatusElement().getValueAsString());

        // basedOn, identifierInReference
        assertEquals("identifikation_der_laboranforderung",
                     diagnosticReport.getBasedOnFirstRep().getIdentifier().getValue());

        // Assert Observation  - name: "result"
        Observation observation = (Observation) diagnosticReport.getResultFirstRep().getResource();

        // - name: "issued"
        Assert.assertEquals("2022-02-03T04:05:06.000+01:00", observation.getIssuedElement().getValueAsString());

        //     - name: "analyteMeasurement"
        assertEquals(7.4, observation.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("mm", observation.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("718-7", observation.getCode().getCodingFirstRep().getCode());
        assertEquals(
                "//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/uv/ips/ValueSet/results-laboratory-observations-uv-ips",
                observation.getCode().getCodingFirstRep().getSystem());
        assertEquals("Hemoglobin [Mass/volume] in Blood", observation.getCode().getText());

        // - name: "interpretation"
        assertEquals("142", observation.getInterpretationFirstRep().getCodingFirstRep().getCode());
        assertEquals(
                "No example for termínology '//fhir.hl7.org/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/observation-interpretation' available",
                observation.getInterpretationFirstRep().getText());

        Observation observation1 = (Observation) diagnosticReport.getResult().get(1).getResource();

        // - name: "issued"
        Assert.assertEquals("3022-02-03T04:05:06.000+01:00", observation1.getIssuedElement().getValueAsString());

        //     - name: "analyteMeasurement"
        assertEquals(8.4, observation1.getValueQuantity().getValue().doubleValue(), 0);
        assertEquals("1_mm", observation1.getValueQuantity().getUnit());

        // laboryte name
        assertEquals("1_718-7", observation1.getCode().getCodingFirstRep().getCode());
        assertEquals("1_Hemoglobin [Mass/volume] in Blood", observation1.getCode().getText());

        // - name: "interpretation"
        assertEquals("1_142", observation1.getInterpretationFirstRep().getCodingFirstRep().getCode());
    }

}
