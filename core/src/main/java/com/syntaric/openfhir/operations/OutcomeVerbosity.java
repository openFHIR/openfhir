package com.syntaric.openfhir.operations;

import java.util.Arrays;
import java.util.Locale;

/**
 * How much the {@code $tofhir} / {@code $toopenehr} operations report back in their OperationOutcome
 * ({@code openfhir.operations.outcome-verbosity}). Issues below the configured level are never collected, so the
 * response payload stays small; the engine log is not affected by this setting.
 */
public enum OutcomeVerbosity {

    /**
     * No OperationOutcome at all: neither the Bundle entry nor the {@code outcome} parameter is produced.
     */
    NONE,

    /**
     * Only {@code error} issues (mappings whose execution failed); warnings about skipped or unmappable elements are
     * dropped.
     */
    ERRORS,

    /**
     * Everything: errors and warnings (the default).
     */
    ALL;

    public static final String PROPERTY = "openfhir.operations.outcome-verbosity";

    /**
     * Parses the configured value case-insensitively.
     *
     * @throws IllegalArgumentException naming the accepted values when the value is not one of them
     */
    public static OutcomeVerbosity fromConfig(final String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(
                    "Invalid value '%s' for %s; expected one of %s", value, PROPERTY,
                    Arrays.toString(values()).toLowerCase(Locale.ROOT)), e);
        }
    }

    /**
     * True when an issue of the given severity ({@code error} / {@code warning} / ...) is reported at this level.
     */
    public boolean includes(final String severity) {
        return switch (this) {
            case NONE -> false;
            case ERRORS -> MappingIssueCollector.SEVERITY_ERROR.equals(severity)
                    || "fatal".equals(severity);
            case ALL -> true;
        };
    }
}
