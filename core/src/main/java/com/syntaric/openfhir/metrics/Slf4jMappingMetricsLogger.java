package com.syntaric.openfhir.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Default {@link MappingMetricsLogger} that emits timing entries via SLF4J at DEBUG level.
 *
 * <p>To redirect metrics elsewhere, provide a different {@link MappingMetricsLogger} bean
 * (annotated with {@code @Primary} or activated via a Spring profile) without modifying any
 * mapping code.
 *
 * <p>Example output:
 * <pre>
 *   [METRICS] toFhir.total           | template=blood-pressure    | 42 ms
 *   [METRICS] flatJson.marshal       | template=blood-pressure    | 3 ms
 *   [METRICS] helpers.construct      | template=blood-pressure    | 1 ms
 *   [METRICS] mapToFhir.archetype    | openEHR-EHR-OBSERVATION.bp | 38 ms
 *   [METRICS] mapping.iteration      | mapping=systolic           | 2 ms
 * </pre>
 */
@Slf4j
@Component
public class Slf4jMappingMetricsLogger implements MappingMetricsLogger {

    @Override
    public void record(final String section, final String context, final long elapsedMs) {
        log.debug("[METRICS] {}", String.format("%-30s | %-50s | %d ms", section, context, elapsedMs));
    }
}
