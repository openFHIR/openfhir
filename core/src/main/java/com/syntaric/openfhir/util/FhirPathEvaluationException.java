package com.syntaric.openfhir.util;

import lombok.Getter;

/**
 * A FHIRPath expression from a model mapping could not be evaluated against the input, e.g. because it uses an
 * unknown function or its {@code resolve()} target cannot be found. Raised by the FHIR → openEHR path evaluation
 * so the mapping loop can report it as a warning naming the mapping and the expression; the FHIRPath engine's
 * message describes the expression, not engine internals, so it is safe to pass on.
 */
@Getter
public class FhirPathEvaluationException extends RuntimeException {

    private final String fhirPath;

    public FhirPathEvaluationException(final String fhirPath, final Throwable cause) {
        super(String.format("FHIRPath '%s' could not be evaluated: %s", fhirPath,
                cause == null || cause.getMessage() == null ? "unknown error" : cause.getMessage()), cause);
        this.fhirPath = fhirPath;
    }
}
