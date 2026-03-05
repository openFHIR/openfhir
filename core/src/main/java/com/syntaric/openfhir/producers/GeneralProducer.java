package com.syntaric.openfhir.producers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.Gson;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class GeneralProducer {
    @Bean
    public Gson gson() {
        return new Gson();
    }

    @Bean
    public ObjectMapper yamlObjectMapper() {
        final YAMLMapper mapper = new YAMLMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // skip nulls
        return mapper;
    }
}
