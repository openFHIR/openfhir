package com.syntaric.openfhir;

import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Data
public class OpenFhirContextRepository {
    private Map<String, List<OpenFhirFhirConnectModelMapper>> mappers;
    /**
     * Resolved start archetype of the context this repository was built from. Only set for sub-contexts,
     * where it is the entry point when the context is entered via slotContext.
     */
    private String start;
    /**
     * Names of the sub-contexts declared (context.contexts) by the context this repository was built from.
     */
    private List<String> contexts;
}
