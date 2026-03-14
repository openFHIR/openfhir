package com.syntaric.openfhir.aql;
import lombok.Data;

@Data
public class ToAqlRequest {
    private final String template;
    private final String ehrId;
    private final String fhirFullUrl;
}
