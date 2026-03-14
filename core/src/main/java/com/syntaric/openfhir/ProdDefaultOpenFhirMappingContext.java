package com.syntaric.openfhir;

import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.db.repository.FhirConnectModelRepository;
import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.util.FhirConnectModelMerger;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * RequestScoped cache of all the needed information for mapping (Context mappers, Model mappers and parsed
 * OPERATIONALTEMPLATE and WebTemplate. This is requested multiple time throughout the mapping but except for the first
 * time, should only be taken from cache directly to avoid performance issues
 */
@Component
@RequestScope
@Slf4j
public class ProdDefaultOpenFhirMappingContext extends OpenFhirMappingContext {

    private final FhirConnectModelRepository fhirConnectModelRepository;

    @Autowired
    public ProdDefaultOpenFhirMappingContext(final FhirConnectModelRepository fhirConnectModelRepository,
                                             final FhirConnectModelMerger modelMerger) {
        super(modelMerger);
        this.fhirConnectModelRepository = fhirConnectModelRepository;
    }

    public void initMappingCache(final FhirConnectContext context,
                                 final OPERATIONALTEMPLATE operationaltemplate,
                                 final WebTemplate webTemplate,
                                 final String tenant) {
        final String templateId = context.getContext().getTemplate().getId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized", normalizedRepoId);
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();
        fhirContextRepo.setOperationaltemplate(operationaltemplate);
        fhirContextRepo.setWebTemplate(webTemplate);

        final List<OpenFhirFhirConnectModelMapper> openFhirFhirConnectModelMappers = prepareJoinedModels(
                context.getContext(), tenant);

        final Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = new HashMap<>();

        openFhirFhirConnectModelMappers.forEach(mapperEntity -> {
            final String archetype = mapperEntity.getOpenEhrConfig().getArchetype();
            final String mappingName = mapperEntity.getName();
            if (mapperEntity.getFhirConfig() == null) {
                return;
            }
            if (!mappers.containsKey(mappingName)) {
                mappers.put(mappingName, new ArrayList<>());
            }
            if (!mappers.containsKey(archetype)) {
                mappers.put(archetype, new ArrayList<>());
            }
            mappers.get(mappingName).add(mapperEntity);
            if (!mappingName.equals(archetype)) {
                mappers.get(archetype).add(mapperEntity);
            }

        });

        fhirContextRepo.setMappers(mappers);

        final String start = context.getContext().getStart();
        if (start != null) {
            final List<OpenFhirFhirConnectModelMapper> archetypeMappers = mappers.get(start);
            if (archetypeMappers == null) {
                context.getContext().setStart(start);
            } else {
                final String overridenMapper = archetypeMappers.get(0).getOpenEhrConfig()
                        .getArchetype();
                context.getContext().setStart(overridenMapper);
            }
        }

        repository.put(normalizedRepoId, fhirContextRepo);
    }

    public List<OpenFhirFhirConnectModelMapper> prepareJoinedModels(final Context context, final String tenant) {
        // now load mappings
        final List<FhirConnectModelEntity> modelEntities = fhirConnectModelRepository.findByTenantAndName(
                context.getArchetypes(), tenant);
        if (modelEntities == null || modelEntities.isEmpty()) {
            log.error("Couldn't find any model entities that would match {}", context.getArchetypes());
            throw new IllegalArgumentException("Couldn't find any model entities for this template.");
        }

        final List<FhirConnectModel> coreModels = modelEntities.stream()
                .map(FhirConnectModelEntity::getFhirConnectModel)
                .collect(Collectors.toList());
        final List<FhirConnectModel> extensionsModels = loadExtensions(context.getExtensions(), tenant);
        return modelMerger.joinModelMappers(coreModels, extensionsModels);
    }

    private List<FhirConnectModel> loadExtensions(final List<String> extensions, final String org) {
        if (extensions == null || extensions.isEmpty()) {
            log.debug("No extensions defined.");
            return null;
        }
        final List<FhirConnectModelEntity> extensionEntities = fhirConnectModelRepository.findByTenantAndName(
                extensions, org);
        if (extensionEntities == null || extensionEntities.isEmpty()) {
            log.error("Couldn't find extension model mappers ({}) in the database.", extensions);
            throw new IllegalArgumentException("Couldn't find defined extension model mappers in the database.");
        }
        return extensionEntities.stream()
                .map(FhirConnectModelEntity::getFhirConnectModel)
                .collect(Collectors.toList());
    }

}
