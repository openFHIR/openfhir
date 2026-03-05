package com.syntaric.openfhir.producers;

import com.syntaric.openfhir.OpenFhirAuthorizationContext;

public interface UserContextProducerInterface {

    OpenFhirAuthorizationContext getAuthContext();
}
