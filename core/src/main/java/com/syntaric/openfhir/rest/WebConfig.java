package com.syntaric.openfhir.rest;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Slf4j
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // without this, two yaml converters are present, one being applied even for application/json content types
        // for some reason
        Optional<HttpMessageConverter<?>> yamlConverter = converters.stream()
                .filter(c -> c instanceof AbstractJackson2HttpMessageConverter &&
                        ((AbstractJackson2HttpMessageConverter) c).getObjectMapper() instanceof YAMLMapper)
                .findFirst();

        yamlConverter.ifPresent(converters::remove);
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}
