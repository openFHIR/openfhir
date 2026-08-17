package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.bootstrap.BootstrapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final BootstrapService bootstrapService;

    @Autowired
    public HealthController(final BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    /**
     * Reports UP only once the startup bootstrap scan has finished.
     * <p>
     * The web server accepts requests before the startup scan has run, so answering UP straight away advertises the
     * application as usable while the bootstrap ledger is still being written - callers that immediately hit
     * {@code POST /$bootstrap} collide with the startup scan and get a 409. Returning 503 until the scan is done
     * makes this endpoint usable as a "safe to send traffic" gate for orchestrators and test harnesses alike.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        if (!bootstrapService.isStartupScanComplete()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("STARTING");
        }
        return ResponseEntity.ok("UP");
    }
}
