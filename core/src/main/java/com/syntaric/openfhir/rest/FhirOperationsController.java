package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.operations.FhirOperationsService;
import com.syntaric.openfhir.operations.OperationRequestParser;
import com.syntaric.openfhir.operations.ToFhirOperationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * FHIR Operations-framework endpoints defined by the FHIRconnect REST API spec, mounted at the server root:
 * {@code POST /$tofhir} and {@code POST /$toopenehr}. Requests and responses use the R4 FHIR envelope
 * ({@code application/fhir+json}); errors are returned as OperationOutcome via
 * {@link FhirOperationExceptionHandler}.
 * <p>
 * Note on the {@code $} in the path: some proxies/gateways percent-encode {@code $}; Spring's PathPattern
 * matches the literal character, so ensure intermediaries pass it through unchanged.
 */
@RestController
@ConditionalOnMissingBean(FhirOperationsControllerMarker.class)
@Slf4j
@Tag(name = "FHIRconnect operations API",
        description = "FHIR Operations ($tofhir, $toopenehr) as defined by the FHIRconnect REST API specification")
public class FhirOperationsController {

    private final FhirOperationsService fhirOperationsService;
    private final OperationRequestParser operationRequestParser;

    public FhirOperationsController(final FhirOperationsService fhirOperationsService,
                                    final OperationRequestParser operationRequestParser) {
        this.fhirOperationsService = fhirOperationsService;
        this.operationRequestParser = operationRequestParser;
    }

    @PostMapping(value = "/$tofhir",
            consumes = {FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Maps an openEHR Composition to FHIR ($tofhir operation)",
            description = "FHIR Operations-framework form of the openEHR→FHIR mapping. Input is a FHIR (R4) "
                    + "Parameters resource with a 'composition' parameter (stringified openEHR Composition, flat or "
                    + "canonical), an optional 'templateId' (required when the composition is flat) and an optional "
                    + "nested 'context' parameter (parts: ehr_id, patient, who, onBehalfOf). Short context fields may "
                    + "also be passed as query parameters; the body takes precedence on conflict. The response Bundle "
                    + "includes an engine-generated Provenance entry and may include an OperationOutcome entry with "
                    + "warnings about skipped/unmappable elements.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "FHIR Bundle with mapped resources, a Provenance entry and optionally an OperationOutcome entry"),
                    @ApiResponse(responseCode = "400", description = "OperationOutcome describing the request error")
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR (R4) Parameters resource",
                    content = {
                            @Content(mediaType = FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE),
                            @Content(mediaType = "application/json")
                    }
            )
    )
    ResponseEntity<String> toFhir(@RequestBody String parametersBody,
                                  @RequestParam(required = false) String templateId,
                                  @RequestParam(name = "ehr_id", required = false) String ehrId,
                                  @RequestParam(required = false) String patient,
                                  @RequestParam(required = false) String who,
                                  @RequestParam(required = false) String onBehalfOf,
                                  @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        final ToFhirOperationRequest request = operationRequestParser.parseToFhirRequest(parametersBody,
                templateId, ehrId, patient, who, onBehalfOf, reqId);
        final String bundle = fhirOperationsService.toFhir(request);
        return ResponseEntity.ok().contentType(FhirMediaTypes.APPLICATION_FHIR_JSON).body(bundle);
    }

    @PostMapping(value = "/$toopenehr",
            consumes = {FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE},
            produces = {FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(
            summary = "Maps a FHIR Bundle to an openEHR Composition ($toopenehr operation)",
            description = "FHIR Operations-framework form of the FHIR→openEHR mapping. Input is the FHIR Bundle "
                    + "itself (no Parameters wrapper; a single resource is also accepted). 'templateId' and 'format' "
                    + "(canonical|flat, default canonical) are query parameters. The response is a FHIR (R4) "
                    + "Parameters resource with a 'composition' parameter (valueString) and, when gaps were reported "
                    + "during mapping, an 'outcome' parameter holding an OperationOutcome — a partial result may "
                    + "coexist with issues.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Parameters resource with 'composition' and optional 'outcome'"),
                    @ApiResponse(responseCode = "400", description = "OperationOutcome describing the request error")
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR Bundle (or single resource)",
                    content = {
                            @Content(mediaType = FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE),
                            @Content(mediaType = "application/json")
                    }
            )
    )
    ResponseEntity<String> toOpenEhr(@RequestBody String fhirResource,
                                     @RequestParam(required = false) String templateId,
                                     @RequestParam(required = false) String format,
                                     @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        final String effectiveFormat = operationRequestParser.parseFormat(format);
        final String parameters = fhirOperationsService.toOpenEhr(fhirResource, templateId, effectiveFormat);
        return ResponseEntity.ok().contentType(FhirMediaTypes.APPLICATION_FHIR_JSON).body(parameters);
    }
}
