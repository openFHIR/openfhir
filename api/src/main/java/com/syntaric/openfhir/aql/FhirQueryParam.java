package com.syntaric.openfhir.aql;

import lombok.Data;

@Data
public class FhirQueryParam {
    private final String name;
    private final String value;
    private final String operation;
    private boolean handled;
}
