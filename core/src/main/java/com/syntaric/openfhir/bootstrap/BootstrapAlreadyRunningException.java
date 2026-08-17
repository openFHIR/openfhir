package com.syntaric.openfhir.bootstrap;

/**
 * Thrown when a bootstrap scan is requested while another one is still in progress.
 */
public class BootstrapAlreadyRunningException extends RuntimeException {

    public BootstrapAlreadyRunningException(final String message) {
        super(message);
    }
}
