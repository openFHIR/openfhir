package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.rest.RequestValidationException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * A search parameter's condition is never lost silently: an AQL is never replaced by the unfiltered archetype-only one
 * while the parameter counts as handled. A value one mapping can't compare means that mapping matches nothing; a
 * mapping's own fixed value that its attribute can't take is the mapping's fault, not the request's.
 */
public class ToAqlConditionLossTest {

    private final ToAqlMappingEngine engine = new ToAqlMappingEngine(new OpenEhrAqlPopulator());

    /** An AQL that doesn't parse is left out with an ERROR, not replaced by the archetype-only one. */
    @Test
    public void anAqlThatDoesNotParse_isReportedAndNotReplacedByTheUnfilteredOne() {
        // a path no AQL parser reads: the mapping's fault, but the condition is lost all the same
        final MappingHelper broken = helper("openEHR-EHR-OBSERVATION.a.v1", "DV_CODED_TEXT", null);
        broken.setFullOpenEhrPath("$composition/content[openEHR-EHR-OBSERVATION.a.v1]/data[at0001]/items[at0004]#");

        final ToAqlResponse response = engine.map(List.of(model("A", broken)), "Observation",
                                                  List.of(new FhirQueryParam("code", "29463-7", null)), false);

        Assert.assertTrue(String.valueOf(response.getAqls()),
                          response.getAqls() == null || response.getAqls().isEmpty());
        assertReported(response, "code", ToAqlResponse.UnhandledParamType.ERROR);
    }

    /** Without parameters, the archetype-only AQL is what is asked for. */
    @Test
    public void withoutParameters_theArchetypeOnlyAqlIsReturned() {
        final ToAqlResponse response = engine.map(
                List.of(model("A", helper("openEHR-EHR-OBSERVATION.a.v1", "DV_CODED_TEXT", null))), "Observation",
                List.of(), false);

        Assert.assertEquals(2, response.getAqls().size());
        Assert.assertNull(response.getUnhandledParams());
    }

    /**
     * {@code code=29463-7} across the templates: a code for the one that maps it to a coded text, no number for the
     * one that maps it to a quantity. That one matches nothing and is left out with a WARNING; the request stands.
     */
    @Test
    public void aValueOneMappingCannotCompare_leavesOutOnlyThatMappingsAql() {
        final ToAqlResponse response = engine.map(
                List.of(model("A", helper("openEHR-EHR-OBSERVATION.a.v1", "DV_CODED_TEXT", null)),
                        model("B", helper("openEHR-EHR-OBSERVATION.b.v1", "DV_QUANTITY", null))),
                "Observation", List.of(new FhirQueryParam("code", "29463-7", null)), false);

        Assert.assertFalse(response.getAqls().isEmpty());
        Assert.assertTrue(response.getAqls().stream().allMatch(a -> "A".equals(a.getTemplateId())));
        Assert.assertTrue(response.getAqls().stream().allMatch(a -> a.getAql().contains("= '29463-7'")));
        final ToAqlResponse.UnhandledParam warning = assertReported(response, "code",
                                                                    ToAqlResponse.UnhandledParamType.WARNING);
        Assert.assertTrue(warning.getMessage(), warning.getMessage().contains("B"));
    }

    @Test
    public void aValueEveryMappingComparesToANumber_andIsNotOne_isRejected() {
        final RequestValidationException e = Assert.assertThrows(RequestValidationException.class, () -> engine.map(
                List.of(model("A", helper("openEHR-EHR-OBSERVATION.a.v1", "DV_COUNT", null)),
                        model("B", helper("openEHR-EHR-OBSERVATION.b.v1", "DV_QUANTITY", null))),
                "Observation", List.of(new FhirQueryParam("code", "29463-7", null)), false));
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("'code'"));
        Assert.assertFalse(e.getMessage(), e.getMessage().contains("29463-7"));
    }

    /**
     * A mapping whose fixed ({@code manual}) value is no number for the number it is written to is at fault itself:
     * an ERROR names the parameter and the mapping, that mapping's AQL is left out, the others stand.
     */
    @Test
    public void aMappingsFixedValueItsAttributeCannotTake_isAMappingError() {
        final MappingHelper faulty = helper("openEHR-EHR-OBSERVATION.b.v1", "DV_QUANTITY", "high");
        faulty.setModelMetadataName("OBSERVATION.b.v1");

        final ToAqlResponse response = engine.map(
                List.of(model("A", helper("openEHR-EHR-OBSERVATION.a.v1", "DV_CODED_TEXT", null)),
                        model("B", faulty)),
                "Observation", List.of(new FhirQueryParam("code", "29463-7", null)), false);

        Assert.assertTrue(response.getAqls().stream().allMatch(a -> "A".equals(a.getTemplateId())));
        final ToAqlResponse.UnhandledParam error = assertReported(response, "code",
                                                                  ToAqlResponse.UnhandledParamType.ERROR);
        Assert.assertTrue(error.getMessage(), error.getMessage().contains("mapping"));
        Assert.assertTrue(error.getMessage(), error.getMessage().contains("OBSERVATION.b.v1"));
        Assert.assertFalse(error.getMessage(), error.getMessage().contains("29463-7"));
    }

    /** Alone, the faulty mapping gives no AQL, and no 400: the request is not at fault. */
    @Test
    public void aMappingsFixedValueItsAttributeCannotTake_aloneGivesNoAqlAndNoRequestError() {
        final ToAqlResponse response = engine.map(
                List.of(model("B", helper("openEHR-EHR-OBSERVATION.b.v1", "DV_QUANTITY", "high"))),
                "Observation", List.of(new FhirQueryParam("code", "29463-7", null)), false);

        Assert.assertTrue(String.valueOf(response.getAqls()),
                          response.getAqls() == null || response.getAqls().isEmpty());
        assertReported(response, "code", ToAqlResponse.UnhandledParamType.ERROR);
    }

    private static ToAqlResponse.UnhandledParam assertReported(final ToAqlResponse response, final String param,
                                                               final ToAqlResponse.UnhandledParamType type) {
        Assert.assertNotNull("nothing reported", response.getUnhandledParams());
        return response.getUnhandledParams().stream()
                .filter(p -> param.equals(p.getParamName()) && p.getType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no " + type + " for " + param + ": "
                                                              + response.getUnhandledParams()));
    }

    private static MappingHelper helper(final String archetype, final String rmType, final String manualValue) {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");
        helper.setFhir("code");
        helper.setArchetype(archetype);
        helper.setFullOpenEhrPath("$composition/content[" + archetype + "]/data[at0001]/items[at0004]");
        helper.setPossibleRmTypes(List.of(rmType));
        helper.setOpenEhrNodeRmType("ELEMENT");
        helper.setManualOpenEhrValue(manualValue);
        return helper;
    }

    private static ToAql.ToAqlModels model(final String templateId, final MappingHelper helper) {
        final Context context = new Context();
        final ContextTemplate template = new ContextTemplate();
        template.setId(templateId);
        context.setTemplate(template);
        context.setStart("openEHR-EHR-COMPOSITION.sample.v1");
        final FhirConnectContext fhirConnectContext = new FhirConnectContext();
        fhirConnectContext.setContext(context);
        final FhirConnectContextEntity entity = new FhirConnectContextEntity();
        entity.setFhirConnectContext(fhirConnectContext);
        return ToAql.ToAqlModels.builder()
                .context(entity)
                .mappingHelpers(List.of(helper))
                .mainArchetype(helper.getArchetype())
                .build();
    }
}
