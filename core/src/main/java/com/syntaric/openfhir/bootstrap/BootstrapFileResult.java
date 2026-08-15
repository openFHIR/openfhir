package com.syntaric.openfhir.bootstrap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Outcome of bootstrapping a single file, as reported by {@link BootstrapService#runBootstrap(String)}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BootstrapFileResult {

    /**
     * Path of the file relative to the configured bootstrap dir, separators normalized to '/'.
     */
    private String path;

    /**
     * CREATED, UPDATED, UNCHANGED or FAILED.
     */
    private String outcome;

    /**
     * MODEL, CONTEXT or OPT; null if the file failed before it could be classified.
     */
    private String entityType;

    /**
     * Engine-assigned id of the created/updated/unchanged entity; null on FAILED.
     */
    private String entityId;

    /**
     * Failure message; null unless the outcome is FAILED.
     */
    private String message;
}
