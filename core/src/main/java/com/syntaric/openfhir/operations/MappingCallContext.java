package com.syntaric.openfhir.operations;

import lombok.Builder;
import lombok.Value;

/**
 * Caller-supplied context for a single mapping invocation, as defined by the FHIRconnect REST API spec's
 * {@code context} parameter on {@code $tofhir} ({@code ehr_id}, {@code patient}, {@code who}, {@code onBehalfOf}).
 * <p>
 * All reference fields are plain FHIR reference strings (e.g. {@code Patient/123} or an absolute URL).
 */
@Value
@Builder(toBuilder = true)
public class MappingCallContext {

    String ehrId;
    String patientRef;
    String whoRef;
    String onBehalfOfRef;
    String requestId;

    public static MappingCallContext empty() {
        return MappingCallContext.builder().build();
    }
}
