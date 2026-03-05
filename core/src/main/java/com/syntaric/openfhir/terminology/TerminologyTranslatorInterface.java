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
}
