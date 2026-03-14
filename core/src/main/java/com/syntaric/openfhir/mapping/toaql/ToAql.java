package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.OpenFhirContextRepository;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.repository.FhirConnectContextRepository;
import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import com.syntaric.openfhir.fc.schema.model.Preprocessor;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import com.syntaric.openfhir.rest.RequestValidationException;
import com.syntaric.openfhir.util.OpenEhrCachedUtils;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_NOT_OF;
import static com.syntaric.openfhir.fc.FhirConnectConst.CONDITION_OPERATOR_ONE_OF;

@Component
@Slf4j
public class ToAql {

    private final FhirConnectContextRepository fhirConnectContextRepository;
    private final OpenFhirMapperUtils openFhirMapperUtils;
    private final UserContextProducerInterface openFhirUser;
    private final OpenFhirMappingContext openFhirMappingContext;
    private final ToAqlMappingEngine toAqlMappingEngine;
    private final HelpersCreator helpersCreator;
    private final OpenEhrCachedUtils cachedUtils;

    @Autowired
    public ToAql(final FhirConnectContextRepository fhirConnectContextRepository,
                 final OpenFhirMapperUtils openFhirMapperUtils,
                 final UserContextProducerInterface openFhirUser,
                 final OpenFhirMappingContext openFhirMappingContext,
                 final ToAqlMappingEngine toAqlMappingEngine, HelpersCreator helpersCreator,
                 final OpenEhrCachedUtils cachedUtils) {
        this.fhirConnectContextRepository = fhirConnectContextRepository;
        this.openFhirMapperUtils = openFhirMapperUtils;
        this.openFhirUser = openFhirUser;
        this.openFhirMappingContext = openFhirMappingContext;
        this.toAqlMappingEngine = toAqlMappingEngine;
        this.helpersCreator = helpersCreator;
        this.cachedUtils = cachedUtils;
    }

    public ToAqlResponse toAql(final ToAqlRequest toAqlRequest) {
        final String template = toAqlRequest.getTemplate();
        final String fhirFullUrl = toAqlRequest.getFhirFullUrl();
        log.info("Invoking toAql with {}", toAqlRequest);
        final String resourceType = openFhirMapperUtils.parseFhirResourceType(fhirFullUrl);
        final List<FhirQueryParam> queryParams = openFhirMapperUtils.parseFhirQueryParams(fhirFullUrl);
        log.debug("Parsed FHIR resource type '{}' and query params {} from URL '{}'",
                resourceType, queryParams, fhirFullUrl);
        final String profileUrl = queryParams.stream().filter(p -> "_profile".equals(p.getName()))
                .map(param -> {
                    param.setHandled(true);
                    return param.getValue();
                }).findFirst().orElse(null);

        final List<ToAqlModels> allModels = getRelevantModelEntities(template, profileUrl);
        final List<ToAqlModels> narrowedByResourceType = new ArrayList<>();

        narrowByResourceTypeAndPrecondition(allModels, resourceType, narrowedByResourceType, queryParams);

        for (final ToAqlModels aModel : narrowedByResourceType) {
            final String templateId = OpenFhirMappingContext.normalizeTemplateId(aModel.getContext().getTemplateId());
            final OPERATIONALTEMPLATE operationalTemplate = cachedUtils.getOperationalTemplate(
                    openFhirUser.getAuthContext().getTenant(), templateId);
            final WebTemplate webTemplate = cachedUtils.parseWebTemplate(operationalTemplate);
            final List<MappingHelper> mappingHelpers = helpersCreator.constructHelpers(templateId,
                    aModel.getModelMappers(),
                    webTemplate);
            aModel.setMappingHelpers(mappingHelpers);
        }

        return toAqlMappingEngine.map(narrowedByResourceType, resourceType, queryParams, toAqlRequest.getTemplate() != null || profileUrl != null);
    }

