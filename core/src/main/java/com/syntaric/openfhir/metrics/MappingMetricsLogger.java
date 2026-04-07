package com.syntaric.openfhir.metrics;

/**
 * Central sink for toFhir mapping performance metrics.
 *
 * <p>Swap the active Spring bean to redirect all timing output without touching any mapping code.
 * Built-in implementations:
 * <ul>
 *   <li>{@link Slf4jMappingMetricsLogger} – logs via SLF4J (default)</li>
 * </ul>
 * Custom implementations (e.g. file writer, Micrometer, OpenTelemetry) can be provided as
 * alternative Spring beans — annotate the desired one with {@code @Primary} or use a Spring
 * profile to activate it.
 */
public interface MappingMetricsLogger {

    /**
     * Records the elapsed time for a named section of the toFhir pipeline.
     *
     * @param section   logical name of the timed section (e.g. "toFhir.total", "flatJson.marshal")
     * @param context   additional context to disambiguate the measurement (e.g. template id, archetype, mapping name)
     * @param elapsedMs elapsed wall-clock time in milliseconds
     */
    void record(String section, String context, long elapsedMs);
}
