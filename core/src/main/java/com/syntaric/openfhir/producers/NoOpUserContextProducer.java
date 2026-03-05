package com.syntaric.openfhir.producers;

import com.syntaric.openfhir.OpenFhirAuthorizationContext;

public class NoOpUserContextProducer implements UserContextProducerInterface {

    @Override
    public OpenFhirAuthorizationContext getAuthContext() {
        final OpenFhirAuthorizationContext context = new OpenFhirAuthorizationContext();
        context.setTenant("default");
        context.setUserId("default");
        return context;
    }
}
