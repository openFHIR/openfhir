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

    /**
     * The flag is set in a {@code finally} block on purpose: a scan that blows up (an unparseable fixture, say) has
     * still finished, and leaving the application permanently un-ready over it would turn a bad mapping file into an
     * outage. Individual file failures are already reported in the summary and the log.
     */
    @Override
    public void run(final ApplicationArguments args) {
        try {
            bootstrapService.runBootstrap("bootstrap-req");
        } finally {
            bootstrapService.markStartupScanComplete();
        }
    }
}
