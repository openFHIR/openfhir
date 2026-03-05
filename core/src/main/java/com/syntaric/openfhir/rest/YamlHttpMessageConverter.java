package com.syntaric.openfhir.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.stereotype.Service;

@Service
public class YamlHttpMessageConverter extends AbstractHttpMessageConverter<Object> {

    @Autowired
    private ObjectMapper yamlParser;

    public YamlHttpMessageConverter() {
        super(MediaType.valueOf("application/x-yaml"),
              MediaType.valueOf("application/yaml"),
              MediaType.valueOf("text/plain"));
    }

    @Override
    protected boolean supports(Class<?> clazz) {
        return true;
    }

    @Override
    protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException {
        final InputStream body = inputMessage.getBody();
        final String string = IOUtils.toString(body, StandardCharsets.UTF_8);
        return yamlParser.readValue(string, clazz);
    }

    @Override
    protected void writeInternal(Object o, HttpOutputMessage outputMessage) throws IOException {
        outputMessage.getBody().write(yamlParser.writeValueAsBytes(o));
    }
}
