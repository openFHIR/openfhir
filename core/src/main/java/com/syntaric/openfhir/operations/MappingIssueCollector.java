package com.syntaric.openfhir.operations;

import com.syntaric.openfhir.util.MappingExecutionException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the issues raised during a single mapping run so they can be surfaced to the caller as an
 * OperationOutcome instead of being silently dropped: warnings about skipped or unmappable elements, and errors
 * for mappings whose execution failed (the engine records the error and carries on with the remaining mappings,
 * so a partial result coexists with the issues). One instance per request; not shared across threads.
 *
 * <p>An {@link OutcomeVerbosity} decides which severities are kept; issues below it are discarded on arrival so
 * the response does not grow with them. The engine still logs everything.
 *
 * <p>Callers that never read the collector should use {@link #failFast()}: it throws the first error as a
 * {@link MappingExecutionException} instead of recording it, so a failure cannot go unnoticed.
 */
public class MappingIssueCollector {

    /**
     * A single issue; field values mirror OperationOutcome.issue (severity/code/diagnostics).
     */
    public record MappingIssue(String severity, String code, String diagnostics) {
    }

    public static final String SEVERITY_WARNING = "warning";
    public static final String SEVERITY_ERROR = "error";
    public static final String CODE_INCOMPLETE = "incomplete";
    public static final String CODE_EXCEPTION = "exception";
    public static final String CODE_PROCESSING = "processing";
    public static final String CODE_STRUCTURE = "structure";

    private final List<MappingIssue> issues = new ArrayList<>();
    private final OutcomeVerbosity verbosity;
    private final boolean failFast;

    /**
     * A lenient collector reporting everything: errors are recorded alongside warnings and the mapping run
     * continues.
     */
    public MappingIssueCollector() {
        this(OutcomeVerbosity.ALL);
    }

    /**
     * A lenient collector that keeps only the severities the given verbosity includes.
     */
    public MappingIssueCollector(final OutcomeVerbosity verbosity) {
        this(verbosity, false);
    }

    private MappingIssueCollector(final OutcomeVerbosity verbosity, final boolean failFast) {
        this.verbosity = verbosity == null ? OutcomeVerbosity.ALL : verbosity;
        this.failFast = failFast;
    }

    /**
     * A collector for callers that do not read it back: warnings are still recorded, but the first
     * {@link #addError(MappingExecutionException) error} is thrown instead.
     */
    public static MappingIssueCollector failFast() {
        return new MappingIssueCollector(OutcomeVerbosity.ALL, true);
    }

    /**
     * Reports an element that was skipped or could not be mapped (severity {@code warning},
     * code {@code incomplete}).
     */
    public void addWarning(final String diagnostics) {
        add(SEVERITY_WARNING, CODE_INCOMPLETE, diagnostics);
    }

    /**
     * Reports a mapping whose execution failed (severity {@code error}). The issue code follows the exception's
     * classification of its cause: {@code processing} / {@code structure} when the caller can correct it,
     * {@code exception} for an engine fault. In {@link #failFast() fail-fast} mode the exception is thrown instead.
     */
    public void addError(final MappingExecutionException e) {
        if (failFast) {
            throw e;
        }
        add(SEVERITY_ERROR, e.issueCode(), e.getMessage());
    }

    /**
     * Records an issue, unless the collector's verbosity excludes its severity.
     */
    public void add(final String severity, final String code, final String diagnostics) {
        if (!verbosity.includes(severity)) {
            return;
        }
        issues.add(new MappingIssue(severity, code, diagnostics));
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(issue -> SEVERITY_ERROR.equals(issue.severity()));
    }

    public boolean isFailFast() {
        return failFast;
    }

    public OutcomeVerbosity getVerbosity() {
        return verbosity;
    }

    public List<MappingIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}
