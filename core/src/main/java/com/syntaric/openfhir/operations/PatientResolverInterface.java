package com.syntaric.openfhir.operations;

import java.util.Optional;

/**
 * Extension point for engine-side EHR-ID→patient resolution (MPI/demographics lookup), which the FHIRconnect
 * REST API spec prefers over caller-supplied patient references. Deployments plug in their own implementation
 * by registering a bean of this type; the default is {@link NoOpPatientResolver}.
 */
public interface PatientResolverInterface {

    /**
     * Resolves the given openEHR EHR-ID to a FHIR patient reference string (e.g. {@code Patient/123}).
     *
     * @return the reference, or empty when the patient cannot be resolved
     */
    Optional<String> resolve(String ehrId);
}
