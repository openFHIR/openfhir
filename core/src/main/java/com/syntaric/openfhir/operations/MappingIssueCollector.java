package com.syntaric.openfhir.operations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects non-fatal issues (skipped or unmappable elements) raised during a single mapping run so they can be
 * surfaced to the caller as an OperationOutcome instead of being silently dropped. One instance per request;
 * not shared across threads.
 */
public class MappingIssueCollector {

    /**
     * A single issue; field values mirror OperationOutcome.issue (severity/code/diagnostics).
     */
    public record MappingIssue(String severity, String code, String diagnostics) {
    }

    public static final String SEVERITY_WARNING = "warning";
    public static final String CODE_INCOMPLETE = "incomplete";

    private final List<MappingIssue> issues = new ArrayList<>();

    /**
     * Reports an element that was skipped or could not be mapped (severity {@code warning},
     * code {@code incomplete}).
     */
    public void addWarning(final String diagnostics) {
        issues.add(new MappingIssue(SEVERITY_WARNING, CODE_INCOMPLETE, diagnostics));
    }

    public void add(final String severity, final String code, final String diagnostics) {
        issues.add(new MappingIssue(severity, code, diagnostics));
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }

    public List<MappingIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}
