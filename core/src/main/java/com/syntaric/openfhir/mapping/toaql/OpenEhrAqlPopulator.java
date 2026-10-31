package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.mapping.helpers.RmAttributePins;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrAqlPopulator {

    /**
     * The WHERE comparison of {@code attributePath}, the strict AQL path of {@code attribute}, with a search value:
     * a string literal for text, escaped so that the value can't leave it, as is for a number or a boolean, which
     * the value then has to be (see {@link AqlLiterals}).
     *
     * @throws IllegalArgumentException when the attribute is a number or a boolean and the value is not one
     */
    public String comparison(final String attributePath, final RmAttributePins.Attribute attribute,
                             final String expectedValue) {
        final String literal = switch (attribute.kind()) {
            case TEXT -> AqlLiterals.string(expectedValue);
            case NUMBER -> AqlLiterals.number(expectedValue);
            case BOOLEAN -> AqlLiterals.bool(expectedValue);
        };
        return attributePath + " = " + literal;
    }
}
