package com.syntaric.openfhir.util;

import ca.uhn.fhir.parser.DataFormatException;
import com.syntaric.openfhir.mapping.MappingContext;
import com.syntaric.openfhir.operations.OperationRequestException;
import lombok.Getter;

import java.util.UUID;

/**
 * A runtime failure inside a single mapping, wrapped together with the {@link MappingContext} the engine was in.
 * The message names the model mapper, archetype, mapping and paths so the failure can be located in the mapper
 * without a local reproduction.
 *
 * <p>The cause decides how much is echoed. A caller-correctable cause ({@link #isCallerError}) keeps its
 * message, because it describes the input or the mapper. Anything else is an engine fault: the message only
 * carries the cause's class name and a reference id, and the detail stays in the engine log.
 *
 * <p>This is also the one place the caller-vs-engine classification lives; the issue collector and the REST
 * exception handler both consult it.
 */
@Getter
public class MappingExecutionException extends RuntimeException {

    private final MappingContext context;
    private final String reference;

    public MappingExecutionException(final MappingContext context, final Throwable cause) {
        this(context, cause, UUID.randomUUID().toString());
    }

    MappingExecutionException(final MappingContext context, final Throwable cause, final String reference) {
        super(buildMessage(context, cause, reference), cause);
        this.context = context;
        this.reference = reference;
    }

    /**
     * True when the cause describes something the caller can correct: their input (HAPI parse failures), the
     * mapper (illegal arguments raised while interpreting it) or the request itself.
     */
    public static boolean isCallerError(final Throwable cause) {
        return cause instanceof IllegalArgumentException
                || cause instanceof DataFormatException
                || cause instanceof OperationRequestException;
    }

    /**
     * The OperationOutcome issue code for a failure with the given cause: {@code structure} for input that does
     * not parse, {@code processing} for other caller errors, {@code exception} for engine faults.
     */
    public static String issueCode(final Throwable cause) {
        if (cause instanceof DataFormatException) {
            return "structure";
        }
        if (isCallerError(cause)) {
            return "processing";
        }
        return "exception";
    }

    public boolean isCallerError() {
        return isCallerError(getCause());
    }

    public String issueCode() {
        return issueCode(getCause());
    }

    private static String buildMessage(final MappingContext context, final Throwable cause, final String reference) {
        final String detail;
        if (isCallerError(cause)) {
            detail = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        } else {
            detail = String.format("%s (reference %s; see the engine log)",
                    cause == null ? "unknown error" : cause.getClass().getSimpleName(), reference);
        }
        return String.format("Failed to execute %s while mapping %s: %s",
                context.describe(), context.directionLabel(), detail);
    }
}
