package com.syntaric.openfhir.rest;

import ca.uhn.fhir.parser.DataFormatException;
import com.syntaric.openfhir.operations.OperationOutcomeFactory;
import com.syntaric.openfhir.operations.OperationRequestException;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.InvalidTemplateException;
import com.syntaric.openfhir.util.MappingExecutionException;
import com.syntaric.openfhir.util.TemplateNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

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

    /**
     * A referenced operational template that was never uploaded is caller-correctable input, so it gets a 400
     * naming the template rather than the opaque 500 a runtime failure would produce.
     */
    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<String> handleTemplateNotFoundException(final TemplateNotFoundException e) {
        return respond(HttpStatus.BAD_REQUEST,
                operationOutcomeFactory.error(OperationOutcome.IssueType.NOTFOUND, e.getMessage()));
    }

    @ExceptionHandler(InvalidTemplateException.class)
    public ResponseEntity<String> handleInvalidTemplateException(final InvalidTemplateException e) {
        log.error("Stored operational template {} could not be parsed", e.getTemplateId(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                operationOutcomeFactory.error(OperationOutcome.IssueType.EXCEPTION, e.getMessage()));
    }

    /**
     * A single mapping failed and the engine was asked to fail fast (issue #120). The message already names the
     * model mapper, archetype, mapping and paths; the cause decides the status: a caller-correctable cause is a
     * 400 with the cause's text, an engine fault is a 500 whose diagnostics carry the reference id under which
     * the engine logged the stack at the point of failure — so it is not logged again here.
     */
    @ExceptionHandler(MappingExecutionException.class)
    public ResponseEntity<String> handleMappingExecutionException(final MappingExecutionException e) {
        final HttpStatus status = e.isCallerError() ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
        return respond(status,
                operationOutcomeFactory.error(OperationOutcome.IssueType.fromCode(e.issueCode()), e.getMessage()));
    }

    /**
     * Catch-all for genuinely unexpected failures. The exception message is deliberately not echoed back: it
     * tends to carry internal class names and offsets that mean nothing to the caller, so the detail stays in
     * the log and the response only says where to look.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(final Exception e) {
        final String reference = UUID.randomUUID().toString();
        log.error("Unexpected error handling a FHIR operation request (reference {})", reference, e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR,
                operationOutcomeFactory.error(OperationOutcome.IssueType.EXCEPTION, String.format(
                        "Unexpected error while processing the request. Please contact the openFHIR support team quoting reference %s.",
                        reference)));
    }

    private ResponseEntity<String> respond(final HttpStatusCode status, final OperationOutcome outcome) {
        final String encoded = fhirContextRegistry.getDefaultContext().newJsonParser()
                .encodeResourceToString(outcome);
        return ResponseEntity.status(status)
                .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                .body(encoded);
    }
}
