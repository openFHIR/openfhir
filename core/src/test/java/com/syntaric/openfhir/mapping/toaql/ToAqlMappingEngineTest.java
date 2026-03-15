package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ToAqlMappingEngineTest {

    private ToAqlMappingEngine engine;

    @Before
    public void setUp() {
        engine = new ToAqlMappingEngine(new OpenEhrAqlPopulator());
    }

    @Test
    public void findByFhirPath_nullHelpers_returnsEmpty() {
        final List<MappingHelper> result = engine.findByFhirPath(null, "Observation.code");
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void findByFhirPath_matchOnTopLevel_returnsHelper() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");

        final List<MappingHelper> result = engine.findByFhirPath(List.of(helper), "Observation.code");

        Assert.assertEquals(1, result.size());
        Assert.assertSame(helper, result.get(0));
    }

    @Test
    public void findByFhirPath_noMatch_returnsEmpty() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.status");

        final List<MappingHelper> result = engine.findByFhirPath(List.of(helper), "Observation.code");

        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void findByFhirPath_matchOnChild_returnsChild() {
        final MappingHelper child = new MappingHelper();
        child.setFullFhirPath("Observation.code.coding");

        final MappingHelper parent = new MappingHelper();
        parent.setFullFhirPath("Observation.status");
        parent.getChildren().add(child);

        final List<MappingHelper> result = engine.findByFhirPath(List.of(parent), "Observation.code.coding");

        Assert.assertEquals(1, result.size());
        Assert.assertSame(child, result.get(0));
    }

    @Test
    public void findByFhirPath_matchOnBothParentAndChild_returnsBoth() {
        final MappingHelper child = new MappingHelper();
        child.setFullFhirPath("Observation.code.coding");

        final MappingHelper parent = new MappingHelper();
        parent.setFullFhirPath("Observation.code");
        parent.getChildren().add(child);

        final List<MappingHelper> result = engine.findByFhirPath(List.of(parent), "Observation.code");

        Assert.assertEquals(2, result.size()); // parent matches, child also contains "Observation.code"
    }

    @Test
    public void findByFhirPath_containsMatch_returnsHelper() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code.coding.system");

        final List<MappingHelper> result = engine.findByFhirPath(List.of(helper), "Observation.code");

        Assert.assertEquals(1, result.size());
    }

    @Test
    public void findByFhirPath_pipeSeparatedPaths_matchesFirstSegment() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.value");

        // "value-quantity" resolves to "(Observation.value as Quantity) | (Observation.value as SampledData)"
        final String pipePath = "(Observation.value as Quantity) | (Observation.value as SampledData)";
        final List<MappingHelper> result = engine.findByFhirPath(List.of(helper), pipePath);

        Assert.assertEquals(1, result.size());
        Assert.assertSame(helper, result.get(0));
    }

    @Test
    public void findByFhirPath_pipeSeparatedPaths_matchesSecondSegment() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.component.value");

        final String pipePath = "(Observation.value as Quantity) | (Observation.component.value as SampledData)";
        final List<MappingHelper> result = engine.findByFhirPath(List.of(helper), pipePath);

        Assert.assertEquals(1, result.size());
        Assert.assertSame(helper, result.get(0));
    }

    @Test
    public void findByFhirPath_pipeSeparatedPaths_noSegmentMatches_returnsEmpty() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");

        final String pipePath = "(Observation.value as Quantity) | (Observation.value as SampledData)";
        final List<MappingHelper> result = engine.findByFhirPath(List.of(helper), pipePath);

        Assert.assertTrue(result.isEmpty());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // mapQueryToMappingHelper
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void mapQueryToMappingHelper_unknownParam_returnsEmpty() {
        final List<ToAql.ToAqlModels> result = engine.mapQueryToMappingHelper("nonexistent-param", "Observation", List.of());
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void mapQueryToMappingHelper_knownParam_noMatchingHelper_returnsEmpty() {
        // "code" maps to path "Observation.code" — helper has a different path so nothing matches
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.status");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(new FhirConnectContextEntity())
                .mappingHelpers(List.of(helper))
                .build();

        final List<ToAql.ToAqlModels> result = engine.mapQueryToMappingHelper("code", "Observation", List.of(model));

        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void mapQueryToMappingHelper_knownParam_matchingHelper_returnsModel() {
        // "code" resolves to path "Observation.code" — helper contains that path
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");

        final FhirConnectContextEntity context = new FhirConnectContextEntity();
        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(context)
                .mappingHelpers(List.of(helper))
                .build();

        final List<ToAql.ToAqlModels> result = engine.mapQueryToMappingHelper("code", "Observation", List.of(model));

        Assert.assertEquals(1, result.size());
        Assert.assertSame(context, result.get(0).getContext());
        Assert.assertEquals(1, result.get(0).getMappingHelpers().size());
        Assert.assertSame(helper, result.get(0).getMappingHelpers().get(0));
    }

    @Test
    public void mapQueryToMappingHelper_knownParam_matchInChild_returnsModelWithChild() {
        // "category" resolves to "Observation.category" — match is in a child helper
        final MappingHelper child = new MappingHelper();
        child.setFullFhirPath("Observation.category");

        final MappingHelper parent = new MappingHelper();
        parent.setFullFhirPath("Observation.code");
        parent.getChildren().add(child);

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(new FhirConnectContextEntity())
                .mappingHelpers(List.of(parent))
                .build();

        final List<ToAql.ToAqlModels> result = engine.mapQueryToMappingHelper("category", "Observation", List.of(model));

        Assert.assertEquals(1, result.size());
        Assert.assertEquals(1, result.get(0).getMappingHelpers().size());
        Assert.assertSame(child, result.get(0).getMappingHelpers().get(0));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // archetypeOnlyAql
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void archetypeOnlyAql_singleHelper_notNarrowedToTemplate() {
        final MappingHelper helper = new MappingHelper();
        helper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(helper))
                .build();

        final ToAqlResponse response = engine.archetypeOnlyAql(List.of(model), false);

        Assert.assertNotNull(response.getAqls());
        Assert.assertEquals(2, response.getAqls().size());
        Assert.assertEquals(
                "SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(0).getAql());
        Assert.assertEquals(
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(1).getAql());
    }

    @Test
    public void archetypeOnlyAql_singleHelper_narrowedToTemplate() {
        final MappingHelper helper = new MappingHelper();
        helper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(helper))
                .build();

        final ToAqlResponse response = engine.archetypeOnlyAql(List.of(model), true);

        Assert.assertNotNull(response.getAqls());
        Assert.assertEquals(2, response.getAqls().size());
        Assert.assertEquals(
                "SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(0).getAql());
        Assert.assertEquals(
                "SELECT c from EHR e CONTAINS COMPOSITION c [openEHR-EHR-COMPOSITION.growth_chart.v0] CONTAINS OBSERVATION [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(1).getAql());
    }

    @Test
    public void archetypeOnlyAql_multipleHelpersSameArchetype_archetypeDeduplicatedInAql() {
        final MappingHelper helper1 = new MappingHelper();
        helper1.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper1.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]");

        final MappingHelper helper2 = new MappingHelper();
        helper2.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper2.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0005]");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(helper1, helper2))
                .build();

        final ToAqlResponse response = engine.archetypeOnlyAql(List.of(model), false);

        Assert.assertEquals(
                "SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(0).getAql());

        Assert.assertEquals(
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(1).getAql());
    }

    @Test
    public void archetypeOnlyAql_multipleHelpersDifferentArchetypes_bothArchetypesInAql() {
        final MappingHelper helper1 = new MappingHelper();
        helper1.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper1.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]");

        final MappingHelper helper2 = new MappingHelper();
        helper2.setArchetype("openEHR-EHR-OBSERVATION.height.v2");
        helper2.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.height.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(helper1, helper2))
                .build();

        final ToAqlResponse response = engine.archetypeOnlyAql(List.of(model), false);

        Assert.assertEquals(
                "SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2,openEHR-EHR-OBSERVATION.height.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(0).getAql());

        Assert.assertEquals(
                "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2,openEHR-EHR-OBSERVATION.height.v2] WHERE e/ehr_id/value='{{ehrid}}'",
                response.getAqls().get(1).getAql());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // map — multiple params combined into a single AQL
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void map_twoMatchingParams_producedSingleAqlWithBothConditions() {
        // Two params both matching helpers on the same model — should produce one AQL with both conditions AND-ed
        final MappingHelper codeHelper = new MappingHelper();
        codeHelper.setFullFhirPath("Observation.code");
        codeHelper.setPossibleRmTypes(List.of("DvCodedText"));
        codeHelper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        codeHelper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]");

        final MappingHelper statusHelper = new MappingHelper();
        statusHelper.setFullFhirPath("Observation.status");
        codeHelper.setPossibleRmTypes(List.of("DvCodedText"));
        statusHelper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        statusHelper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0005]");


        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(codeHelper, statusHelper))
                .build();

        final List<FhirQueryParam> params = new ArrayList<>();
        params.add(new FhirQueryParam("code", "29463-7"));
        params.add(new FhirQueryParam("status", "final"));

        final ToAqlResponse response = engine.map(List.of(model), "Observation", params, false);

        Assert.assertNotNull(response.getAqls());
        Assert.assertEquals(2, response.getAqls().size());

        final ToAqlResponse.AqlResponse entryAqlResponse = response.getAqls().stream()
                .filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY).findFirst().orElseThrow();
        Assert.assertEquals(
                "SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value = '29463-7' AND h/data[at0002]/events[at0003]/data[at0001]/items[at0005]/value = 'final'",
                entryAqlResponse.getAql());

        final ToAqlResponse.AqlResponse compositionAqlResponse = response.getAqls().stream()
                .filter(a -> a.getType() == ToAqlResponse.AqlType.COMPOSITION).findFirst().orElseThrow();
        Assert.assertEquals(
                "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]/value = '29463-7' AND c/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0005]/value = 'final'",
                compositionAqlResponse.getAql());

        Assert.assertTrue(response.getUnhandledParams() == null || response.getUnhandledParams().isEmpty());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // map — unhandled params
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void map_unknownParam_addsUnhandledParam() {
        final ToAqlResponse response = engine.map(List.of(), "Observation", List.of(new FhirQueryParam("nonexistent-param", "val")), false);

        Assert.assertNotNull(response.getUnhandledParams());
        Assert.assertEquals(1, response.getUnhandledParams().size());
        Assert.assertEquals("nonexistent-param", response.getUnhandledParams().get(0).getParamName());
        Assert.assertEquals(ToAqlResponse.UnhandledParamType.ERROR, response.getUnhandledParams().get(0).getType());
        Assert.assertNotNull(response.getUnhandledParams().get(0).getMessage());
    }

    @Test
    public void map_knownParamNoMatchingHelper_addsUnhandledParam() {
        // "code" resolves to "Observation.code" but the helper has a different path — no match
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.status");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(new FhirConnectContextEntity())
                .mappingHelpers(List.of(helper))
                .build();

        final ToAqlResponse response = engine.map(List.of(model), "Observation", List.of(new FhirQueryParam("code", "123")), false);

        Assert.assertNotNull(response.getUnhandledParams());
        Assert.assertEquals(1, response.getUnhandledParams().size());
        Assert.assertEquals("code", response.getUnhandledParams().get(0).getParamName());
        Assert.assertEquals(ToAqlResponse.UnhandledParamType.ERROR, response.getUnhandledParams().get(0).getType());
    }

    @Test
    public void map_knownParamWithMatchingHelper_noUnhandledParams() {
        // "code" resolves to "Observation.code" — helper matches, so no unhandled param
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(helper))
                .build();

        final ToAqlResponse response = engine.map(List.of(model), "Observation", List.of(new FhirQueryParam("code", "123")), false);

        Assert.assertTrue(response.getUnhandledParams() == null || response.getUnhandledParams().isEmpty());
    }

    @Test
    public void map_multipleParams_onlyUnmatchedAddedToUnhandled() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(getContext())
                .mappingHelpers(List.of(helper))
                .build();

        // "code" matches, "status" does not (different path)
        final ToAqlResponse response = engine.map(List.of(model), "Observation",
                List.of(new FhirQueryParam("code", "123"), new FhirQueryParam("nonexistent-param", "val")), false);

        Assert.assertNotNull(response.getUnhandledParams());
        Assert.assertEquals(1, response.getUnhandledParams().size());
        Assert.assertEquals("nonexistent-param", response.getUnhandledParams().get(0).getParamName());
    }

    @Test
    public void mapQueryToMappingHelper_knownParam_onlyMatchingModelsReturned() {
        // Two models — only one has a helper matching "Observation.status"
        final MappingHelper matching = new MappingHelper();
        matching.setFullFhirPath("Observation.status");

        final MappingHelper nonMatching = new MappingHelper();
        nonMatching.setFullFhirPath("Observation.code");

        final FhirConnectContextEntity matchingContext = new FhirConnectContextEntity();
        final ToAql.ToAqlModels matchingModel = ToAql.ToAqlModels.builder()
                .context(matchingContext)
                .mappingHelpers(List.of(matching))
                .build();
        final ToAql.ToAqlModels nonMatchingModel = ToAql.ToAqlModels.builder()
                .context(new FhirConnectContextEntity())
                .mappingHelpers(List.of(nonMatching))
                .build();

        final List<ToAql.ToAqlModels> result = engine.mapQueryToMappingHelper("status", "Observation",
                List.of(matchingModel, nonMatchingModel));

        Assert.assertEquals(1, result.size());
        Assert.assertSame(matchingContext, result.get(0).getContext());
    }

    private FhirConnectContextEntity getContext() {
        final FhirConnectContextEntity context = new FhirConnectContextEntity();
        FhirConnectContext fhirConnectContext = new FhirConnectContext();
        Context context1 = new Context();
        ContextTemplate template = new ContextTemplate();
        template.setId("Growth chart");
        context1.setTemplate(template);
        context1.setStart("openEHR-EHR-COMPOSITION.growth_chart.v0");
        fhirConnectContext.setContext(context1);
        context.setFhirConnectContext(fhirConnectContext);
        return context;
    }
}
