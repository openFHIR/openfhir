package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.RmAttributePins;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A mapping on one attribute of a data value ({@code .../value/magnitude}, {@code .../value/defining_code/code_string})
 * is a WHERE on that attribute's strict AQL path: a number or boolean unquoted, an object attribute
 * ({@code terminology_id}) completed to its string. The strict spellings used to keep their path and get the
 * element's suffix appended as well ({@code .../value/magnitude/value = 2}); any other spelling of the attribute
 * ({@code .../defining_code}) is spelled the strict way.
 */
public class ToAqlPinnedRmAttributeTest {

    private static final String ARCHETYPE = "openEHR-EHR-OBSERVATION.laboratory_test_result.v1";
    private static final String ELEMENT = ARCHETYPE + "/data[at0001]/events[at0002]/data[at0003]/items[at0004]";
    private static final String WHERE_ELEMENT = "h/data[at0001]/events[at0002]/data[at0003]/items[at0004]";

    private ToAqlMappingEngine engine;

    @Before
    public void setUp() {
        engine = new ToAqlMappingEngine(new OpenEhrAqlPopulator());
    }

    private String where(final String path, final List<String> possibleRmTypes, final String pinnedRmType,
                         final String value) {
        return where(path, possibleRmTypes, pinnedRmType, path.replaceFirst("^/value/", ""), value);
    }

    /**
     * @param pinnedRmType the type whose attribute {@code rmPath} the path addresses, as the converter resolves it
     *                     against the template; null for an ordinary path
     */
    private String where(final String path, final List<String> possibleRmTypes, final String pinnedRmType,
                         final String rmPath, final String value) {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");
        helper.setFhir("code");
        helper.setArchetype(ARCHETYPE);
        helper.setFullOpenEhrPath(ELEMENT + path);
        helper.setPossibleRmTypes(possibleRmTypes);
        if (pinnedRmType != null) {
            final RmAttributePins.Attribute attribute = RmAttributePins.all().get(pinnedRmType).stream()
                    .filter(a -> a.rmPath().equals(rmPath))
                    .findFirst().orElseThrow();
            helper.setPinnedAttribute(attribute);
            helper.setPinnedAqlPath(ELEMENT + "/value/" + attribute.rmPath() + attribute.aqlTail());
        }

        return aql(helper, value);
    }

    private String aql(final MappingHelper helper, final String value) {
        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(context())
                .mappingHelpers(List.of(helper))
                .mainArchetype(ARCHETYPE)
                .build();
        final String aql = engine.map(List.of(model), "Observation", List.of(new FhirQueryParam("code", value, null)),
                                      false).getAqls().stream()
                .filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .map(ToAqlResponse.AqlResponse::getAql)
                .findFirst().orElseThrow();
        final String marker = " AND " + WHERE_ELEMENT;
        Assert.assertTrue(aql, aql.contains(marker));
        return aql.substring(aql.indexOf(marker) + marker.length());
    }

    @Test
    public void numbersAndBooleansAreUnquoted() {
        Assert.assertEquals("/value/magnitude = 2", where("/value/magnitude", List.of("ELEMENT"), "DV_QUANTITY", "2"));
        Assert.assertEquals("/value/magnitude = 2", where("/value/magnitude", List.of("DV_COUNT"), "DV_COUNT", "2"));
        Assert.assertEquals("/value/numerator = 1", where("/value/numerator", List.of("ELEMENT"), "DV_PROPORTION", "1"));
        Assert.assertEquals("/value/value = 3", where("/value/value", List.of("ELEMENT"), "DV_ORDINAL", "3"));
        Assert.assertEquals("/value/value = true", where("/value/value", List.of("DV_BOOLEAN"), "DV_BOOLEAN", "true"));
    }

    @Test
    public void textIsQuoted() {
        Assert.assertEquals("/value/units = 'mg'", where("/value/units", List.of("ELEMENT"), "DV_QUANTITY", "mg"));
        Assert.assertEquals("/value/value = 'Active'",
                            where("/value/value", List.of("ELEMENT"), "DV_CODED_TEXT", "Active"));
        Assert.assertEquals("/value/defining_code/code_string = 'at0131'",
                            where("/value/defining_code/code_string", List.of("ELEMENT"), "DV_CODED_TEXT", "at0131"));
        Assert.assertEquals("/value/symbol/defining_code/code_string = 'at0012'",
                            where("/value/symbol/defining_code/code_string", List.of("ELEMENT"), "DV_ORDINAL",
                                  "at0012"));
        Assert.assertEquals("/value/id = 'X-1'", where("/value/id", List.of("ELEMENT"), "DV_IDENTIFIER", "X-1"));
    }

    @Test
    public void anObjectAttributeIsCompletedToItsString() {
        final String expected = "/value/defining_code/terminology_id/value = 'local'";
        Assert.assertEquals(expected,
                            where("/value/defining_code/terminology_id", List.of("ELEMENT"), "DV_CODED_TEXT", "local"));
        Assert.assertEquals(expected, where("/value/defining_code/terminology_id/value", List.of("ELEMENT"),
                                            "DV_CODED_TEXT", "local"));
    }

