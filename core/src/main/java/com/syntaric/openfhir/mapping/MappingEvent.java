package com.syntaric.openfhir.mapping;

import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.operations.MappingCallContext;
import lombok.Value;

/**
 * A mapping the engine executes, as {@link MappingListenerInterface}s are told about it.
 */
@Value
public class MappingEvent {

    MappingDirection direction;

    /**
     * The context mapper the mapping runs with.
     */
    FhirConnectContext context;

    /**
     * Id of the operational template, as the context mapper declares it.
     */
    String templateId;

    /**
     * Caller-supplied call context; never null, but possibly empty.
     */
    MappingCallContext callContext;
}
