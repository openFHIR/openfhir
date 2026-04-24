package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Resource;

public interface ToOpenEhrPrePostProcessorInterface {

    void postProcess(final Composition composition);

    void postProcess(final JsonObject compositionFlatFormat);

    void preProcess(final FhirConnectContext context,
                    final IAnyResource startingResource);
}
