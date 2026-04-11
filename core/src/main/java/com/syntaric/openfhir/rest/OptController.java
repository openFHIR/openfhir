package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.db.entity.OptEntity;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnMissingBean(OptControllerMarker.class)
@Tag(name = "Operational Template (openEHR) API", description = "Operations related to Operational Template as one of the states of the engine (if engine isn't integrated with an openEHR CDR directly)")
public class OptController {

    private final OptManager optManager;

    @Autowired
    public OptController(final OptManager optManager) {
        this.optManager = optManager;
    }

    /**
     * creates an operational template as part of the state configuration of the engine
     *
     * @param opt payload that is the operational template
     * @param reqId request id that will be logged
     * @return OptEntity without the actual context of the template, just the metadata and the assigned database ID
     */
    @PostMapping(value = "/opt", produces = {
            "application/json"}, consumes = {"application/xml", "text/xml", "text/plain"})
    @Operation(
            summary = "Create a new Operational Template",
            description = "Creates a new Operational Template and returns a 200 OK if operation completely successfully.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = OptEntity.class)))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Operational Template that you want to create",
                    content = {
                            @Content(mediaType = "application/xml")
                    }
            )
    )
    ResponseEntity newOpt(@RequestBody String opt,
                          @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            final OptEntity body = optManager.upsert(opt, null, reqId);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }


    @PutMapping(value = "/opt/{id}", produces = "application/json", consumes = {"application/xml", "text/xml",
            "text/plain"})
    @Operation(
            summary = "Updates an existing Operational Template",
            description = "Updates an existing Operational Template and returns a 200 OK if operation completely successfully.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Created", content = @Content(schema = @Schema(implementation = OptEntity.class)))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Operational Template that you want to update with",
                    content = {
                            @Content(mediaType = "application/xml")
                    }
            )
    )
    ResponseEntity newOpt(@PathVariable String id, @RequestBody String opt,
                          @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Id must no be empty when updating.");
        }
        try {
            final OptEntity body = optManager.upsert(opt, id, reqId);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping(value = "/opt/{id}")
    @Operation(
            summary = "Delete an existing Operational Template",
            description = "Deletes an existing Operational Template by its ID.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Deleted")
            }
    )
    ResponseEntity deleteOpt(@PathVariable String id,
                             @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        optManager.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * returns all existing operational templates in the database, but without the actual content of it (to avoid
     * huge payloads being returned). If you want to get a specific template and it's content, trigger a GET on a
     * specific
     * operational template id.
     *
     * @param reqId request id that will be logged
     * @return returns all existing operational templates without the content
     */
    @GetMapping(value = "/opt", produces = "application/json")
    @Operation(
            summary = "Get all existing Operational Templates currently in the engine (metadata only)",
            description = "Returns metadata of all existing Operational Templates currently in the engine for a specific user (logged in).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = OptEntity.class)
                    )))
            }
    )
    List<OptEntity> usersOpts(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return optManager.allOfUser(reqId);
    }

    /**
     * reads a specific operational template with content
     *
     * @param id id of the template as it was assigned to the OptEntity at the time of the creation
     * @return a specific operational template with content
     */
    @GetMapping(value = "/opt/{id}")
    @Operation(
            summary = "Returns a specific Operational Template (actual template)",
            description = "Returns a specific Operational Template (actual template)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK")
            }
    )
    ResponseEntity<String> read(@PathVariable String id) {
        final String content = optManager.getContent(id);
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(content);
    }

    /**
     * reads a specific operational template with content
     *
     * @param templateId id of the template as it was assigned to the OptEntity at the time of the creation
     * @return a specific operational template with content
     */
    @GetMapping(value = "/opt")
    @Operation(
            summary = "Returns a specific Operational Template (actual template)",
            description = "Returns a specific Operational Template (actual template)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK")
            }
    )
    ResponseEntity<String> find(@RequestParam(required = false) String templateId) {
        final String content = optManager.getContentByTemplateId(templateId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(content);
    }

}
