package com.syntaric.openfhir.db;

import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.db.entity.OptEntity;
import com.syntaric.openfhir.db.repository.OptRepository;
import com.syntaric.openfhir.producers.UserContextProducerInterface;
import com.syntaric.openfhir.rest.RequestValidationException;
import com.syntaric.openfhir.util.OpenEhrTemplateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Component
@Slf4j
@Transactional
public class OptService {

    private final OptRepository optRepository;
    private final OpenEhrTemplateUtils openEhrApplicationScopedUtils;
    private final UserContextProducerInterface openFhirUser;

    @Autowired
    public OptService(final OptRepository optRepository,
                      final OpenEhrTemplateUtils openEhrApplicationScopedUtils,
                      final UserContextProducerInterface openFhirUser) {
        this.optRepository = optRepository;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.openFhirUser = openFhirUser;
    }

    /**
     * Creates an operational template in the database.
     *
     * @param opt string payload of the operational template
     * @return created OptEntity without the content (just with the ID assigned by the database)
     * @throws IllegalArgumentException if validation of a template fails (if it can not be parsed)
     */
    public OptEntity upsert(final String opt, String id, final String reqId) {
        log.debug("Receive CREATE/UPDATE OPT, id {}, reqId: {}", id, reqId);
        try {
            id = resolveOptId(id);

            final OPERATIONALTEMPLATE operationaltemplate = parseOptFromString(opt);
            final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(
                    operationaltemplate.getTemplateId().getValue());
            openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);

            checkTemplateIdNotTaken(operationaltemplate, normalizedTemplateId, id);

            final OptEntity entity = buildOptEntity(id, opt, operationaltemplate, normalizedTemplateId);
            final OptEntity saved = optRepository.save(entity);
            final OptEntity copied = saved.copy();
            copied.setContent("redacted");
            return copied;
        } catch (final RequestValidationException e) {
            throw e;
        } catch (final Exception e) {
            log.error("Couldn't create a template, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a template. " + e.getMessage());
        }
    }

    private String resolveOptId(final String id) {
        if (StringUtils.isEmpty(id)) {
            return null;
        }
        final String existing = optRepository.readByOrganisation(openFhirUser.getAuthContext().getTenant(), id);
        return existing == null
                ? null // id not owned by this tenant — treat as create
                : id;
    }

    private void checkTemplateIdNotTaken(final OPERATIONALTEMPLATE operationaltemplate,
                                         final String normalizedTemplateId, final String id) {
        if (StringUtils.isNotBlank(id)) {
            return; // updating an existing record — template collision check not needed
        }
        final OptEntity existing = optRepository.findByTemplateIdAndOrganisation(
                normalizedTemplateId, openFhirUser.getAuthContext().getTenant());
        if (existing != null) {
            throw new RequestValidationException(
                    "Template with templateId " + operationaltemplate.getTemplateId() + " (normalized to: "
                            + normalizedTemplateId + ") already exists.", null);
        }
    }

    private OptEntity buildOptEntity(final String id, final String opt,
                                     final OPERATIONALTEMPLATE operationaltemplate,
                                     final String normalizedTemplateId) {
        final OptEntity entity = new OptEntity(StringUtils.isEmpty(id) ? null : id, opt, normalizedTemplateId,
                operationaltemplate.getTemplateId().getValue(),
                operationaltemplate.getTemplateId().getValue());
        entity.setUser(openFhirUser.getAuthContext().getUserId());
        entity.setOrganisation(openFhirUser.getAuthContext().getTenant());
        entity.setCreated(new Date());
        return entity;
    }

    public String getContent(final String id) {
        final OptEntity byIdAndOrganisation = optRepository.findByIdAndOrganisation(id,
                openFhirUser.getAuthContext()
                        .getTenant());
        return byIdAndOrganisation == null ? null : byIdAndOrganisation.getContent();
    }

    public String getContentByTemplateId(final String templateId) {
        final OptEntity foundOpt = optRepository.findByTemplateIdAndOrganisation(templateId,
                openFhirUser.getAuthContext()
                        .getTenant());
        if (foundOpt == null) {
            return null;
        }
        return foundOpt.getContent();
    }

    public OptEntity byTemplateIdAndOrganization(final String templateId) {
        return optRepository.findByTemplateIdAndOrganisation(templateId,
                openFhirUser.getAuthContext().getTenant());
    }

    public List<OptEntity> allOfUser(final String reqId) {
        return optRepository.findByOrganisation(openFhirUser.getAuthContext().getTenant());
    }

    public void deleteAllTenant() {
        optRepository.deleteAllTenant(openFhirUser.getAuthContext().getTenant());
    }

    /**
     * Ignore any white character at the beginning of the payload and parse content to OPERATIONALTEMPLATE
     *
     * @param content XML that represents a serialized operational template
     * @return parsed OPERATIONALTEMPLATE from the given payload
     * @throws XmlException if content is invalid XML after removing the white characters
     */
    private OPERATIONALTEMPLATE parseOptFromString(final String content) throws XmlException {
        return TemplateDocument.Factory.parse(content.trim().replaceFirst("^(\\W+)<", "<")).getTemplate();
    }
}
