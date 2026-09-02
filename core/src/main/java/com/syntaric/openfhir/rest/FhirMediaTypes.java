package com.syntaric.openfhir.rest;

import org.springframework.http.MediaType;

/**
 * Media types defined by the FHIRconnect REST API spec: {@code application/fhir+json} for the FHIR envelope and
 * {@code application/openehr+json} for an unwrapped openEHR JSON payload.
 */
public final class FhirMediaTypes {

    public static final String APPLICATION_FHIR_JSON_VALUE = "application/fhir+json";
    public static final String APPLICATION_OPENEHR_JSON_VALUE = "application/openehr+json";

    public static final MediaType APPLICATION_FHIR_JSON = MediaType.valueOf(APPLICATION_FHIR_JSON_VALUE);
    public static final MediaType APPLICATION_OPENEHR_JSON = MediaType.valueOf(APPLICATION_OPENEHR_JSON_VALUE);

    private FhirMediaTypes() {
    }
}
