package com.syntaric.openfhir.db;

import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.db.entity.FhirConnectModelEntity;
import com.syntaric.openfhir.db.entity.UserBasedEntity;
import com.syntaric.openfhir.db.repository.FhirConnectContextRepository;
import com.syntaric.openfhir.db.repository.FhirConnectModelRepository;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import com.syntaric.openfhir.rest.RequestValidationException;
import com.syntaric.openfhir.util.FhirConnectValidator;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@Transactional
public class FhirConnectService {

    private final FhirConnectModelRepository modelRepository;
    private final FhirConnectContextRepository contextRepository;
    private final FhirConnectValidator validator;
    private final UserContextProducerInterface openFhirUser;

    @Autowired
    public FhirConnectService(final FhirConnectModelRepository modelRepository,
                              final FhirConnectContextRepository contextRepository,
                              final FhirConnectValidator validator,
                              final UserContextProducerInterface openFhirUser) {
        this.modelRepository = modelRepository;
        this.contextRepository = contextRepository;
        this.validator = validator;
        this.openFhirUser = openFhirUser;
    }

    /**
     * Creates a model mapper based on FHIR Connect specification.
     *
     * @return created Model Mapper populated with database assigned attributes (namely id)
     * @throws RequestValidationException if incoming BODY is not according to model-mapping json schema
     * @throws RequestValidationException if FHIR Paths within the mappers are not valid FHIR Paths
     */
    public FhirConnectModelEntity upsertModelMapper(final FhirConnectModel fhirConnectModel, String id,
                                                    final String reqId) {
        log.debug("Receive CREATE/UPDATE FhirConnectModel, id {}, reqId: {}", id, reqId);
        try {
            final IdAndCreated resolved = resolveModelIdAndCreated(id);
            id = resolved.id;

            validateOrThrow(validator.validateAgainstModelSchema(fhirConnectModel),
                            "[{}] Error occurred trying to validate FC model mapper against the schema. Nothing has been created. Errors: {}",
                            reqId, "Couldn't validate against the yaml schema");

            validateOrThrow(validator.validateFhirConnectModel(fhirConnectModel),
                            "[{}] Error occurred trying to validate semantic correctness of the mapper.",
                            reqId, "Error occurred trying to validate semantic correctness of the mapper,");

            checkModelNameNotTaken(fhirConnectModel, id);

            final FhirConnectModelEntity build = FhirConnectModelEntity.builder()
                    .fhirConnectModel(fhirConnectModel)
                    .id(StringUtils.isBlank(id) ? null : id)
                    .build();
            populateUserFields(build, resolved.created);
            final FhirConnectModelEntity saved = modelRepository.save(build);
            saved.setFhirConnectModel(
                    fhirConnectModel); // unless we do this, when postgres is used, this will be empty in response
            saved.getFhirConnectModel().setId(saved.getId());
            return saved;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create a FhirConnectModel, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectModel. Invalid one.", e);
        }
    }

    private IdAndCreated resolveModelIdAndCreated(final String id) {
        if (StringUtils.isEmpty(id)) {
            return new IdAndCreated(null, null);
        }
        final FhirConnectModelEntity existing = modelRepository.readByTenant(
                openFhirUser.getAuthContext().getTenant(), id);
        return existing == null
                ? new IdAndCreated(null, null) // id not owned by this tenant — treat as create
                : new IdAndCreated(id, existing.getCreated());
    }

    private void checkModelNameNotTaken(final FhirConnectModel fhirConnectModel, final String id) {
        if (id != null) {
            return; // updating an existing record — name collision check not needed
        }
        final List<FhirConnectModelEntity> existing = modelRepository.findByTenantAndName(
                Collections.singletonList(fhirConnectModel.getMetadata().getName()),
                openFhirUser.getAuthContext().getTenant());
        if (existing != null && !existing.isEmpty()) {
            throw new RequestValidationException(
                    "Model mapper with this name " + fhirConnectModel.getMetadata().getName()
                            + " already exists. Modify the name of the model mapper or update it (PUT with id) if you want to update an existing one.",
                    null);
        }
    }

