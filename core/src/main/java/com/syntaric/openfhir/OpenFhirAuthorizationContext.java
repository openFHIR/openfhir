package com.syntaric.openfhir;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("request")
public class OpenFhirAuthorizationContext {

    @Getter
    @Setter
    private String tenant;

    @Getter
    @Setter
    private String userId;
}
