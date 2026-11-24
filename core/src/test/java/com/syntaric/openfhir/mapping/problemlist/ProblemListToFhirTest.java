package com.syntaric.openfhir.mapping.problemlist;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class ProblemListToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/problemlist/";
    final String CONTEXT_MAPPING = "/problemlist/problem_list_nl.context.yml";
    final String HELPER_LOCATION = "/problemlist/";
    final String OPT = "ProblemList.opt";
    final String FLAT_TEXT_VALUE = "flat_textvalue.json";
    final String FLAT_CODEABLE_CONCEPT = "flat_codeableconcept.json";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String modelsDir;

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized =  IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate,
                            modelsDir != null ? modelsDir : getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void toFhir_textValue() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_TEXT_VALUE), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);

        final org.hl7.fhir.r4.model.Composition composition = (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();
        final Condition condition = (Condition) getBundleEntry(bundle, composition.getSectionFirstRep().getEntryFirstRep().getReference());
        final Observation observation = (Observation) getBundleEntry(bundle, condition.getEvidenceFirstRep().getDetailFirstRep().getReference());

        Assert.assertEquals("Fine", observation.getValueStringType().getValue());
    }

    @Test
    public void toFhir_codeableConcept() {
        // openEHR to FHIR
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_CODEABLE_CONCEPT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);

        final org.hl7.fhir.r4.model.Composition composition = (org.hl7.fhir.r4.model.Composition) bundle.getEntryFirstRep().getResource();
        final Condition condition = (Condition) getBundleEntry(bundle, composition.getSectionFirstRep().getEntryFirstRep().getReference());
        final Observation observation = (Observation) getBundleEntry(bundle, condition.getEvidenceFirstRep().getDetailFirstRep().getReference());

        final CodeableConcept valueCodeableConcept = observation.getValueCodeableConcept();
        Assert.assertEquals("code", valueCodeableConcept.getCodingFirstRep().getCode());
        Assert.assertEquals("vall", valueCodeableConcept.getCodingFirstRep().getDisplay());
        Assert.assertEquals("term", valueCodeableConcept.getCodingFirstRep().getSystem());
        Assert.assertEquals("vall", valueCodeableConcept.getText());
    }

    /**
     * Analyte result is a choice of far more than DV_CODED_TEXT | DV_TEXT, so the flat format keeps its
     * {@code coded_text_value} leaf; only that exact pair is merged into the element's own id. A coded value written
     * to the bare element path is one the SDK cannot consume. The mapping picks the coded text with {@code type},
     * which narrows the RM type but not the flat path. That {@code type} works around a separate defect: the RM type
     * deduction takes any CodeableConcept for a DV_ORDINAL when the choice offers one. Once that is fixed, it can go.
     */
    @Test
    @SneakyThrows
    public void codeableConceptOnAChoiceRoundTripsToItsCodedTextLeaf() {
        useModelsWithAnalyteResultTyped("CODEABLECONCEPT");
        final Composition compositionFromFlat = new FlatJsonUnmarshaller().unmarshal(
                getFlat(HELPER_LOCATION + FLAT_CODEABLE_CONCEPT), new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(compositionFromFlat), webTemplate);

        final JsonObject flat = toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate);

        final String analyteResult = "problem_list_nl/problem_diagnosis/laboratory_analyte_result/analyte_result:0";
        Assert.assertFalse("bare element path written: " + flat, flat.has(analyteResult + "|code"));
        Assert.assertTrue("no coded_text_value leaf in " + flat, flat.has(analyteResult + "/coded_text_value|code"));
        Assert.assertEquals("code", flat.get(analyteResult + "/coded_text_value|code").getAsString());
        Assert.assertEquals("term", flat.get(analyteResult + "/coded_text_value|terminology").getAsString());
        new FlatJsonUnmarshaller().unmarshal(flat.toString(), webTemplate);
    }

    /** Copies the model mappings with a {@code type} added to the analyte result mapping, and re-initialises. */
    @SneakyThrows
    private void useModelsWithAnalyteResultTyped(final String type) {
        final Path source = Path.of(getClass().getResource(MODEL_MAPPINGS).toURI());
        final Path target = temporaryFolder.newFolder().toPath();
        try (Stream<Path> files = Files.list(source)) {
            for (final Path file : files.filter(f -> f.toString().endsWith(".yml")).toList()) {
                Files.copy(file, target.resolve(file.getFileName()));
            }
        }
        final Path analyte = target.resolve("laboratory_test_analyte.v1.yml");
        final String mapping = Files.readString(analyte, StandardCharsets.UTF_8);
        final String line = "openehr: \"$archetype/items[at0001]\" # Analyte result";
        Assert.assertTrue("analyte result mapping not found", mapping.contains(line));
        Files.writeString(analyte, mapping.replace(line, line + "\r\n      type: \"" + type + "\""),
                          StandardCharsets.UTF_8);
        modelsDir = target.toString();
        init();
    }
}