    /**
     * Creates a context mapper based on FHIR Connect specification.
     *
     * @return created Context Mapper populated with database assigned attributes (namely id)
     * @throws RequestValidationException if incoming BODY is not according to contextual-mapping json schema
     * @throws IllegalArgumentException if a context mapper fot the given template already exists (there can
     *         only be
     *         one context mapper for a specific template id)
     */
    public FhirConnectContextEntity upsertContextMapper(final FhirConnectContext fhirContext, String id,
                                                        final String reqId) {
        log.debug("Receive CREATE/UPDATE FhirConnectContext, id {}, reqId: {}", id, reqId);
        try {
            final IdAndCreated resolved = resolveContextIdAndCreated(id);
            id = resolved.id;

            validateOrThrow(validator.validateAgainstContextSchema(fhirContext),
                            "[{}] Error occurred trying to validate connect context mapper against the schema. Nothing has been created. Errors: {}",
                            reqId, "Couldn't validate against the yaml schema");

            checkContextTemplateNotTaken(fhirContext, id, reqId);

            final FhirConnectContextEntity build = FhirConnectContextEntity.builder()
                    .fhirConnectContext(fhirContext)
                    .id(StringUtils.isBlank(id) ? null : id)
                    .build();
            populateUserFields(build, resolved.created);

            final FhirConnectContextEntity saved = contextRepository.save(build);
            saved.setFhirConnectContext(fhirContext);
            saved.getFhirConnectContext().setId(saved.getId());
            return saved;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create/update a FhirConnectContext, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a FhirConnectContext. Invalid one.", e);
        }
    }

    private IdAndCreated resolveContextIdAndCreated(final String id) {
        if (StringUtils.isEmpty(id)) {
            return new IdAndCreated(null, null);
        }
        final FhirConnectContextEntity existing = contextRepository.readByTenant(
                openFhirUser.getAuthContext().getTenant(), id);
        return existing == null
                ? new IdAndCreated(null, null) // id not owned by this tenant — treat as create
                : new IdAndCreated(id, existing.getCreated());
    }

    private void checkContextTemplateNotTaken(final FhirConnectContext fhirContext, final String id,
                                              final String reqId) {
        if (StringUtils.isNotBlank(id)) {
            return; // updating an existing record — template collision check not needed
        }
        final String templateId = fhirContext.getContext().getTemplate().getId();
        if (contextRepository.findByTemplateIdAndTenant(templateId, openFhirUser.getAuthContext().getTenant()) != null) {
            log.error("[{}] A context mapper for this templateId {} already exists.", reqId, templateId);
            throw new RequestValidationException("Couldn't create a FhirConnectContext. Invalid one.",
                                                 List.of("A context mapper for this template already exists."));
        }
    }

    private void validateOrThrow(final List<String> errors, final String logMessage, final String reqId,
                                 final String exceptionMessage) {
        if (errors == null || errors.isEmpty()) {
            return;
        }
        log.error(logMessage, reqId, errors);
        throw new RequestValidationException(exceptionMessage, errors);
    }

    private void populateUserFields(final UserBasedEntity entity,
                                    final Date existingCreated) {
        entity.setUser(openFhirUser.getAuthContext().getUserId());
        entity.setOrganisation(openFhirUser.getAuthContext().getTenant());
        entity.setCreated(existingCreated == null ? new Date() : existingCreated);
        entity.setUpdated(new Date());
    }

    private static final class IdAndCreated {
        final String id;
        final Date created;

        IdAndCreated(final String id, final Date created) {
            this.id = id;
            this.created = created;
        }
    }

    public List<FhirConnectModel> allUserModelMappers(final String reqId) {
        final List<FhirConnectModelEntity> byTenant = modelRepository.findByTenant(
                openFhirUser.getAuthContext().getTenant());
        return byTenant == null ? null : byTenant.stream().map(
                FhirConnectModelEntity::getFhirConnectModel).collect(Collectors.toList());
    }

    public FhirConnectModel readModelMappers(final String id) {
        final FhirConnectModelEntity modelEntity = modelRepository.readByTenant(
                openFhirUser.getAuthContext().getTenant(), id);
        return modelEntity == null ? null : modelEntity.getFhirConnectModel();
    }

    public FhirConnectContext readContextMappers(final String id) {
        final FhirConnectContextEntity fhirConnectContextEntity = contextRepository.readByTenant(
                openFhirUser.getAuthContext().getTenant(), id);
        return fhirConnectContextEntity == null ? null : fhirConnectContextEntity.getFhirConnectContext();
    }

    public List<FhirConnectContext> allUserContextMappers(final String reqId) {
        final List<FhirConnectContextEntity> byTenant = contextRepository.findByTenant(
                openFhirUser.getAuthContext().getTenant());
        return byTenant == null ? null : byTenant.stream().map(
                FhirConnectContextEntity::getFhirConnectContext).collect(Collectors.toList());
    }
}
