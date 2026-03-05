package com.syntaric.openfhir.util;

import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import org.hl7.fhir.r4.model.Base;

public interface PrePostFhirInstancePopulatorInterface {

    void prePopulateElement(final Object toPopulate,
                            final Base data,
                            final String modelName,
                            final String mappingName,
                            final String fromPath,
                            final String toPath,
                            final int index,
                            final Terminology terminology);

    void postPopulateElement(final Object toPopulate,
                            final Base data,
                            final String modelName,
                            final String mappingName,
                            final String fromPath,
                            final String toPath,
                            final int index,
                             final Terminology terminology);
}