    /**
     * Narrows down all models by resource type, also following the slotArchetypes
     */
    private void narrowByResourceTypeAndPrecondition(final List<ToAqlModels> allModels,
                                                     final String resourceType,
                                                     final List<ToAqlModels> relevantModels,
                                                     final List<FhirQueryParam> queryParams) {
        for (final ToAqlModels aModel : allModels) {
            if (aModel.getModelMappers() == null) {
                continue;
            }
            for (final OpenFhirFhirConnectModelMapper modelMapper : aModel.getModelMappers()) {
                narrowByResourceTypeAndPrecondition(modelMapper, null, resourceType, aModel, relevantModels, queryParams);
            }
        }
    }

    private void narrowByResourceTypeAndPrecondition(final OpenFhirFhirConnectModelMapper theSlot,
                                                     final String comingFromSlot,
                                                     final String resourceType,
                                                     final ToAqlModels lookingIntoModel,
                                                     final List<ToAqlModels> relevantModels,
                                                     final List<FhirQueryParam> queryParams) {
        if (theSlot == null) {
            // slot doesnt exist within this AqlModel, which means FhirConnect mapping is not ok
            return;
        }
        if (resourceType.equals(theSlot.getFhirConfig().getResource()) && preconditionPasses(resourceType, theSlot, queryParams)) {
            relevantModels.add(lookingIntoModel);
        }
        narrowByResourceTypeAndPrecondition(theSlot.getMappings(), comingFromSlot, resourceType,
                lookingIntoModel, relevantModels, queryParams);
    }

    private void narrowByResourceTypeAndPrecondition(final List<Mapping> mappings,
                                                     final String comingFromSlot,
                                                     final String resourceType,
                                                     final ToAqlModels lookingIntoModel,
                                                     final List<ToAqlModels> relevantModels,
                                                     final List<FhirQueryParam> queryParams) {
        if (mappings == null) {
            return;
        }
        for (final Mapping mapping : mappings) {
            if (mapping.getFollowedBy() != null) {
                List<Mapping> followedByMappings = mapping.getFollowedBy().getMappings();
                narrowByResourceTypeAndPrecondition(followedByMappings, comingFromSlot, resourceType, lookingIntoModel, relevantModels, queryParams);
            }
            if (mapping.getReference() != null) {
                List<Mapping> referenceMappings = mapping.getReference().getMappings();
                narrowByResourceTypeAndPrecondition(referenceMappings, comingFromSlot, resourceType, lookingIntoModel, relevantModels, queryParams);
            }
            if (StringUtils.isEmpty(mapping.getSlotArchetype())) {
                continue;
            }
            final String slotArchetype = mapping.getSlotArchetype();
            if (slotArchetype.equals(comingFromSlot)) {
                continue; // recursion infinite loop
            }

            String templateId = OpenFhirMappingContext.normalizeTemplateId(lookingIntoModel.getContext().getTemplateId());
            final OpenFhirContextRepository relevantRepositoryForThisTemplate = openFhirMappingContext.getRepository().get(templateId);
            final List<OpenFhirFhirConnectModelMapper> nextSlots = relevantRepositoryForThisTemplate.getMappers().get(slotArchetype); // should only be one
            nextSlots.forEach(nextSlot -> narrowByResourceTypeAndPrecondition(nextSlot, slotArchetype, resourceType, new ToAqlModels(null, nextSlots,
                    lookingIntoModel.getContext()), relevantModels, queryParams));
        }
    }

