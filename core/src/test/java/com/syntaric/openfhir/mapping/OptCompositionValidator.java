package com.syntaric.openfhir.mapping;

import com.nedap.archie.rm.composition.Composition;
import java.util.List;
import java.util.stream.Collectors;
import org.ehrbase.openehr.sdk.validation.CompositionValidator;
import org.ehrbase.openehr.sdk.validation.ConstraintViolation;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

public final class OptCompositionValidator {

    private static final CompositionValidator VALIDATOR = new CompositionValidator();

    private OptCompositionValidator() {
        // utility class
    }

    public static void assertValid(OPERATIONALTEMPLATE opt, Composition composition) {
        List<ConstraintViolation> violations = VALIDATOR.validate(composition, opt);

        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));

            throw new AssertionError(
                    "Composition is NOT valid against OPT:\n" +  message
            );
        }
    }

    public static List<ConstraintViolation> validate(OPERATIONALTEMPLATE opt, Composition composition) {
        return VALIDATOR.validate(composition, opt);
    }
}
