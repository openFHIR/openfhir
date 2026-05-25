package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class ToAqlQueryRuleMatchTest {

    private ToAql toAql;

    @Before
    public void setUp() {
        toAql = new ToAql(null, null, null, null, null, null, null, null);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // matchesRule — operation rules ($xxx)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void matchesRule_operationRule_matches() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam(null, null, "$summary"));
        Assert.assertTrue(toAql.matchesRule("Patient", params, "$summary"));
    }

    @Test
    public void matchesRule_operationRule_noOperationInParams_doesNotMatch() {
        Assert.assertFalse(toAql.matchesRule("Patient", List.of(), "$summary"));
    }

    @Test
    public void matchesRule_operationRule_differentOperation_doesNotMatch() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam(null, null, "$everything"));
        Assert.assertFalse(toAql.matchesRule("Patient", params, "$summary"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // matchesRule — resource-only rule (no params)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void matchesRule_resourceOnly_matches() {
        Assert.assertTrue(toAql.matchesRule("Condition", List.of(new FhirQueryParam("code", "123", null)), "Condition"));
    }

    @Test
    public void matchesRule_resourceOnly_wrongResource_doesNotMatch() {
        Assert.assertFalse(toAql.matchesRule("Observation", List.of(), "Condition"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // matchesRule — resource with params
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void matchesRule_withParams_allPresent_matches() {
        final List<FhirQueryParam> params = List.of(
                new FhirQueryParam("something", "somethignelse", null),
                new FhirQueryParam("other", "val", null)
        );
        Assert.assertTrue(toAql.matchesRule("Condition", params, "Condition?something=somethignelse"));
    }

    @Test
    public void matchesRule_withParams_ruleMissingOneParam_stillMatches() {
        // rule has fewer params than request — should still match
        final List<FhirQueryParam> params = List.of(
                new FhirQueryParam("something", "somethignelse", null),
                new FhirQueryParam("extra", "ignored", null)
        );
        Assert.assertTrue(toAql.matchesRule("Condition", params, "Condition?something=somethignelse"));
    }

    @Test
    public void matchesRule_withParams_wrongValue_doesNotMatch() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam("something", "wrongvalue", null));
        Assert.assertFalse(toAql.matchesRule("Condition", params, "Condition?something=somethignelse"));
    }

    @Test
    public void matchesRule_withParams_paramMissing_doesNotMatch() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam("other", "val", null));
        Assert.assertFalse(toAql.matchesRule("Condition", params, "Condition?something=somethignelse"));
    }

    @Test
    public void matchesRule_withParams_multipleRuleParams_allPresent_matches() {
        final List<FhirQueryParam> params = List.of(
                new FhirQueryParam("code", "abc", null),
                new FhirQueryParam("category", "lab", null)
        );
        Assert.assertTrue(toAql.matchesRule("Observation", params, "Observation?code=abc&category=lab"));
    }

    @Test
    public void matchesRule_withParams_multipleRuleParams_moreInQuery_matches() {
        final List<FhirQueryParam> params = List.of(
                new FhirQueryParam("code", "abc", null),
                new FhirQueryParam("category", "lab", null),
                new FhirQueryParam("patient", "Patient/123", null)
        );
        Assert.assertTrue(toAql.matchesRule("Observation", params, "Observation?code=abc&category=lab"));
    }

    @Test
    public void matchesRule_withParams_multipleRuleParams_oneAbsent_doesNotMatch() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam("code", "abc", null),
                                                    new FhirQueryParam("patient", "Patient/123", null));
        Assert.assertFalse(toAql.matchesRule("Observation", params, "Observation?code=abc&category=lab"));
    }

    @Test
    public void matchesRule_withParams_paramsInDifferentOrder_matches() {
        final List<FhirQueryParam> params = List.of(
                new FhirQueryParam("category", "lab", null),
                new FhirQueryParam("code", "abc", null)
        );
        Assert.assertTrue(toAql.matchesRule("Observation", params, "Observation?code=abc&category=lab"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // queryMatchesAnyRule
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void queryMatchesAnyRule_nullRules_returnsFalse() {
        Assert.assertFalse(toAql.queryMatchesAnyRule("Condition", List.of(), null));
    }

    @Test
    public void queryMatchesAnyRule_emptyRules_returnsFalse() {
        Assert.assertFalse(toAql.queryMatchesAnyRule("Condition", List.of(), List.of()));
    }

    @Test
    public void queryMatchesAnyRule_firstRuleMatches_returnsTrue() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam(null, null, "$summary"));
        Assert.assertTrue(toAql.queryMatchesAnyRule("Patient", params,
                List.of("$summary", "Condition?something=somethignelse")));
    }

    @Test
    public void queryMatchesAnyRule_secondRuleMatches_returnsTrue() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam("something", "somethignelse", null));
        Assert.assertTrue(toAql.queryMatchesAnyRule("Condition", params,
                List.of("$summary", "Condition?something=somethignelse")));
    }

    @Test
    public void queryMatchesAnyRule_noRuleMatches_returnsFalse() {
        final List<FhirQueryParam> params = List.of(new FhirQueryParam("other", "val", null));
        Assert.assertFalse(toAql.queryMatchesAnyRule("Observation", params,
                List.of("$summary", "Condition?something=somethignelse")));
    }
}
