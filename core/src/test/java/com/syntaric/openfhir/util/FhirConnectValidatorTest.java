package com.syntaric.openfhir.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.jupiter.api.Test;

class FhirConnectValidatorTest {

    @Test
    void validateAgainstModelSchema() throws IOException {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.readValue(
                getClass().getResourceAsStream("/kds/core/projects/org.highmed/KDS/diagnose/KDS_problem_diagnose.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertTrue(strings.isEmpty());
    }

    @Test
    void validateAgainstModelCondition() throws IOException {
        final ObjectMapper yaml = OpenFhirTestUtility.getYaml();
        final FhirConnectModel fhirConnectModel = yaml.readValue(
                getClass().getResourceAsStream("/growth_chart/body-height.model.yml"),
                FhirConnectModel.class);
        final List<String> strings = new FhirConnectValidator().validateAgainstModelSchema(fhirConnectModel);
        Assert.assertNull(strings);
    }
}