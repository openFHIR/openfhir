package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A mapping pinning one attribute of a data value with strict AQL ({@code .../value/magnitude},
 * {@code .../value/defining_code/code_string}) is a WHERE on that path as written: a number or boolean unquoted, an
 * object attribute ({@code terminology_id}) completed to its string. The strict spellings used to keep their path and
 * get the element's suffix appended as well ({@code .../value/magnitude/value = 2}).
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
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");
        helper.setFhir("code");
        helper.setArchetype(ARCHETYPE);
        helper.setFullOpenEhrPath(ELEMENT + path);
        helper.setPossibleRmTypes(possibleRmTypes);
        helper.setPinnedRmType(pinnedRmType);

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

    @Test
    public void unpinnedPathsKeepTheirDataTypeAwareSuffix() {
        // as before: the element and the suffix its type gives
        Assert.assertEquals("/value = 2", where("", List.of("DV_QUANTITY"), null, "2"));
        Assert.assertEquals("/value/defining_code/code_string = 'at0131'",
                            where("/defining_code/code_string", List.of("CODE_PHRASE"), null, "at0131"));
        Assert.assertEquals("/value/value = 'Active'", where("", List.of("DV_CODED_TEXT"), null, "Active"));
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
