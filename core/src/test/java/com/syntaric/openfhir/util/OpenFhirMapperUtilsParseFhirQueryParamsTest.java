package com.syntaric.openfhir.util;

import com.syntaric.openfhir.aql.FhirQueryParam;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class OpenFhirMapperUtilsParseFhirQueryParamsTest {

    private OpenFhirMapperUtils utils;

    @Before
    public void setUp() {
        utils = new OpenFhirMapperUtils();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // No params
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirQueryParams_blank_returnsEmpty() {
        Assert.assertTrue(utils.parseFhirQueryParams("   ").isEmpty());
    }

    @Test
    public void parseFhirQueryParams_null_returnsEmpty() {
        Assert.assertTrue(utils.parseFhirQueryParams(null).isEmpty());
    }

    @Test
    public void parseFhirQueryParams_noQueryString_returnsEmpty() {
        Assert.assertTrue(utils.parseFhirQueryParams("Observation").isEmpty());
    }

    @Test
    public void parseFhirQueryParams_fullUrlNoQueryString_returnsEmpty() {
        Assert.assertTrue(utils.parseFhirQueryParams("http://example.com/fhir/Observation").isEmpty());
    }

    @Test
    public void parseFhirQueryParams_trailingQuestionMarkOnly_returnsEmpty() {
        Assert.assertTrue(utils.parseFhirQueryParams("Observation?").isEmpty());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Single param
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirQueryParams_singleParam_relativeUrl() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("Observation?code=123");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("code", result.get(0).getName());
        Assert.assertEquals("123", result.get(0).getValue());
    }

    @Test
    public void parseFhirQueryParams_singleParam_fullUrl() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("http://example.com/fhir/Observation?code=123");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("code", result.get(0).getName());
        Assert.assertEquals("123", result.get(0).getValue());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Multiple params
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirQueryParams_multipleParams_returnsAll() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("Observation?code=123&category=lab");
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("code", result.get(0).getName());
        Assert.assertEquals("123", result.get(0).getValue());
        Assert.assertEquals("category", result.get(1).getName());
        Assert.assertEquals("lab", result.get(1).getValue());
    }

    @Test
    public void parseFhirQueryParams_multipleParams_fullUrl() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams(
                "https://example.com/fhir/Condition?code=abc&category=problem-list-item&patient=456");
        Assert.assertEquals(3, result.size());
        Assert.assertEquals("code", result.get(0).getName());
        Assert.assertEquals("category", result.get(1).getName());
        Assert.assertEquals("patient", result.get(2).getName());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Value contains '='
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirQueryParams_valueContainsEquals_parsedCorrectly() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("Observation?token=a=b");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("token", result.get(0).getName());
        Assert.assertEquals("a=b", result.get(0).getValue());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Operation URLs (Patient/123/$summary style)
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirQueryParams_operationNoQueryParams_returnsOperationEntry() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("Patient/123/$summary");
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("$summary", result.get(0).getOperation());
        Assert.assertNull(result.get(0).getName());
        Assert.assertNull(result.get(0).getValue());
    }

    @Test
    public void parseFhirQueryParams_operationWithQueryParams_returnsOperationPlusParams() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("Patient/$everything?patient=123");
        Assert.assertEquals(2, result.size());
        Assert.assertEquals("$everything", result.get(0).getOperation());
        Assert.assertNull(result.get(0).getName());
        Assert.assertEquals("patient", result.get(1).getName());
        Assert.assertEquals("123", result.get(1).getValue());
        Assert.assertNull(result.get(1).getOperation());
    }

    @Test
    public void parseFhirQueryParams_fullUrlWithOperation_returnsOperationPlusParams() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams(
                "http://example.com/fhir/Patient/$everything?patient=123&start=2020-01-01");
        Assert.assertEquals(3, result.size());
        Assert.assertEquals("$everything", result.get(0).getOperation());
        Assert.assertEquals("patient", result.get(1).getName());
        Assert.assertEquals("start", result.get(2).getName());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Handled flag defaults to false
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirQueryParams_handledDefaultsFalse() {
        final List<FhirQueryParam> result = utils.parseFhirQueryParams("Observation?code=123");
        Assert.assertFalse(result.get(0).isHandled());
    }
}
