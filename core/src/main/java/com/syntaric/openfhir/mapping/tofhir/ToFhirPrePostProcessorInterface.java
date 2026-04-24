package com.syntaric.openfhir.mapping.tofhir;

import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import java.util.List;

import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;

public interface ToFhirPrePostProcessorInterface {

    IBaseBundle postProcess(final IBaseBundle mappedResource,
                       final FhirConnectContext context,
                       final List<Composition> compositions,
                       final WebTemplate webTemplate);

    void preProcess(final FhirConnectContext context,
                    final List<Composition> compositions,
                    final WebTemplate webTemplate);

    void preProcessContentItems(final FhirConnectContext context,
                    final List<ContentItem> contentItems,
                    final WebTemplate webTemplate);
}
