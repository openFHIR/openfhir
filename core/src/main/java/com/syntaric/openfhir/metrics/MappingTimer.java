package com.syntaric.openfhir.metrics;

/**
 * Lightweight stopwatch used at toFhir pipeline call-sites.
 *
 * <p>Typical usage:
 * <pre>
 *   final MappingTimer t = MappingTimer.start();
 *   doWork();
 *   metricsLogger.record("section.name", "context", t.elapsedMs());
 * </pre>
 */
public final class MappingTimer {

    private final long startNanos;

    private MappingTimer() {
        this.startNanos = System.nanoTime();
    }

    /** Creates and starts a new timer. */
    public static MappingTimer start() {
        return new MappingTimer();
    }

    /** Returns the elapsed time in milliseconds since {@link #start()} was called. */
    public long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
