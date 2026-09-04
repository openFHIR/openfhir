package com.syntaric.openfhir.rest;

import ca.uhn.fhir.parser.DataFormatException;
import com.syntaric.openfhir.operations.OperationOutcomeFactory;
import com.syntaric.openfhir.operations.OperationRequestException;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Translates exceptions from the operations endpoints into OperationOutcome responses, per the FHIRconnect REST
 * API spec's error contract. Scoped to {@link FhirOperationsController} — the legacy {@code /openfhir/*}
 * endpoints keep their plain-text error bodies.
 */
@RestControllerAdvice(assignableTypes = FhirOperationsController.class)
@Slf4j
public class FhirOperationExceptionHandler {

    private final OperationOutcomeFactory operationOutcomeFactory;
    private final FhirContextRegistry fhirContextRegistry;

    public FhirOperationExceptionHandler(final OperationOutcomeFactory operationOutcomeFactory,
                                         final FhirContextRegistry fhirContextRegistry) {
        this.operationOutcomeFactory = operationOutcomeFactory;
        this.fhirContextRegistry = fhirContextRegistry;
    }

    @ExceptionHandler(OperationRequestException.class)
    public ResponseEntity<String> handleOperationRequestException(final OperationRequestException e) {
        return respond(e.getStatus(), operationOutcomeFactory.error(e.getIssueType(), e.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleResponseStatusException(final ResponseStatusException e) {
        return respond(e.getStatusCode(),
                operationOutcomeFactory.error(OperationOutcome.IssueType.PROCESSING, e.getReason()));
    }

    @ExceptionHandler(DataFormatException.class)
    public ResponseEntity<String> handleDataFormatException(final DataFormatException e) {
        return respond(HttpStatus.BAD_REQUEST,
                operationOutcomeFactory.error(OperationOutcome.IssueType.STRUCTURE, e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(final IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST,
                operationOutcomeFactory.error(OperationOutcome.IssueType.PROCESSING, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(final Exception e) {
        log.error("Unexpected error handling a FHIR operation request", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                operationOutcomeFactory.error(OperationOutcome.IssueType.EXCEPTION, e.getMessage()));
    }

    private ResponseEntity<String> respond(final HttpStatusCode status, final OperationOutcome outcome) {
        final String encoded = fhirContextRegistry.getDefaultContext().newJsonParser()
                .encodeResourceToString(outcome);
        return ResponseEntity.status(status)
                .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                .body(encoded);
    }
}
