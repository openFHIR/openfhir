package com.syntaric.openfhir.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the bootstrap directory scan once, at startup. All logic lives in {@link BootstrapService}, which is also
 * what {@code POST /$bootstrap} invokes.
 */
@Component
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    private final BootstrapService bootstrapService;

    @Autowired
    public BootstrapRunner(final BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public void run(final ApplicationArguments args) {
        bootstrapService.runBootstrap("bootstrap-req");
    }
}
