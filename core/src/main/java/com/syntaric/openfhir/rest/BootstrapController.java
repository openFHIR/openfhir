package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.bootstrap.BootstrapAlreadyRunningException;
import com.syntaric.openfhir.bootstrap.BootstrapService;
import com.syntaric.openfhir.bootstrap.BootstrapSummary;
import com.syntaric.openfhir.db.entity.BootstrapEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnMissingBean(BootstrapControllerMarker.class)
@Tag(name = "Bootstrap", description = "Operations related to (re-)running the bootstrap directory scan")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    @Autowired
    public BootstrapController(final BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping(value = "/bootstrap", produces = "application/json")
    @Operation(
            summary = "Lists the bootstrap ledger",
            description = "Returns the bootstrap entries of the logged-in user: one per file that has been bootstrapped so far, recording the file's path relative to the bootstrap directory, a hash of the content that was applied, and the id and type of the entity it created.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The bootstrap entries", content = @Content(array = @ArraySchema(schema = @Schema(implementation = BootstrapEntity.class))))
            }
    )
    ResponseEntity listBootstrapped(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            return ResponseEntity.ok(bootstrapService.allOfTenant());
        } catch (final Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping(value = "/$bootstrap", produces = "application/json")
    @Operation(
            summary = "Re-runs the bootstrap directory scan",
            description = "Re-scans the configured bootstrap directory without requiring a restart. Files not seen before are created, files whose content changed since they were last bootstrapped are updated in place (keeping the id of the entity they originally created), and unchanged files are skipped. Files that were bootstrapped before but are no longer on disk are reported in the log; their entities are left untouched.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Summary of the bootstrap run"),
                    @ApiResponse(responseCode = "409", description = "A bootstrap run is already in progress")
            }
    )
    ResponseEntity runBootstrap(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            final BootstrapSummary summary = bootstrapService.runBootstrap(reqId == null ? "bootstrap-req" : reqId);
            return ResponseEntity.ok(summary);
        } catch (final BootstrapAlreadyRunningException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (final Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }
}
