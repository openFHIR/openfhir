package com.syntaric.openfhir.operations;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * Builds the engine-generated Provenance resource the FHIRconnect REST API spec requires in every
 * {@code $tofhir} response Bundle: {@code target} covers every mapped entry, {@code agent.who} is the caller's
 * {@code context.who} or the configured engine Device, and a {@code source} entity carries the mapping inputs
 * (templateId, ehr_id) when known. The Provenance is appended as the last Bundle entry.
 * <p>
 * Applied on {@code $tofhir} only — legacy/direct endpoints stay byte-stable.
 */
@Component
@Slf4j
public class ProvenanceGenerator {

    public static final String PARTICIPANT_TYPE_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/provenance-participant-type";
    public static final String TEMPLATE_ID_IDENTIFIER_SYSTEM = "urn:openfhir:templateId";
    public static final String EHR_ID_IDENTIFIER_SYSTEM = "urn:openfhir:ehrId";

    private final String deviceReference;
    private final String deviceDisplay;

    public ProvenanceGenerator(
            @Value("${openfhir.provenance.device-reference:Device/openfhir-engine}") final String deviceReference,
            @Value("${openfhir.provenance.device-display:openFHIR engine}") final String deviceDisplay) {
        this.deviceReference = deviceReference;
        this.deviceDisplay = deviceDisplay;
    }

    /**
     * Appends a Provenance describing the transformation to the given (R4) Bundle and returns it.
     * Entries without a resolvable reference get a {@code urn:uuid:} fullUrl assigned so they can be targeted;
     * pre-existing fullUrls (e.g. from {@code _bundleMetadata}-driven document bundles) are left untouched.
     */
    public Provenance generate(final Bundle bundle, final MappingCallContext callContext, final String templateId) {
        final Provenance provenance = new Provenance();
        provenance.setRecorded(new Date());

        for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            provenance.addTarget(new Reference(targetReferenceFor(entry)));
        }

        final Provenance.ProvenanceAgentComponent agent = provenance.addAgent();
        agent.setType(new CodeableConcept(new Coding(PARTICIPANT_TYPE_SYSTEM, "assembler", "Assembler")));
        if (StringUtils.isNotBlank(callContext.getWhoRef())) {
            agent.setWho(new Reference(callContext.getWhoRef()));
        } else {
            agent.setWho(new Reference(deviceReference).setDisplay(deviceDisplay));
        }
        if (StringUtils.isNotBlank(callContext.getOnBehalfOfRef())) {
            agent.setOnBehalfOf(new Reference(callContext.getOnBehalfOfRef()));
        }

        if (StringUtils.isNotBlank(templateId)) {
            addSourceEntity(provenance, TEMPLATE_ID_IDENTIFIER_SYSTEM, templateId);
        }
        if (StringUtils.isNotBlank(callContext.getEhrId())) {
            addSourceEntity(provenance, EHR_ID_IDENTIFIER_SYSTEM, callContext.getEhrId());
        }

        bundle.addEntry(new Bundle.BundleEntryComponent()
                .setFullUrl("urn:uuid:" + UUID.randomUUID())
                .setResource(provenance));
        return provenance;
    }

    private String targetReferenceFor(final Bundle.BundleEntryComponent entry) {
        if (StringUtils.isNotBlank(entry.getFullUrl())) {
            return entry.getFullUrl();
        }
        final Resource resource = entry.getResource();
        if (resource != null && resource.getIdElement() != null
                && StringUtils.isNotBlank(resource.getIdElement().getIdPart())) {
            final String idPart = resource.getIdElement().getIdPart();
            if (idPart.startsWith("urn:")) {
                return idPart;
            }
            return resource.fhirType() + "/" + idPart;
        }
        final String generatedFullUrl = "urn:uuid:" + UUID.randomUUID();
        entry.setFullUrl(generatedFullUrl);
        return generatedFullUrl;
    }

    private void addSourceEntity(final Provenance provenance, final String identifierSystem, final String value) {
        provenance.addEntity()
                .setRole(Provenance.ProvenanceEntityRole.SOURCE)
                .setWhat(new Reference().setIdentifier(new Identifier()
                        .setSystem(identifierSystem)
                        .setValue(value)));
    }
}
