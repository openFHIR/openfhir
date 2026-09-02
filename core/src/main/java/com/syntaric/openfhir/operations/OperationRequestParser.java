package com.syntaric.openfhir.operations;

import ca.uhn.fhir.parser.DataFormatException;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parses the {@code $tofhir} Parameters body (including the nested {@code context} parameter) and merges query
 * parameters into it. Per the FHIRconnect REST API spec, the body takes precedence over query parameters on
 * conflict; query parameters only fill fields missing from the body.
 */
@Component
@Slf4j
public class OperationRequestParser {

    public static final String PARAM_COMPOSITION = "composition";
    public static final String PARAM_TEMPLATE_ID = "templateId";
    public static final String PARAM_CONTEXT = "context";
    public static final String PART_EHR_ID = "ehr_id";
    public static final String PART_PATIENT = "patient";
    public static final String PART_WHO = "who";
    public static final String PART_ON_BEHALF_OF = "onBehalfOf";

    public static final String FORMAT_CANONICAL = "canonical";
    public static final String FORMAT_FLAT = "flat";

    private final FhirContextRegistry fhirContextRegistry;

    public OperationRequestParser(final FhirContextRegistry fhirContextRegistry) {
        this.fhirContextRegistry = fhirContextRegistry;
    }

    /**
     * Parses the body of a {@code POST /$tofhir} request and merges the query-parameter aliases into it.
     *
     * @param parametersBody the FHIR Parameters resource (R4, JSON) sent as request body
     * @param templateId     {@code templateId} query parameter, used only when the body carries none
     * @param ehrId          {@code ehr_id} query parameter (alias of context.ehr_id)
     * @param patient        {@code patient} query parameter (alias of context.patient)
     * @param who            {@code who} query parameter (alias of context.who)
     * @param onBehalfOf     {@code onBehalfOf} query parameter (alias of context.onBehalfOf)
     * @param requestId      the {@code x-req-id} header value
     */
    public ToFhirOperationRequest parseToFhirRequest(final String parametersBody,
                                                     final String templateId,
                                                     final String ehrId,
                                                     final String patient,
                                                     final String who,
                                                     final String onBehalfOf,
                                                     final String requestId) {
        if (StringUtils.isBlank(parametersBody)) {
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.REQUIRED,
                    "Request body must be a FHIR Parameters resource with a 'composition' parameter.");
        }
        final Parameters parameters = parseParameters(parametersBody);

        final String composition = stringValue(findParameter(parameters, PARAM_COMPOSITION));
        if (StringUtils.isBlank(composition)) {
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.REQUIRED,
                    "Missing required parameter 'composition' (valueString holding the openEHR Composition).");
        }

        final String bodyTemplateId = stringValue(findParameter(parameters, PARAM_TEMPLATE_ID));

        final Parameters.ParametersParameterComponent context = findParameter(parameters, PARAM_CONTEXT);
        final String bodyEhrId = stringValue(findPart(context, PART_EHR_ID));
        final String bodyPatient = referenceValue(findPart(context, PART_PATIENT));
        final String bodyWho = referenceValue(findPart(context, PART_WHO));
        final String bodyOnBehalfOf = referenceValue(findPart(context, PART_ON_BEHALF_OF));

        final MappingCallContext callContext = MappingCallContext.builder()
                .ehrId(firstNonBlank(bodyEhrId, ehrId))
                .patientRef(firstNonBlank(bodyPatient, patient))
                .whoRef(firstNonBlank(bodyWho, who))
                .onBehalfOfRef(firstNonBlank(bodyOnBehalfOf, onBehalfOf))
                .requestId(requestId)
                .build();

        return ToFhirOperationRequest.builder()
                .composition(composition)
                .templateId(firstNonBlank(bodyTemplateId, templateId))
                .callContext(callContext)
                .build();
    }

    /**
     * Validates the {@code format} query parameter of {@code $toopenehr} and returns the effective value
     * (default {@code canonical}).
     */
    public String parseFormat(final String format) {
        if (StringUtils.isBlank(format)) {
            return FORMAT_CANONICAL;
        }
        if (!FORMAT_CANONICAL.equalsIgnoreCase(format) && !FORMAT_FLAT.equalsIgnoreCase(format)) {
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.INVALID,
                    String.format("Invalid 'format' value '%s'; must be one of: canonical, flat.", format));
        }
        return format.toLowerCase();
    }

    private Parameters parseParameters(final String body) {
        try {
            return fhirContextRegistry.getDefaultContext().newJsonParser().parseResource(Parameters.class, body);
        } catch (final DataFormatException e) {
            log.warn("Could not parse $tofhir request body as a Parameters resource", e);
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.STRUCTURE,
                    "Request body could not be parsed as a FHIR (R4) Parameters resource: " + e.getMessage());
        }
    }

    private Parameters.ParametersParameterComponent findParameter(final Parameters parameters, final String name) {
        return parameters.getParameter().stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    private Parameters.ParametersParameterComponent findPart(
            final Parameters.ParametersParameterComponent parameter, final String name) {
        if (parameter == null) {
            return null;
        }
        final List<Parameters.ParametersParameterComponent> parts = parameter.getPart();
        return parts.stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    private String stringValue(final Parameters.ParametersParameterComponent parameter) {
        if (parameter == null || parameter.getValue() == null) {
            return null;
        }
        final Type value = parameter.getValue();
        if (value instanceof StringType stringType) {
            return stringType.getValue();
        }
        return value.primitiveValue();
    }

    private String referenceValue(final Parameters.ParametersParameterComponent parameter) {
        if (parameter == null || parameter.getValue() == null) {
            return null;
        }
        final Type value = parameter.getValue();
        if (value instanceof Reference reference) {
            return reference.getReference();
        }
        return value.primitiveValue();
    }

    private String firstNonBlank(final String preferred, final String fallback) {
        return StringUtils.isNotBlank(preferred) ? preferred : StringUtils.trimToNull(fallback);
    }
}
