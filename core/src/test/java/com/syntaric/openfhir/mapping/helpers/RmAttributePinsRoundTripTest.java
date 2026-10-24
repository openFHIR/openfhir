package com.syntaric.openfhir.mapping.helpers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syntaric.openfhir.fc.FhirConnectConst;
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
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openehr.schemas.v1.TemplateDocument;

/**
 * Every attribute {@link RmAttributePins} knows, pinned with a strict AQL path through an ELEMENT's value
 * ({@code .../value/magnitude}), on a single-type element and on a choice element of a template that has all the
 * data value types. The flat keys the helper maps must be ones the EHRbase SDK accepts, and the value must read back
 * from what the SDK writes (the format a server returns compositions in).
 */
public class RmAttributePinsRoundTripTest {

    private static final String OPT = "/problemlist/ProblemList.opt";

    /** The rest of a valid composition of the template. */
    private static final Map<String, String> MANDATORY = Map.ofEntries(
            Map.entry("problem_list_nl/language|code", "en"),
            Map.entry("problem_list_nl/language|terminology", "ISO_639-1"),
            Map.entry("problem_list_nl/territory|code", "NL"),
            Map.entry("problem_list_nl/territory|terminology", "ISO_3166-1"),
            Map.entry("problem_list_nl/composer|name", "x"),
            Map.entry("problem_list_nl/problem_diagnosis/problem_diagnosis_name", "p"),
            Map.entry("problem_list_nl/problem_diagnosis/language|code", "en"),
            Map.entry("problem_list_nl/problem_diagnosis/language|terminology", "ISO_639-1"),
            Map.entry("problem_list_nl/problem_diagnosis/encoding|code", "UTF-8"),
            Map.entry("problem_list_nl/problem_diagnosis/encoding|terminology", "IANA_character-sets"),
            Map.entry("problem_list_nl/problem_diagnosis/subject|name", "s"));

    private WebTemplate webTemplate;
    private AqlToFlatPathConverter converter;
    private OpenEhrFlatPathDataExtractor extractor;
    /** Per data value type, an ELEMENT that holds only that type, where the template has one. */
    private final Map<String, String> singleTypeElements = new LinkedHashMap<>();
    /** An ELEMENT that may hold any of the types. */
    private String choiceElement;

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

