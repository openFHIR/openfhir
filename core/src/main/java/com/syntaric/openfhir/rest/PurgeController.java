package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.db.repository.FhirConnectContextRepository;
import com.syntaric.openfhir.db.repository.FhirConnectModelRepository;
import com.syntaric.openfhir.db.repository.OptRepository;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnMissingBean(PurgeControllerMarker.class)
@Tag(name = "Purging", description = "Operations related purging the state of the engine")
public class PurgeController implements PurgeControllerMarker {


    private final OptRepository optRepository;
    private final FhirConnectModelRepository fhirConnectMapperRepository;
    private final FhirConnectContextRepository fhirConnectContextRepository;
    private final UserContextProducerInterface userContextProducer;

    @Autowired
    public PurgeController(final OptRepository optRepository,
                           final FhirConnectModelRepository fhirConnectMapperRepository,
                           final FhirConnectContextRepository fhirConnectContextRepository,
                           final UserContextProducerInterface userContextProducer) {
        this.optRepository = optRepository;
        this.fhirConnectMapperRepository = fhirConnectMapperRepository;
        this.fhirConnectContextRepository = fhirConnectContextRepository;
        this.userContextProducer = userContextProducer;
    }

    @GetMapping("/$purge")
    @Operation(
            summary = "Deletes whole state of the engine belonging to the logged in user",
            description = "Deletes the entire state of the engine for the logged-in user, including operational templates, context mappers, model mappers, and concept maps. This action is irreversible.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "No content")
            }
    )
    ResponseEntity purge(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            optRepository.deleteAllTenant(userContextProducer.getAuthContext().getTenant());
            fhirConnectMapperRepository.deleteAllTenant(userContextProducer.getAuthContext().getTenant());
            fhirConnectContextRepository.deleteAllTenant(userContextProducer.getAuthContext().getTenant());
        } catch (final Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
