package com.syntaric.openfhir.util;

import com.syntaric.openfhir.db.entity.OptEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrTemplateUtils {

    /** Stand-in name for when the looked-up template id wasn't passed in and no entity was found to carry it. */
    private static final String UNKNOWN_TEMPLATE_ID = "<unknown>";

    /**
     * Parses the given operational template entity into a WebTemplate.
     * <p>
     * A missing template and an unparseable one are reported separately: the former is caller-correctable (the
     * templateId doesn't resolve to anything uploaded) while the latter is a server-side fault. Neither is ever
     * returned as {@code null} — {@link OPTParser} dereferences its argument in its constructor, so handing it a
     * null template would surface as an opaque NullPointerException instead of a diagnosable message.
     *
     * @param optEntity  operational template as found in the database, may be null when nothing matched
     * @param templateId template id that was looked up, used to name the template in error messages
     * @return parsed WebTemplate, never null
     * @throws TemplateNotFoundException if no operational template exists for this templateId
     * @throws InvalidTemplateException  if the stored operational template cannot be parsed
     */
    public WebTemplate parseWebTemplate(final OptEntity optEntity, final String templateId) {
        if (optEntity == null) {
            log.warn("No operational template found for template id {}.", templateId);
            throw new TemplateNotFoundException(templateId);
        }
        return createParser(getOperationalTemplate(optEntity, templateId), templateId);
    }

    /**
     * Same as {@link #parseWebTemplate(OptEntity, String)}, naming the template from the entity itself. Callers
     * that know the template id they looked up should prefer the two-argument form, so a missing template can
     * still be named in the error.
     */
    public WebTemplate parseWebTemplate(final OptEntity optEntity) {
        return parseWebTemplate(optEntity, optEntity == null ? UNKNOWN_TEMPLATE_ID : optEntity.getTemplateId());
    }

    private WebTemplate createParser(final OPERATIONALTEMPLATE operationaltemplate, final String templateId) {
        try {
            return new OPTParser(operationaltemplate).parse();
        } catch (final Exception e) {
            log.error("Couldn't build a WebTemplate from operational template {}.", templateId, e);
            throw new InvalidTemplateException(templateId, e);
        }
    }

    private OPERATIONALTEMPLATE getOperationalTemplate(final OptEntity optEntity, final String templateId) {
        return getOperationalTemplate(optEntity.getContent(), templateId);
    }

    private OPERATIONALTEMPLATE getOperationalTemplate(final String templateContent, final String templateId) {
        if (StringUtils.isBlank(templateContent)) {
            log.error("Operational template {} is stored without any content.", templateId);
            throw new InvalidTemplateException(templateId, null);
        }
        try {
            return TemplateDocument.Factory.parse(templateContent).getTemplate();
        } catch (final Exception e) {
            log.error("Couldn't parse OPT even though it came from the db?", e);
            throw new InvalidTemplateException(templateId, e);
        }
    }
}
