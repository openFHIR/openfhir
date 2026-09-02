package com.syntaric.openfhir.operations;

import java.util.Optional;

/**
 * Default {@link PatientResolverInterface} that resolves nothing; in that case only a caller-supplied
 * {@code context.patient} reference is used for subject population.
 */
public class NoOpPatientResolver implements PatientResolverInterface {

    @Override
    public Optional<String> resolve(final String ehrId) {
        return Optional.empty();
    }
}
