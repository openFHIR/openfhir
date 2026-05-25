package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_NOT_OF;
import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_ONE_OF;

public class ToAqlFhirConditionPassesTest {

    private ToAqlMappingEngine engine;

    @Before
    public void setUp() {
        engine = new ToAqlMappingEngine(new OpenEhrAqlPopulator());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // No conditions on helper — should always pass
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void fhirConditionPasses_nullConditions_returnsTrue() {
        final MappingHelper helper = new MappingHelper();
        helper.setFhirConditions(null);
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_emptyConditions_returnsTrue() {
        final MappingHelper helper = new MappingHelper();
        helper.setFhirConditions(List.of());
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // "one of" operator
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void fhirConditionPasses_oneOf_valueMatches_returnsTrue() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_ONE_OF, List.of("laboratory", "vital-signs"));
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_oneOf_secondValueMatches_returnsTrue() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_ONE_OF, List.of("laboratory", "vital-signs"));
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "vital-signs", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_oneOf_valueDoesNotMatch_returnsFalse() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_ONE_OF, List.of("laboratory", "vital-signs"));
        Assert.assertFalse(engine.fhirConditionPasses(new FhirQueryParam("category", "imaging", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_oneOf_emptyCriterias_returnsFalse() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_ONE_OF, List.of());
        Assert.assertFalse(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // "not of" operator
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void fhirConditionPasses_notOf_valueInCriterias_returnsFalse() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_NOT_OF, List.of("imaging", "laboratory"));
        Assert.assertFalse(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_notOf_valueNotInCriterias_returnsTrue() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_NOT_OF, List.of("imaging", "laboratory"));
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "vital-signs", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_notOf_emptyCriterias_returnsTrue() {
        final MappingHelper helper = helperWithCondition(CONDITION_OPERATOR_NOT_OF, List.of());
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Unknown operator — should pass (default true branch)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void fhirConditionPasses_unknownOperator_returnsTrue() {
        final MappingHelper helper = helperWithCondition("some-unknown-op", List.of("laboratory"));
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Multiple conditions — all must pass (AND semantics)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void fhirConditionPasses_multipleConditions_allPass_returnsTrue() {
        final Condition c1 = condition(CONDITION_OPERATOR_ONE_OF, List.of("laboratory", "vital-signs"));
        final Condition c2 = condition(CONDITION_OPERATOR_NOT_OF, List.of("imaging"));
        final MappingHelper helper = new MappingHelper();
        helper.setFhirConditions(List.of(c1, c2));
        Assert.assertTrue(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    @Test
    public void fhirConditionPasses_multipleConditions_oneFails_returnsFalse() {
        final Condition c1 = condition(CONDITION_OPERATOR_ONE_OF, List.of("laboratory", "vital-signs"));
        final Condition c2 = condition(CONDITION_OPERATOR_NOT_OF, List.of("laboratory")); // this blocks "laboratory"
        final MappingHelper helper = new MappingHelper();
        helper.setFhirConditions(List.of(c1, c2));
        Assert.assertFalse(engine.fhirConditionPasses(new FhirQueryParam("category", "laboratory", null), helper, "category"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private MappingHelper helperWithCondition(final String operator, final List<String> criterias) {
        final MappingHelper helper = new MappingHelper();
        helper.setFhirConditions(List.of(condition(operator, criterias)));
        return helper;
    }

    private Condition condition(final String operator, final List<String> criterias) {
        final Condition c = new Condition();
        c.setOperator(operator);
        c.setCriterias(criterias);
        return c;
    }
}
