package com.syntaric.openfhir.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OpenFhirMapperUtilsParseFhirResourceTypeTest {

    private OpenFhirMapperUtils utils;

    @Before
    public void setUp() {
        utils = new OpenFhirMapperUtils();
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Full URLs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirResourceType_fullUrlWithParams_returnsResourceType() {
        Assert.assertEquals("Observation", utils.parseFhirResourceType("http://something.com/fhir/Observation?code=123"));
    }

    @Test
    public void parseFhirResourceType_fullUrlNoParams_returnsResourceType() {
        Assert.assertEquals("Observation", utils.parseFhirResourceType("http://something.com/fhir/Observation"));
    }

    @Test
    public void parseFhirResourceType_fullUrlMultipleParams_returnsResourceType() {
        Assert.assertEquals("Condition", utils.parseFhirResourceType("https://example.org/fhir/Condition?code=abc&category=lab"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Relative (no host) inputs
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirResourceType_relativeWithParams_returnsResourceType() {
        Assert.assertEquals("Observation", utils.parseFhirResourceType("Observation?code=123"));
    }

    @Test
    public void parseFhirResourceType_relativeNoParams_returnsResourceType() {
        Assert.assertEquals("Condition", utils.parseFhirResourceType("Condition"));
    }

    @Test
    public void parseFhirResourceType_relativeWithMultipleParams_returnsResourceType() {
        Assert.assertEquals("AllergyIntolerance", utils.parseFhirResourceType("AllergyIntolerance?patient=123&category=food"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Operation
    // -----------------------------------------------------------------------------------------------------------------

    @Test
    public void parseFhirResourceType_operationNoParams_returnsOperation() {
        Assert.assertEquals("$summary", utils.parseFhirResourceType("Patient/123/$summary"));
    }

    @Test
    public void parseFhirResourceType_operationWithParams_returnsOperation() {
        Assert.assertEquals("$everything", utils.parseFhirResourceType("Patient/$everything?patient=123"));
    }

    @Test
    public void parseFhirResourceType_fullUrlWithOperation_returnsOperation() {
        Assert.assertEquals("$summary", utils.parseFhirResourceType("http://example.com/fhir/Patient/$summary"));
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Blank / null — should throw
    // -----------------------------------------------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void parseFhirResourceType_blank_throws() {
        utils.parseFhirResourceType("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFhirResourceType_empty_throws() {
        utils.parseFhirResourceType("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFhirResourceType_null_throws() {
        utils.parseFhirResourceType(null);
    }
}
