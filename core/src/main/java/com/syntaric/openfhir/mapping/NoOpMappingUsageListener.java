package com.syntaric.openfhir.mapping;

import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.operations.MappingCallContext;

/**
 * Default {@link MappingUsageListenerInterface} that neither refuses nor records any mapping.
 */
public class NoOpMappingUsageListener implements MappingUsageListenerInterface {

    @Override
    public void beforeMapping(final MappingDirection direction, final FhirConnectContext context,
                              final String templateId, final MappingCallContext callContext) {
    }

    @Override
    public void mapped(final MappingDirection direction, final FhirConnectContext context,
                       final MappingCallContext callContext) {
    }
}
