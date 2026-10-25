package com.syntaric.openfhir.mapping;

import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.operations.MappingCallContext;

/**
 * Extension point notified around every mapping the engine executes, in both directions and whichever endpoint
 * the mapping came from. Deployments plug in their own implementation by registering a bean of this type; the
 * default is {@link NoOpMappingUsageListener}.
 */
public interface MappingUsageListenerInterface {

    /**
     * Called once the context mapper and the operational template of a mapping are resolved and validated, before
     * any mapping work. Throwing refuses the mapping: the exception fails the request.
     *
     * @param context     the context mapper the mapping runs with
     * @param templateId  id of the operational template, as the context mapper declares it
     * @param callContext caller-supplied call context; never null, but possibly empty
     */
    void beforeMapping(MappingDirection direction, FhirConnectContext context, String templateId,
                       MappingCallContext callContext);

    /**
     * Called once per successful mapping. Must be fast and must not throw; anything it throws is logged and does
     * not fail the mapping.
     *
     * @param context     the context mapper the mapping ran with
     * @param callContext caller-supplied call context; never null, but possibly empty
     */
    void mapped(MappingDirection direction, FhirConnectContext context, MappingCallContext callContext);
}
