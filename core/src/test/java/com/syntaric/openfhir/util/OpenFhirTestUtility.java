package com.syntaric.openfhir.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class OpenFhirTestUtility {

    public static ObjectMapper getYaml() {
        final YAMLMapper mapper = new YAMLMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // skip nulls
        return mapper;
    }


}
