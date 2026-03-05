package com.syntaric.openfhir;

import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Data
public class OpenFhirContextRepository {
    private Map<String, List<OpenFhirFhirConnectModelMapper>> mappers;
    private OPERATIONALTEMPLATE operationaltemplate;
    private WebTemplate webTemplate;
}
