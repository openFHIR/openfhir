package com.syntaric.openfhir.operations;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fills only <b>empty</b> top-level {@code subject}/{@code patient} Reference children of mapped Bundle entries
 * with the patient reference from the call context. Strictly those two child names are considered, to avoid
 * over-reach on e.g. {@code performer}. Resolution order per the spec: caller-supplied {@code context.patient}
 * takes precedence, then engine-side {@link PatientResolverInterface} resolution of {@code ehr_id}; when neither
 * yields a reference the Bundle is left untouched.
 */
@Component
@Slf4j
public class SubjectReferencePopulator {

    private static final List<String> SUBJECT_CHILD_NAMES = List.of("subject", "patient");

    private final PatientResolverInterface patientResolver;

    public SubjectReferencePopulator(final PatientResolverInterface patientResolver) {
        this.patientResolver = patientResolver;
    }

    public void populate(final Bundle bundle, final MappingCallContext callContext) {
        final String patientReference = resolvePatientReference(callContext);
        if (StringUtils.isBlank(patientReference)) {
            return;
        }
        for (final Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            final Resource resource = entry.getResource();
            if (resource == null) {
                continue;
            }
            for (final Property property : resource.children()) {
                if (!SUBJECT_CHILD_NAMES.contains(property.getName())
                        || property.getTypeCode() == null
                        || !property.getTypeCode().startsWith("Reference")) {
                    continue;
                }
                if (property.hasValues() && property.getValues().stream().anyMatch(v -> !v.isEmpty())) {
                    continue; // already populated by the mapping — never overwrite
                }
                resource.setProperty(property.getName(), new Reference(patientReference));
            }
        }
    }

    private String resolvePatientReference(final MappingCallContext callContext) {
        if (StringUtils.isNotBlank(callContext.getPatientRef())) {
            return callContext.getPatientRef();
        }
        if (StringUtils.isNotBlank(callContext.getEhrId())) {
            return patientResolver.resolve(callContext.getEhrId()).orElse(null);
        }
        return null;
    }
}
