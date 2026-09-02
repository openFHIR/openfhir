package com.syntaric.openfhir.operations;

import lombok.Builder;
import lombok.Value;

/**
 * Parsed and merged (body + query parameters) input of a {@code POST /$tofhir} invocation.
 */
@Value
@Builder
public class ToFhirOperationRequest {

    /**
     * Stringified openEHR Composition, flat or canonical.
     */
    String composition;
    String templateId;
    MappingCallContext callContext;
}
