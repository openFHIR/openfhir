package com.syntaric.openfhir.util;

import lombok.Getter;

/**
 * Raised when an operational template is present in the openFHIR state but cannot be turned into a WebTemplate,
 * because the stored XML no longer parses. Unlike {@link TemplateNotFoundException} the caller can't correct this
 * by fixing the request, so it stays a server-side fault.
 *
 * @see OpenEhrTemplateUtils#parseWebTemplate
 */
@Getter
public class InvalidTemplateException extends RuntimeException {

    private final String templateId;

    public InvalidTemplateException(final String templateId, final Throwable cause) {
        super(String.format(
                "Could not create a WebTemplate from the stored operational template '%s'. Please validate the template or contact the openFHIR support team.",
                templateId), cause);
        this.templateId = templateId;
    }
}
