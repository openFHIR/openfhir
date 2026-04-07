package com.syntaric.openfhir.util;

import com.syntaric.openfhir.db.entity.OptEntity;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrTemplateUtils {

    public WebTemplate parseWebTemplate(final OPERATIONALTEMPLATE operationaltemplate) {
        return createParser(operationaltemplate);
    }

    private WebTemplate createParser(final OPERATIONALTEMPLATE operationaltemplate) {
        return new OPTParser(operationaltemplate).parse();
    }

    public OPERATIONALTEMPLATE getOperationalTemplate(final OptEntity optEntity) {
        if(optEntity == null) {
            return null;
        }
        return getOperationalTemplate(optEntity.getContent());
    }

    private OPERATIONALTEMPLATE getOperationalTemplate(final String templateContent) {
        try {
            return TemplateDocument.Factory.parse(templateContent).getTemplate();
        } catch (final Exception e) {
            log.error("Couldn't parse OPT even though it came from the db?", e);
            return null;
        }
    }
}
