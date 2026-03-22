package com.syntaric.openfhir.mapping.tofhir;

import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

public interface ToFhirPrePostProcessorInterface {

    Bundle postProcess(final Bundle mappedResource,
                       final FhirConnectContext context,
                       final List<Composition> compositions,
                       final OPERATIONALTEMPLATE operationaltemplate);

    void preProcess(final FhirConnectContext context,
                    final List<Composition> compositions,
                    final OPERATIONALTEMPLATE operationaltemplate);

    void preProcessContentItems(final FhirConnectContext context,
                    final List<ContentItem> contentItems,
                    final OPERATIONALTEMPLATE operationaltemplate);
}