    /**
     * A whole data value is compared by the attribute its type is compared by, through the ELEMENT's value: the
     * number of a quantity, the code of a coded text (a token search names a code) ...
     */
    @Test
    public void aWholeDataValueIsComparedByItsTypesAttribute() {
        Assert.assertEquals("/value/magnitude = 2", whole(List.of("DV_QUANTITY"), "ELEMENT", null, null, "2"));
        Assert.assertEquals("/value/magnitude = 2", whole(List.of("DV_COUNT"), "ELEMENT", null, null, "2"));
        Assert.assertEquals("/value/numerator = 1", whole(List.of("DV_PROPORTION"), "ELEMENT", null, null, "1"));
        Assert.assertEquals("/value/defining_code/code_string = 'at0131'",
                            whole(List.of("DV_CODED_TEXT", "DV_TEXT"), "ELEMENT", null, null, "at0131"));
        Assert.assertEquals("/value/value = 'text'", whole(List.of("DV_TEXT"), "ELEMENT", null, null, "text"));
        Assert.assertEquals("/value/id = 'X-1'", whole(List.of("DV_IDENTIFIER"), "ELEMENT", null, null, "X-1"));
        Assert.assertEquals("/value/value = true", whole(List.of("DV_BOOLEAN"), "ELEMENT", null, null, "true"));
    }

    /** A node that is a data value itself ({@code context/start_time}) has the attribute directly. */
    @Test
    public void aDataValueNodeHasTheAttributeDirectly() {
        Assert.assertEquals("/value = '2024-01-02'",
                            whole(List.of("DV_DATE_TIME"), "DV_DATE_TIME", null, null, "2024-01-02"));
        Assert.assertEquals("/defining_code/code_string = '245'",
                            whole(List.of("DV_CODED_TEXT"), "DV_CODED_TEXT", null, null, "245"));
    }

    @Test
    public void withTypePicksAmongTheElementsTypes() {
        Assert.assertEquals("/value/magnitude = 2",
                            whole(List.of("DV_TEXT", "DV_QUANTITY"), "ELEMENT", "QUANTITY", null, "2"));
        Assert.assertEquals("/value/value = '2'", whole(List.of("DV_TEXT", "DV_QUANTITY"), "ELEMENT", null, null, "2"));
        // a type the template does not allow there picks nothing
        Assert.assertEquals("/value/value = '2'",
                            whole(List.of("DV_TEXT", "DV_QUANTITY"), "ELEMENT", "IDENTIFIER", null, "2"));
    }

    /** A manual mapping's fixed value is written to a coded text as its text, so it is compared as one. */
    @Test
    public void aManualValueIsComparedToACodedTextsText() {
        Assert.assertEquals("/value/value = 'Confirmed'",
                            whole(List.of("DV_CODED_TEXT"), "ELEMENT", null, "Confirmed", "Confirmed"));
    }

    private String whole(final List<String> possibleRmTypes, final String nodeRmType, final String withType,
                         final String manualValue, final String value) {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");
        helper.setFhir("code");
        helper.setArchetype(ARCHETYPE);
        helper.setFullOpenEhrPath(ELEMENT);
        helper.setPossibleRmTypes(possibleRmTypes);
        helper.setOpenEhrNodeRmType(nodeRmType);
        helper.setHardcodedType(withType);
        helper.setManualOpenEhrValue(manualValue);
        return aql(helper, value);
    }

    @Test
    public void anyOtherSpellingIsTheStrictPath() {
        Assert.assertEquals("/value/defining_code/code_string = 'at0131'",
                            where("/defining_code/code_string", List.of("ELEMENT"), "DV_CODED_TEXT",
                                  "defining_code/code_string", "at0131"));
        Assert.assertEquals("/value/defining_code/code_string = 'at0131'",
                            where("/defining_code", List.of("ELEMENT"), "DV_CODED_TEXT",
                                  "defining_code/code_string", "at0131"));
        Assert.assertEquals("/value/defining_code/terminology_id/value = 'local'",
                            where("/|terminology", List.of("ELEMENT"), "DV_CODED_TEXT",
                                  "defining_code/terminology_id", "local"));
        Assert.assertEquals("/value/magnitude = 2",
                            where("/magnitude", List.of("ELEMENT"), "DV_QUANTITY", "magnitude", "2"));
    }

    private FhirConnectContextEntity context() {
        final ContextTemplate template = new ContextTemplate();
        template.setId("Laboratory");
        final Context context = new Context();
        context.setTemplate(template);
        context.setStart("openEHR-EHR-COMPOSITION.report-result.v1");
        final FhirConnectContext fhirConnectContext = new FhirConnectContext();
        fhirConnectContext.setContext(context);
        final FhirConnectContextEntity entity = new FhirConnectContextEntity();
        entity.setFhirConnectContext(fhirConnectContext);
        return entity;
    }
}
