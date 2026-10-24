package com.syntaric.openfhir.mapping.helpers;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.helpers.parser.CodedParser;
import com.syntaric.openfhir.mapping.helpers.parser.FhirValueReaders;
import com.syntaric.openfhir.mapping.helpers.parser.IdentifierParser;
import com.syntaric.openfhir.mapping.helpers.parser.MediaParser;
import com.syntaric.openfhir.mapping.helpers.parser.QuantityParser;
import com.syntaric.openfhir.mapping.helpers.parser.ReferenceParser;
import com.syntaric.openfhir.mapping.helpers.parser.TemporalParser;
import com.syntaric.openfhir.mapping.helpers.parser.TextParser;
import com.syntaric.openfhir.mapping.helpers.parser.ValueToFHIRParser;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.List;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.TemplateDocument;

/**
 * A mapping can pin one attribute of a DV_CODED_TEXT ({@code .../defining_code/code_string},
 * {@code .../defining_code/terminology_id}, {@code .../|value} ...). FHIR to openEHR writes it to that pipe
 * attribute of the element ({@code active_inactive_status|code}); openEHR to FHIR has to read it back from
 * there. The helper's flat path keeps the RM segments ({@code active_inactive_status/defining_code}), which
 * no flat key has, so nothing used to reach FHIR.
 */
public class OpenEhrFlatPathDataExtractorPinnedAttributeTest {

    private static final String OPT = "/ips/International Patient Summary.opt";

    /** active_inactive_status: DV_CODED_TEXT or DV_TEXT, so a choice element in the flat format. */
    private static final String ELEMENT = "$composition/content[openEHR-EHR-SECTION.adhoc.v1 and "
            + "name/value='Allergies and Intolerances']/items[openEHR-EHR-EVALUATION.adverse_reaction_risk.v2]"
            + "/data[at0001]/items[at0130]";

    private static final String FLAT =
            "international_patient_summary/allergies_and_intolerances/adverse_reaction_risk:0/active_inactive_status";

    private WebTemplate webTemplate;
    private AqlToFlatPathConverter converter;
    private OpenEhrFlatPathDataExtractor extractor;
    private JsonObject flat;

    @SneakyThrows
    @Before
    public void setUp() {
        final String serialized = IOUtils.toString(getClass().getResourceAsStream(OPT));
        webTemplate = new OPTParser(TemplateDocument.Factory.parse(serialized).getTemplate()).parse();
        final OpenFhirStringUtils stringUtils = new OpenFhirStringUtils();
        converter = new AqlToFlatPathConverter(stringUtils, new OpenFhirMapperUtils());
        final FhirValueReaders readers = new FhirValueReaders(new OpenFhirMapperUtils());
        extractor = new OpenEhrFlatPathDataExtractor(stringUtils, new OpenFhirMapperUtils(), new ValueToFHIRParser(
                new TemporalParser(readers), new QuantityParser(readers), new CodedParser(readers),
                new MediaParser(readers), new TextParser(readers), new IdentifierParser(readers),
                new ReferenceParser(readers)));

        flat = new JsonObject();
        flat.addProperty(FLAT + "|value", "AAA");
        flat.addProperty(FLAT + "|terminology", "BBB");
        flat.addProperty(FLAT + "|code", "CCC");
    }

    private List<DataWithIndex> extract(final String openEhrPath) {
        final MappingHelper helper = new MappingHelper();
        helper.setMappingName("activeInactive");
        helper.setFullOpenEhrPath(openEhrPath);
        converter.convert(helper, webTemplate, false);
        return extractor.extract(helper, flat);
    }

    private void assertReads(final String pin, final String expected) {
        final List<DataWithIndex> extracted = extract(ELEMENT + pin);
        Assert.assertEquals(pin, 1, extracted.size());
        Assert.assertTrue(pin, extracted.get(0).getData() instanceof StringType);
        Assert.assertEquals(pin, expected, ((StringType) extracted.get(0).getData()).getValue());
        Assert.assertEquals(pin, 0, extracted.get(0).getIndex());
    }

    @Test
    public void readsThePinnedAttributeOfACodedText() {
        assertReads("/defining_code/code_string", "CCC");
        assertReads("/defining_code", "CCC");
        assertReads("/defining_code/terminology_id", "BBB");
    }

    @Test
    public void readsAPipeAttributeAddressedDirectly() {
        assertReads("/|code", "CCC");
        assertReads("/|value", "AAA");
        assertReads("/|terminology", "BBB");
    }

    @Test
    public void readsTheStrictAqlPathsThroughTheElementsValue() {
        assertReads("/value/value", "AAA");
        assertReads("/value/defining_code/code_string", "CCC");
        assertReads("/value/defining_code/terminology_id", "BBB");
        assertReads("/value/defining_code/terminology_id/value", "BBB");
    }

    @Test
    public void nothingWhenThePinnedAttributeIsAbsent() {
        flat.remove(FLAT + "|code");
        Assert.assertTrue(extract(ELEMENT + "/defining_code/code_string").isEmpty());
    }

    @Test
    public void theWholeElementStillReadsAsACodedValue() {
        final List<DataWithIndex> extracted = extract(ELEMENT);
        Assert.assertEquals(1, extracted.size());
        Assert.assertTrue(extracted.get(0).getData() instanceof CodeableConcept);
        Assert.assertEquals("CCC", ((CodeableConcept) extracted.get(0).getData()).getCodingFirstRep().getCode());
    }
}
