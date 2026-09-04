package com.syntaric.openfhir.operations;

import ca.uhn.fhir.context.FhirContext;
import com.syntaric.openfhir.OpenFhirEngine;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Implements the FHIRconnect REST API operations {@code $tofhir} and {@code $toopenehr}: parses/validates the
 * request, invokes the engine, applies the context-semantics post-processing passes (subject population,
 * Provenance generation) and packages collected mapping issues into the response.
 * <p>
 * The operation envelope (Parameters/Bundle/OperationOutcome/Provenance) is pinned to FHIR R4 — the FHIRconnect
 * IG is R4. Context mappings targeting other FHIR versions still work for the plain mapping payload, but the
 * R4-only post-processing passes (Provenance, subject population, warnings entry) are skipped for them.
 */
@Service
@Slf4j
public class FhirOperationsService {

    private final OpenFhirEngine openFhirEngine;
    private final SubjectReferencePopulator subjectReferencePopulator;
    private final ProvenanceGenerator provenanceGenerator;
    private final OperationOutcomeFactory operationOutcomeFactory;
    private final FhirContextRegistry fhirContextRegistry;

    public FhirOperationsService(final OpenFhirEngine openFhirEngine,
                                 final SubjectReferencePopulator subjectReferencePopulator,
                                 final ProvenanceGenerator provenanceGenerator,
                                 final OperationOutcomeFactory operationOutcomeFactory,
                                 final FhirContextRegistry fhirContextRegistry) {
        this.openFhirEngine = openFhirEngine;
        this.subjectReferencePopulator = subjectReferencePopulator;
        this.provenanceGenerator = provenanceGenerator;
        this.operationOutcomeFactory = operationOutcomeFactory;
        this.fhirContextRegistry = fhirContextRegistry;
    }

    /**
     * Executes {@code $tofhir}: maps the openEHR Composition to a FHIR Bundle, fills empty subject/patient
     * references, appends the Provenance entry and, when mapping issues were collected, an OperationOutcome
     * entry. Returns the encoded Bundle.
     */
    public String toFhir(final ToFhirOperationRequest request) {
        validateToFhirRequest(request);

        final MappingIssueCollector issueCollector = new MappingIssueCollector();
        final IBaseBundle mapped = openFhirEngine.toFhirBundle(request.getComposition(), request.getTemplateId(),
                request.getCallContext(), issueCollector);

        if (mapped instanceof Bundle r4Bundle) {
            subjectReferencePopulator.populate(r4Bundle, request.getCallContext());
            provenanceGenerator.generate(r4Bundle, request.getCallContext(), request.getTemplateId());
            if (!issueCollector.isEmpty()) {
                final OperationOutcome outcome = operationOutcomeFactory.fromIssues(issueCollector.getIssues());
                r4Bundle.addEntry(new Bundle.BundleEntryComponent()
                        .setFullUrl("urn:uuid:" + UUID.randomUUID())
                        .setResource(outcome));
            }
        } else {
            // non-R4 context mapping: the envelope post-processing (Provenance/OperationOutcome) is R4-only
            log.warn("Mapped bundle is {} — skipping R4-only Provenance/OperationOutcome post-processing.",
                    mapped.getStructureFhirVersionEnum());
        }

        return contextFor(mapped).newJsonParser().encodeResourceToString(mapped);
    }

    /**
     * Executes {@code $toopenehr}: maps the FHIR Resource/Bundle to an openEHR Composition and wraps it in a
     * Parameters resource with a {@code composition} parameter plus an optional {@code outcome}
     * (OperationOutcome) parameter carrying reported gaps. Returns the encoded Parameters.
     */
    public String toOpenEhr(final String fhirResource, final String templateId, final String format) {
        if (StringUtils.isBlank(fhirResource)) {
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.REQUIRED,
                    "Request body must be a FHIR Bundle (or single resource) to map to openEHR.");
        }
        final boolean flat = OperationRequestParser.FORMAT_FLAT.equalsIgnoreCase(format);

        final MappingIssueCollector issueCollector = new MappingIssueCollector();
        final String composition = openFhirEngine.toOpenEhr(fhirResource, templateId, flat, issueCollector);

        final Parameters response = new Parameters();
        response.addParameter().setName(OperationRequestParser.PARAM_COMPOSITION)
                .setValue(new StringType(composition));
        if (!issueCollector.isEmpty()) {
            response.addParameter().setName("outcome")
                    .setResource(operationOutcomeFactory.fromIssues(issueCollector.getIssues()));
        }
        return fhirContextRegistry.getDefaultContext().newJsonParser().encodeResourceToString(response);
    }

    private void validateToFhirRequest(final ToFhirOperationRequest request) {
        final OpenFhirEngine.IncomingOpenEhrType payloadType;
        try {
            payloadType = openFhirEngine.deduceIncomingPayloadType(request.getComposition());
        } catch (final Exception e) {
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.STRUCTURE,
                    "The 'composition' parameter could not be parsed as openEHR JSON: " + e.getMessage());
        }
        if (payloadType == OpenFhirEngine.IncomingOpenEhrType.FLAT
                && StringUtils.isBlank(request.getTemplateId())) {
            throw OperationRequestException.badRequest(OperationOutcome.IssueType.REQUIRED,
                    "'templateId' is required when 'composition' is in flat format, because a flat payload cannot carry its template id inline.");
        }
    }

    private FhirContext contextFor(final IBaseBundle bundle) {
        return fhirContextRegistry.getContext(
                FhirContextRegistry.specVersionOf(bundle.getStructureFhirVersionEnum()));
    }
}
