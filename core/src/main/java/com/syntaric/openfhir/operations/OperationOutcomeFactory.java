package com.syntaric.openfhir.operations;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds R4 OperationOutcome resources from exceptions and collected mapping issues.
 * The FHIRconnect REST envelope is pinned to R4.
 */
@Component
public class OperationOutcomeFactory {

    /**
     * Single-issue OperationOutcome with severity {@code error}, used for request/processing failures.
     */
    public OperationOutcome error(final OperationOutcome.IssueType issueType, final String diagnostics) {
        final OperationOutcome outcome = new OperationOutcome();
        outcome.addIssue()
                .setSeverity(OperationOutcome.IssueSeverity.ERROR)
                .setCode(issueType)
                .setDiagnostics(diagnostics);
        return outcome;
    }

    /**
     * OperationOutcome carrying the non-fatal issues collected during a mapping run (warnings about skipped or
     * unmappable elements alongside a partial result).
     */
    public OperationOutcome fromIssues(final List<MappingIssueCollector.MappingIssue> issues) {
        final OperationOutcome outcome = new OperationOutcome();
        for (final MappingIssueCollector.MappingIssue issue : issues) {
            outcome.addIssue()
                    .setSeverity(severityFromCode(issue.severity()))
                    .setCode(issueTypeFromCode(issue.code()))
                    .setDiagnostics(issue.diagnostics());
        }
        return outcome;
    }

    private OperationOutcome.IssueSeverity severityFromCode(final String severity) {
        try {
            return OperationOutcome.IssueSeverity.fromCode(severity);
        } catch (final Exception e) {
            return OperationOutcome.IssueSeverity.WARNING;
        }
    }

    private OperationOutcome.IssueType issueTypeFromCode(final String code) {
        try {
            return OperationOutcome.IssueType.fromCode(code);
        } catch (final Exception e) {
            return OperationOutcome.IssueType.INCOMPLETE;
        }
    }
}
