package com.syntaric.openfhir;

import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.util.FhirConnectModelMerger;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RequestScoped cache of all the needed information for mapping (Context mappers, Model mappers and parsed
 * OPERATIONALTEMPLATE and WebTemplate. This is requested multiple time throughout the mapping but except for the first
 * time, should only be taken from cache directly to avoid performance issues
 */
@Component
@RequestScope
@Slf4j
public class ProdDefaultOpenFhirMappingContext extends OpenFhirMappingContext {

    private final FhirConnectManager fhirConnectManager;

    @Autowired
    public ProdDefaultOpenFhirMappingContext(final FhirConnectManager fhirConnectManager,
                                             final FhirConnectModelMerger modelMerger) {
        super(modelMerger);
        this.fhirConnectManager = fhirConnectManager;
    }

    public void initMappingCache(final FhirConnectContext context) {
        final String templateId = context.getContext().getTemplate().getId();
        final String normalizedRepoId = normalizeTemplateId(templateId);
        if (repository.containsKey(normalizedRepoId)) {
            log.info("Repository for template {} already initialized", normalizedRepoId);
            return;
        }
        final OpenFhirContextRepository fhirContextRepo = new OpenFhirContextRepository();

        final Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = groupMappers(
                prepareJoinedModels(context.getContext()));

        fhirContextRepo.setMappers(mappers);
        fhirContextRepo.setContexts(context.getContext().getContexts());

        context.getContext().setStart(resolveStart(mappers, context.getContext().getStart()));

        registerSubContexts(context, tenantContextByName(), this::prepareJoinedModels);

        repository.put(normalizedRepoId, fhirContextRepo);
    }

    /**
     * Finds a context by its metadata.name among the tenant's contexts; these are only loaded when a context
     * actually references a sub-context, and then only once.
     */
    private Function<String, FhirConnectContext> tenantContextByName() {
        final List<FhirConnectContextEntity> tenantContexts = new ArrayList<>();
        final boolean[] loaded = {false};
        return name -> {
            if (!loaded[0]) {
                final List<FhirConnectContextEntity> all = fhirConnectManager.allUserContextEntities();
                if (all != null) {
                    tenantContexts.addAll(all);
                }
                loaded[0] = true;
            }
            return tenantContexts.stream()
                    .map(FhirConnectContextEntity::getFhirConnectContext)
                    .filter(c -> c != null && c.getMetadata() != null && name.equals(c.getMetadata().getName()))
                    .findFirst()
                    .orElse(null);
        };
    }

    public List<OpenFhirFhirConnectModelMapper> prepareJoinedModels(final Context context) {
        // now load mappings
        final List<FhirConnectModelEntity> modelEntities = fhirConnectManager.findModelsByNames(
                context.getArchetypes());
        if (modelEntities == null || modelEntities.isEmpty()) {
            log.error("Couldn't find any model entities that would match {}", context.getArchetypes());
            throw new IllegalArgumentException("Couldn't find any model entities for this template.");
        }

        final List<FhirConnectModel> coreModels = modelEntities.stream()
                .map(FhirConnectModelEntity::getFhirConnectModel)
                .collect(Collectors.toList());
        final List<FhirConnectModel> extensionsModels = loadExtensions(context.getExtensions());
        return modelMerger.joinModelMappers(coreModels, extensionsModels);
    }

    private List<FhirConnectModel> loadExtensions(final List<String> extensions) {
        if (extensions == null || extensions.isEmpty()) {
            log.debug("No extensions defined.");
            return null;
        }
        final List<FhirConnectModelEntity> extensionEntities = fhirConnectManager.findModelsByNames(extensions);
        if (extensionEntities == null || extensionEntities.isEmpty()) {
            log.error("Couldn't find extension model mappers ({}) in the database.", extensions);
            throw new IllegalArgumentException("Couldn't find defined extension model mappers in the database.");
        }
        return extensionEntities.stream()
                .map(FhirConnectModelEntity::getFhirConnectModel)
                .collect(Collectors.toList());
    }

}
