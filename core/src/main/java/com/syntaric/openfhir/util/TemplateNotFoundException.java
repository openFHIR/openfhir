package com.syntaric.openfhir.util;

import lombok.Getter;

/**
 * Raised when the operational template a mapping requires is not present in the openFHIR state for the current
 * tenant. This is caller-correctable input (a templateId that doesn't resolve), not a server fault, so callers
 * translate it into a 400 naming the template rather than letting it surface as an opaque runtime error.
 *
 * @see OpenEhrTemplateUtils#parseWebTemplate
 */
@Getter
public class TemplateNotFoundException extends RuntimeException {

    private final String templateId;

    public TemplateNotFoundException(final String templateId) {
        super(String.format(
                "Operational template '%s' not found. Upload the operational template with this templateId, or correct the template id referenced by the Context mapper.",
                templateId));
        this.templateId = templateId;
    }
}
