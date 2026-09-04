package com.syntaric.openfhir.operations;

import lombok.Getter;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;

/**
 * Raised when an operation request ({@code $tofhir} / {@code $toopenehr}) is invalid; translated into an
 * OperationOutcome response by the operations exception handler.
 */
@Getter
public class OperationRequestException extends RuntimeException {

    private final HttpStatus status;
    private final OperationOutcome.IssueType issueType;

    public OperationRequestException(final HttpStatus status, final OperationOutcome.IssueType issueType,
                                     final String message) {
        super(message);
        this.status = status;
        this.issueType = issueType;
    }

    public static OperationRequestException badRequest(final OperationOutcome.IssueType issueType,
                                                       final String message) {
        return new OperationRequestException(HttpStatus.BAD_REQUEST, issueType, message);
    }
}
