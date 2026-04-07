package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for FHIR Connect specific models, namely for state configuration of the openFHIR Engine
 */
@RestController
@ConditionalOnMissingBean(FhirConnectControllerMarker.class)
@Tag(name = "FHIR Connect API", description = "Operations related to FHIR Connect models (context and model mappings)")
public class FhirConnectController {

    private final FhirConnectManager fhirConnectManager;

    @Autowired
    public FhirConnectController(final FhirConnectManager fhirConnectManager) {
        this.fhirConnectManager = fhirConnectManager;
    }

    /**
     * creation of the model mapper
     *
     * @return 200 OK if context is successfully created with the created Context entity; 400 BAD REQUEST if validation
     *         of the given YAML failed and is not according to specification or semantic validation; 500 INTERNAL
     *         SERVER
     *         ERROR if an unknown error occurred.
     */
    @PostMapping(value = "/fc/model", consumes = {"application/json", "application/x-yaml", "text/plain"}, produces = {
            "application/json", "application/x-yaml"})
    @Operation(
            summary = "Create a new FHIR Connect model mapper",
            description = "Creates a new FHIR Connect model mapper and returns a 200 OK if operation completely successfully. Payload can either be YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = FhirConnectModel.class)))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR Connect model mapper that you want to create",
                    content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = FhirConnectModel.class)),
                            @Content(mediaType = "application/x-yaml", schema = @Schema(implementation = FhirConnectModel.class))
                    }
            )
    )
    ResponseEntity newModel(@RequestBody FhirConnectModel yamlMapper,
                            @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            final FhirConnectModelEntity body = fhirConnectManager.upsertModelMapper(yamlMapper, null, reqId);
            return ResponseEntity.ok(body.getFhirConnectModel());
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping(value = "/fc/model/{id}", consumes = {"application/json", "application/x-yaml",
            "text/plain"}, produces = {
            "application/json", "application/x-yaml"})
    @Operation(
            summary = "Updates an existing FHIR Connect model mapper",
            description = "Updates an existing FHIR Connect model mapper and returns a 200 OK if operation completely successfully. Payload can either be YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = FhirConnectModel.class)))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR Connect model mapper that you want to update",
                    content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = FhirConnectModel.class)),
                            @Content(mediaType = "application/x-yaml", schema = @Schema(implementation = FhirConnectModel.class))
                    }
            )
    )
    ResponseEntity updateModel(@PathVariable String id,
                               @RequestBody FhirConnectModel yamlMapper,
                               @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Id must no be empty when updating.");
        }
        try {
            return ResponseEntity.ok(fhirConnectManager.upsertModelMapper(yamlMapper, id, reqId).getFhirConnectModel());
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/fc/model", produces = {"application/json", "application/x-yaml"})
    @Operation(
            summary = "Get all existing FHIR Connect model mappers",
            description = "Returns all existing FHIR Connect model mappers for a specific user (logged in). Returned payload is either YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = FhirConnectModel.class)
                    )))
            }
    )
    List<FhirConnectModel> userModelMappers(
            @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return fhirConnectManager.allUserModelMappers(reqId);
    }

    @GetMapping(value = "/fc/model/{id}", produces = {"application/json", "application/x-yaml"})
    @Operation(
            summary = "Read a specific FHIR Connect model mapper",
            description = "Returns a specific FHIR Connect model mapper. Returned payload is either YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = FhirConnectModel.class)))
            }
    )
    FhirConnectModel readModelMapper(@RequestHeader(value = "x-req-id", required = false) final String reqId,
                                     @PathVariable String id) {
        return fhirConnectManager.readModelMappers(id);
    }

    /**
     * creation of the context mapper
     *
     * @return 200 OK if context is successfully created with the created Context entity; 400 BAD REQUEST if validation
     *         of the given YAML failed and is not according to specification or semantic validation; 500 INTERNAL
     *         SERVER
     *         ERROR if an unknown error occurred.
     */
    @PostMapping(value = "/fc/context", consumes = {"application/json", "application/x-yaml",
            "text/plain"}, produces = {
            "application/json", "application/x-yaml"})
    @Operation(
            summary = "Create a new FHIR Connect context mapper",
            description = "Creates a new FHIR Connect context mapper and returns a 200 OK if operation completely successfully. Payload can either be YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = FhirConnectContext.class)))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR Connect context mapper that you want to create",
                    content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = FhirConnectContext.class)),
                            @Content(mediaType = "application/x-yaml", schema = @Schema(implementation = FhirConnectContext.class))
                    }
            )
    )
    ResponseEntity newContext(@RequestBody FhirConnectContext yamlMapper,
                              @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            final FhirConnectContextEntity body = fhirConnectManager.upsertContextMapper(yamlMapper, null, reqId);
            return ResponseEntity.ok(body.getFhirConnectContext());
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping(value = "/fc/context/{id}", consumes = {"application/json", "application/x-yaml",
            "text/plain"}, produces = {
            "application/json", "application/x-yaml"})
    @Operation(
            summary = "Updates an existing FHIR Connect context mapper",
            description = "Updates an existing FHIR Connect context mapper and returns a 200 OK if operation completely successfully. Payload can either be YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = FhirConnectContext.class)))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR Connect context mapper that you want to update",
                    content = {
                            @Content(mediaType = "application/json", schema = @Schema(implementation = FhirConnectContext.class)),
                            @Content(mediaType = "application/x-yaml", schema = @Schema(implementation = FhirConnectContext.class))
                    }
            )
    )
    ResponseEntity updateContext(@PathVariable String id, @RequestBody FhirConnectContext yamlMapper,
                                 @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Id must no be empty when updating.");
        }
        try {
            final FhirConnectContextEntity body = fhirConnectManager.upsertContextMapper(yamlMapper, id, reqId);
            return ResponseEntity.ok(body.getFhirConnectContext());
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/fc/context", produces = {"application/json", "application/x-yaml"})
    @Operation(
            summary = "Get all existing FHIR Connect context mappers",
            description = "Returns all existing FHIR Connect context mappers for a specific user (logged in). Returned payload is either YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = FhirConnectContext.class)
                    )))
            }
    )
    List<FhirConnectContext> userContextMappers(
            @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return fhirConnectManager.allUserContextMappers(reqId);
    }

    @GetMapping(value = "/fc/context/{id}", produces = {"application/json", "application/x-yaml"})
    @Operation(
            summary = "Read a specific FHIR Connect context mapper",
            description = "Returns a specific FHIR Connect context mapper. Returned payload is either YAML or JSON and needs to be correctly specified in the Content-Type/Accept header.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = FhirConnectContext.class)))
            }
    )
    FhirConnectContext readContextMapper(@RequestHeader(value = "x-req-id", required = false) final String reqId,
                                         @PathVariable String id) {
        return fhirConnectManager.readContextMappers(id);
    }

}
