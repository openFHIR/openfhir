package com.syntaric.openfhir.terminology;

import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;


public interface TerminologyTranslatorInterface {

    Coding translateToFhir(final String code, final String system, final String desiredSystem,
                           final Terminology terminology);

    Coding translateToOpenEhr(final String code,
                              final String system,
                              final String desiredSystem,
                              final Terminology terminology,
                              final List<OfCoding> availableCodings);

    /**
     * As {@link #translateToFhir(String, String, String, Terminology)}, additionally handing the translator the
     * source coding's {@code display}. A translation that keeps the code and rewrites only the system (a
     * ConceptMap {@code "*"} element) has no display of its own and should return this one, so the term
     * survives the trip. Implementations that do not use the display inherit this default, which drops it.
     */
    default Coding translateToFhir(final String code, final String system, final String display,
                                   final String desiredSystem, final Terminology terminology) {
        return translateToFhir(code, system, desiredSystem, terminology);
    }

    /**
     * As {@link #translateToOpenEhr(String, String, String, Terminology, List)}, additionally handing the
     * translator the source coding's {@code display}; see
     * {@link #translateToFhir(String, String, String, String, Terminology)}.
     */
    default Coding translateToOpenEhr(final String code,
                                      final String system,
                                      final String display,
                                      final String desiredSystem,
                                      final Terminology terminology,
                                      final List<OfCoding> availableCodings) {
        return translateToOpenEhr(code, system, desiredSystem, terminology, availableCodings);
    }
}
