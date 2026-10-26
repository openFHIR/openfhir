package com.syntaric.openfhir.mapping;

/**
 * Extension point notified around every mapping the engine executes, in both directions and whichever endpoint
 * the mapping came from. Deployments add listeners by registering beans of this type; the engine calls all of them,
 * in their {@link org.springframework.core.annotation.Order}. A listener implements only the callbacks it needs.
 */
public interface MappingListenerInterface {

    /**
     * Called once the context mapper and the operational template of a mapping are resolved and validated, before
     * any mapping work. Throwing refuses the mapping: the exception fails the request.
     */
    default void beforeMapping(final MappingEvent event) {
    }

    /**
     * Called once after a successful mapping. Must be fast; anything it throws is logged and never fails the
     * mapping.
     */
    default void afterMapping(final MappingEvent event) {
    }
}
