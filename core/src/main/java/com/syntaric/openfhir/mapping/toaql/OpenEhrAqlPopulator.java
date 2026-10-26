package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.mapping.helpers.RmAttributePins;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrAqlPopulator {

    /**
     * The WHERE comparison of {@code attributePath}, the strict AQL path of {@code attribute}, with a search value:
     * quoted for text, as is for a number or a boolean.
     */
    public String comparison(final String attributePath, final RmAttributePins.Attribute attribute,
                             final String expectedValue) {
        final String literal = attribute.kind() == RmAttributePins.Kind.TEXT
                ? String.format("'%s'", expectedValue)
                : expectedValue;
        return attributePath + " = " + literal;
    }
}
