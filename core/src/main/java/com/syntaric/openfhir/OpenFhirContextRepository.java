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
}