        final Deque<WebTemplateNode> queue = new ArrayDeque<>(List.of(webTemplate.getTree()));
        while (!queue.isEmpty()) {
            final WebTemplateNode node = queue.poll();
            queue.addAll(node.getChildren());
            if (!FhirConnectConst.ELEMENT.equals(node.getRmType())) {
                continue;
            }
            final List<String> types = node.getChildren().stream()
                    .filter(c -> c.getAqlPath(false).endsWith("/value"))
                    .map(WebTemplateNode::getRmType).distinct().toList();
            final String path = "$composition" + node.getAqlPath(true);
            if (types.size() == 1) {
                singleTypeElements.putIfAbsent(types.get(0), path);
            } else if (types.containsAll(RmAttributePins.all().keySet())) {
                choiceElement = choiceElement == null ? path : choiceElement;
            }
        }
        Assert.assertNotNull("the template has an element of every type", choiceElement);
    }

    private MappingHelper pinned(final String element, final RmAttributePins.Attribute attribute,
                                 final boolean withType) {
        final MappingHelper helper = new MappingHelper();
        helper.setMappingName(attribute.rmType() + " " + attribute.rmPath());
        helper.setFullOpenEhrPath(element + "/value/" + attribute.rmPath());
        if (withType) {
            helper.setHardcodedType(attribute.rmType());
        }
        converter.convert(helper, webTemplate, false);
        return helper;
    }

    private static String sample(final RmAttributePins.Attribute attribute) {
        switch (attribute.rmType()) {
            case FhirConnectConst.DV_DATE_TIME: return "2024-01-02T10:00:00";
            case FhirConnectConst.DV_DATE: return "2024-01-02";
            case FhirConnectConst.DV_TIME: return "10:00:00";
            case FhirConnectConst.DV_DURATION: return "PT1H";
            case FhirConnectConst.DV_BOOL: return "true";
            case FhirConnectConst.DV_PROPORTION: if ("type".equals(attribute.rmPath())) return "1"; break;
            case FhirConnectConst.DV_MULTIMEDIA: if (attribute.rmPath().startsWith("media_type")) return "image/png"; break;
            default: break;
        }
        if ("units".equals(attribute.rmPath())) return "mg";
        if (attribute.rmPath().contains("terminology_id")) return "local";
        return attribute.kind() == RmAttributePins.Kind.TEXT ? "v_" + attribute.rmPath().replace('/', '_') : "2";
    }

    private static boolean sameValue(final RmAttributePins.Attribute attribute, final String expected,
                                     final String actual) {
        if (attribute.kind() == RmAttributePins.Kind.NUMBER) {
            return new BigDecimal(expected).compareTo(new BigDecimal(actual)) == 0;
        }
        return actual.startsWith(expected);
    }

    /** Pins every attribute of {@code rmType} on {@code element}, round-trips through the SDK and reads them back. */
    private void roundTrip(final String rmType, final String element, final boolean choice) {
        final List<RmAttributePins.Attribute> attributes = RmAttributePins.all().get(rmType).stream()
                // the same attribute as defining_code/terminology_id, spelled to its string
                .filter(a -> !a.rmPath().endsWith("terminology_id/value"))
                .toList();
        final JsonObject flat = new JsonObject();
        MANDATORY.forEach(flat::addProperty);
        final Map<RmAttributePins.Attribute, MappingHelper> helpers = new LinkedHashMap<>();
        for (final RmAttributePins.Attribute attribute : attributes) {
            final MappingHelper helper = pinned(element, attribute, choice);
            final String where = rmType + " " + attribute.rmPath() + (choice ? " (choice)" : "");
            Assert.assertEquals(where, rmType, helper.getPinnedRmType());
            final String key = helper.getFullOpenEhrFlatPath().replace("[n]", ":0") + helper.getFlatPathPipeSuffix();
            if (choice) {
                Assert.assertTrue(where + ": " + key, key.contains("/" + RmAttributePins.typeSegment(
                        List.of(rmType, "DV_OTHER"), rmType)));
            }
            // the template's own value for this element in the rest of the composition
            if (MANDATORY.containsKey(key.split("\\|")[0])) {
                flat.remove(key.split("\\|")[0]);
            }
            flat.addProperty(key, sample(attribute));
            helpers.put(attribute, helper);
        }

        final String written;
        try {
            written = new FlatJsonMarshaller().toFlatJson(
                    new FlatJsonUnmarshaller().unmarshal(flat.toString(), webTemplate), webTemplate);
        } catch (final Exception e) {
            throw new AssertionError(rmType + (choice ? " (choice)" : "") + ": the SDK rejects " + flat, e);
        }
        final JsonObject returned = JsonParser.parseString(written).getAsJsonObject();

        helpers.forEach((attribute, helper) -> {
            final String where = rmType + " " + attribute.rmPath() + (choice ? " (choice)" : "");
            final List<DataWithIndex> read = extractor.extract(helper, returned);
            Assert.assertEquals(where + " read " + read.stream().map(DataWithIndex::getFullOpenEhrPath).toList(), 1, read.size());
            final String value = read.get(0).getData() instanceof StringType s ? s.getValue()
                    : read.get(0).getData().primitiveValue();
            Assert.assertNotNull(where + ": " + read.get(0).getData(), value);
            Assert.assertTrue(where + ": read " + value, sameValue(attribute, sample(attribute), value));
        });
    }

    @Test
    public void everyAttributeOnASingleTypeElement() {
        Assert.assertFalse(singleTypeElements.isEmpty());
        singleTypeElements.forEach((rmType, element) -> {
            if (RmAttributePins.all().containsKey(rmType)) {
                roundTrip(rmType, element, false);
            }
        });
    }

    @Test
    public void everyAttributeOnAChoiceElement() {
        RmAttributePins.all().keySet().forEach(rmType -> roundTrip(rmType, choiceElement, true));
    }

    @Test
    public void anAttributeSeveralTypesHaveIsTheFirstTypesWithoutWithType() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullOpenEhrPath(choiceElement + "/value/magnitude");
        converter.convert(helper, webTemplate, false);
        Assert.assertEquals(FhirConnectConst.DV_QUANTITY, helper.getPinnedRmType());
        Assert.assertEquals("|magnitude", helper.getFlatPathPipeSuffix());
    }

    @Test
    public void theWholeValueIsNoPin() {
        Assert.assertNull(RmAttributePins.parse(choiceElement + "/value"));
        Assert.assertNull(RmAttributePins.parse(choiceElement));
        Assert.assertNull(RmAttributePins.parse(choiceElement + "/value/not_an_attribute"));
        final MappingHelper helper = new MappingHelper();
        helper.setFullOpenEhrPath(choiceElement + "/null_flavour/value");
        Assert.assertNull(RmAttributePins.parse(helper.getFullOpenEhrPath()));
    }
}