    boolean preconditionPasses(final String resourceType,
                               final OpenFhirFhirConnectModelMapper theSlot,
                               final List<FhirQueryParam> queryParams) {
        final Preprocessor preprocessor = theSlot.getOriginalModel().getPreprocessor();
        if (preprocessor == null) {
            return true;
        }
        final List<Condition> fhirConditions = preprocessor.getFhirConditions();
        if (fhirConditions == null || fhirConditions.isEmpty()) {
            return true;
        }

        for (final Condition fhirCondition : fhirConditions) {
            final String conditionPath = String.format("%s.%s", resourceType, fhirCondition.getTargetAttribute());

            boolean relevantQueryParamPresent = false;
            for (final FhirQueryParam param : queryParams) {
                final String paramName = param.getName();
                final String paramValue = param.getValue();

                final String fhirPathForQueryName = toAqlMappingEngine.getFhirPathForQueryName(resourceType, paramName);
                final Set<String> fhirPathsForQueryName = Set.of(fhirPathForQueryName.split("\\|"));// because it can be many

                if (fhirPathsForQueryName.stream().noneMatch(conditionPath::startsWith)) { // not sure if starsWith is ok here, but fhirPath on i.e. Observation.code.coding.code is actually Observation.code
                    // if ONE OF says this mapping is only relevant for a specific condition
                    // yet that queryParam is not present, it means it's not relevant for the AQL translation
                    if (CONDITION_OPERATOR_ONE_OF.equals(fhirCondition.getOperator())) {
                        relevantQueryParamPresent = false;
                    }
                    continue;
                }
                relevantQueryParamPresent = true;

                // evaluate values
                final String operator = fhirCondition.getOperator();
                final List<String> criterias = fhirCondition.getCriterias();
                switch (operator) {
                    case CONDITION_OPERATOR_ONE_OF:
                        param.setHandled(true);
                        return criterias.contains(paramValue);
                    case CONDITION_OPERATOR_NOT_OF:
                        if (criterias.contains(paramValue)) {
                            return false;
                        }
                }
            }

            if (!relevantQueryParamPresent) {
                return false;
            }
        }

        return true;
    }

    private List<ToAqlModels> getRelevantModelEntities(final String templateId,
                                                       final String profileUrl) {
        final FhirConnectContextEntity relevantContext = getContext(templateId, profileUrl);
        if (relevantContext == null && (StringUtils.isNotEmpty(templateId) || StringUtils.isNotEmpty(profileUrl))) {
            final String formattedError = String.format(
                    "Couldn't find context relevant to template %s or profile %s",
                    templateId, profileUrl);
            log.error(formattedError);
            throw new RequestValidationException(formattedError, null);
        }
        if (relevantContext != null) {
            log.info("Found relevant context for toAql translation {}", relevantContext.getId());
            return List.of(contextToToAqlModels(relevantContext));
        }
        // no specific context found — search across all tenant contexts
        return fhirConnectContextRepository.findByTenant(openFhirUser.getAuthContext().getTenant()).stream()
                .map(this::contextToToAqlModels)
                .toList();
    }

    private ToAqlModels contextToToAqlModels(final FhirConnectContextEntity context) {
        List<OpenFhirFhirConnectModelMapper> mapperForArchetype = openFhirMappingContext
                .getMapperForArchetype(context.getFhirConnectContext().getContext().getTemplate().getId(),
                        context.getFhirConnectContext().getContext().getStart());
        return ToAqlModels.builder()
                .modelMappers(mapperForArchetype)
                .context(context)
                .build();
    }

    private FhirConnectContextEntity getContext(final String templateId,
                                                final String profileUrl) {
        if (StringUtils.isNotEmpty(templateId)) {
            log.debug("Finding context by templateId {}", templateId);
            return fhirConnectContextRepository.findByTemplateIdAndTenant(templateId, openFhirUser.getAuthContext().getTenant());
        }
        if (StringUtils.isNotEmpty(profileUrl)) {
            log.debug("Finding context by profile url {}", profileUrl);
            List<FhirConnectContextEntity> byTenant = fhirConnectContextRepository.findByTenant(openFhirUser.getAuthContext().getTenant());
            return byTenant.stream()
                    .filter(c -> profileUrl.equals(c.getFhirConnectContext().getContext().getProfile().getUrl()))
                    .findAny()
                    .orElse(null);
        }
        return null;
    }

    @Data
    @Builder
    public static class ToAqlModels {
        private List<MappingHelper> mappingHelpers;
        private List<OpenFhirFhirConnectModelMapper> modelMappers;
        private FhirConnectContextEntity context;
    }
}
