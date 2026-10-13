package com.syntaric.openfhir;

import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.util.FhirConnectModelMerger;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class OpenFhirMappingContext {
    @Getter
    Map<String, OpenFhirContextRepository> repository = new HashMap<>();

    private static final String SUB_CONTEXT_KEY_PREFIX = "context:";

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

    /**
     * Registry key of a sub-context. Sub-contexts are keyed by context name rather than template id, because
     * several sub-contexts may declare the same template id (and the same one as their parent).
     */
    public static String subContextKey(final String contextName) {
        return normalizeTemplateId(SUB_CONTEXT_KEY_PREFIX + contextName);
    }

    /**
     * Returns the model mappers of the start archetype of a registered sub-context, resolved with that
     * sub-context's own archetypes and extensions. Null if no such sub-context has been registered.
     */
    public List<OpenFhirFhirConnectModelMapper> getStartMappersForContext(final String contextName) {
        final String key = subContextKey(contextName);
        final OpenFhirContextRepository subContextRepo = repository.get(key);
        if (subContextRepo == null || subContextRepo.getStart() == null) {
            return null;
        }
        return getMapperForArchetype(key, subContextRepo.getStart());
    }

    /**
     * Resolved start archetype of a registered sub-context, or null if it has not been registered.
     */
    public String getStartOfContext(final String contextName) {
        final OpenFhirContextRepository subContextRepo = repository.get(subContextKey(contextName));
        return subContextRepo == null ? null : subContextRepo.getStart();
    }

    /**
     * Whether the context registered under the given key (a template id or a sub-context key) lists the given
     * sub-context under its 'contexts'.
     */
    public boolean declaresSubContext(final String repositoryKey, final String contextName) {
        final OpenFhirContextRepository repo = repository.get(normalizeTemplateId(repositoryKey));
        return repo != null && repo.getContexts() != null && repo.getContexts().contains(contextName);
    }

    /**
     * Groups joined model mappers by their name and by the archetype they map, which is how they are looked up
     * when a slot is followed.
     */
    protected static Map<String, List<OpenFhirFhirConnectModelMapper>> groupMappers(
            final List<OpenFhirFhirConnectModelMapper> joinedMappers) {
        final Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = new HashMap<>();
        joinedMappers.forEach(mapperEntity -> {
            final String archetype = mapperEntity.getOpenEhrConfig().getArchetype();
            final String mappingName = mapperEntity.getName();
            if (mapperEntity.getFhirConfig() == null) {
                return;
            }
            mappers.computeIfAbsent(mappingName, k -> new ArrayList<>()).add(mapperEntity);
            if (!mappingName.equals(archetype)) {
                mappers.computeIfAbsent(archetype, k -> new ArrayList<>()).add(mapperEntity);
            }
        });
        return mappers;
    }

    /**
     * A context's start may reference a model mapper by name; resolves it to the archetype that mapper maps.
     */
    protected static String resolveStart(final Map<String, List<OpenFhirFhirConnectModelMapper>> mappers,
                                         final String start) {
        if (start == null) {
            return null;
        }
        final List<OpenFhirFhirConnectModelMapper> archetypeMappers = mappers.get(start);
        return archetypeMappers == null ? start : archetypeMappers.get(0).getOpenEhrConfig().getArchetype();
    }

    /**
     * Registers every sub-context the given context lists under 'contexts' (and theirs, recursively) as its
     * own repository under {@link #subContextKey(String)}, so a slotContext resolves its models from the
     * sub-context's archetypes and extensions rather than from the referencing context's.
     *
     * @param context context whose sub-contexts are to be registered
     * @param subContextByName finds a context by its metadata.name; returns null if there is none
     * @param mappersOfContext loads and joins the model mappers of a context
     * @throws IllegalArgumentException if a listed sub-context can't be found or the references are cyclic
     */
    protected void registerSubContexts(final FhirConnectContext context,
                                       final Function<String, FhirConnectContext> subContextByName,
                                       final Function<Context, List<OpenFhirFhirConnectModelMapper>> mappersOfContext) {
        final Set<String> path = new LinkedHashSet<>();
        if (context.getMetadata() != null && context.getMetadata().getName() != null) {
            path.add(context.getMetadata().getName());
        }
        registerSubContexts(context, subContextByName, mappersOfContext, path);
    }

    private void registerSubContexts(final FhirConnectContext context,
                                     final Function<String, FhirConnectContext> subContextByName,
                                     final Function<Context, List<OpenFhirFhirConnectModelMapper>> mappersOfContext,
                                     final Set<String> path) {
        final List<String> subContextNames = context.getContext().getContexts();
        if (subContextNames == null) {
            return;
        }
        for (final String subContextName : subContextNames) {
            if (path.contains(subContextName)) {
                throw new IllegalArgumentException(
                        "Cyclic context reference: " + String.join(" -> ", path) + " -> " + subContextName);
            }
            final String key = subContextKey(subContextName);
            if (repository.containsKey(key)) {
                continue; // already registered (and its own sub-contexts with it)
            }
            final FhirConnectContext subContext = subContextByName.apply(subContextName);
            if (subContext == null) {
                throw new IllegalArgumentException(
                        "Couldn't find sub-context '" + subContextName + "' listed under contexts of '"
                                + (context.getMetadata() == null ? null : context.getMetadata().getName()) + "'.");
            }
            final Map<String, List<OpenFhirFhirConnectModelMapper>> mappers = groupMappers(
                    mappersOfContext.apply(subContext.getContext()));
            final OpenFhirContextRepository subContextRepo = new OpenFhirContextRepository();
            subContextRepo.setMappers(mappers);
            subContextRepo.setStart(resolveStart(mappers, subContext.getContext().getStart()));
            subContextRepo.setContexts(subContext.getContext().getContexts());

            path.add(subContextName);
            registerSubContexts(subContext, subContextByName, mappersOfContext, path);
            path.remove(subContextName);

            repository.put(key, subContextRepo);
        }
    }

    public static String normalizeTemplateId(final String templateId) {
        if (templateId == null) {
            return null;
        }
        return templateId.toLowerCase().replace(" ", "_");
    }
}
