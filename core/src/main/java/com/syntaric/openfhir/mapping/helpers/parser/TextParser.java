package com.syntaric.openfhir.mapping.helpers.parser;

import com.google.gson.JsonObject;

import com.syntaric.openfhir.mapping.helpers.DataWithIndex;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TextParser {

    private final FhirValueReaders fhirValueReaders;

    @Autowired
    public TextParser(FhirValueReaders readers) {
        this.fhirValueReaders = readers;
    }

    public DataWithIndex string(JsonObject valueHolder,
                                Integer lastIndex,
                                String path) {

        String v = fhirValueReaders.get(valueHolder, path);
        if (StringUtils.isNotEmpty(v)) {
            return new DataWithIndex(new StringType(v), lastIndex, path);
        }
        return null;
    }
}
