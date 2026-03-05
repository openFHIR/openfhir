package com.syntaric.openfhir;

import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.util.FhirConnectModelMerger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class OpenFhirMappingContext {
    @Getter
    Map<String, OpenFhirContextRepository> repository = new HashMap<>();

    protected final FhirConnectModelMerger modelMerger;

    public OpenFhirMappingContext(final FhirConnectModelMerger modelMerger) {
        this.modelMerger = modelMerger;
    }

    /**
     * Returns a fhir connect model mapper for a specific archetype within a template.
     */
    public List<OpenFhirFhirConnectModelMapper> getMapperForArchetype(final String templateId, final String archetypeId) {
        final OpenFhirContextRepository repoForTemplate = repository.get(normalizeTemplateId(templateId));
        if (repoForTemplate == null) {
            log.warn("No repo exists for template: {}", templateId);
            return null;
        }
        final List<OpenFhirFhirConnectModelMapper> fhirConnectMapper = repoForTemplate.getMappers().get(archetypeId);
        if (fhirConnectMapper == null) {
            return null;
        }
        return fhirConnectMapper.stream().map(OpenFhirFhirConnectModelMapper::copy).collect(Collectors.toList());
    }

    public static String normalizeTemplateId(final String templateId) {
        return templateId.toLowerCase().replace(" ", "_");
    }
}
