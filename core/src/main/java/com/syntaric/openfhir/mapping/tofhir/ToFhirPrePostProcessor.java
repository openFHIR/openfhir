package com.syntaric.openfhir.mapping.tofhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.FhirTerser;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
@Slf4j
public class ToFhirPrePostProcessor implements ToFhirPrePostProcessorInterface {

    final protected FhirContext fhirContext;

    public ToFhirPrePostProcessor(final FhirContext fhirContext) {
        this.fhirContext = fhirContext;
    }

    @Override
    public Bundle postProcess(final Bundle mappedResource,
                              final FhirConnectContext context,
                              final List<Composition> compositions,
                              final WebTemplate webTemplate) {
        stripEmptyContained(mappedResource);
        return mappedResource;
    }

    @Override
    public void preProcess(final FhirConnectContext context, final List<Composition> compositions,
                           final WebTemplate webTemplate) {

    }

    @Override
    public void preProcessContentItems(FhirConnectContext context, List<ContentItem> contentItems, WebTemplate webTemplate) {

    }

    public void stripEmptyContained(final Bundle bundle) {
        int resourceIndex = 1;
        for (BundleEntryComponent bundleEntryComponent : bundle.getEntry()) {
            final Resource resource = bundleEntryComponent.getResource();
            final FhirTerser fhirTerser = fhirContext.newTerser();
            final List<Reference> allReferences = fhirTerser.getAllPopulatedChildElementsOfType(
                    resource, Reference.class);
            for (final Reference reference : allReferences) {
                final IBaseResource containedResource = reference.getResource();
                if (resourceIsEmpty((Base) containedResource)) {
                    reference.setResource(null);
                    reference.setReference(null);
                } else {
                    reference.setReference(String.format("#%s", resourceIndex));
                    containedResource.setId(String.format("#%s", resourceIndex));
                    resourceIndex += 1;
                }
            }
        }
    }

    private boolean resourceIsEmpty(final Base containedResource) {
        if (containedResource == null) {
            return true;
        }
        try {
            final Base copy = containedResource.copy();
            copy.setIdBase(null);
            return copy.isEmpty();
        } catch (Exception e) {
            // because copy can be tricky but if it turns out to be tricky, it means element isn't empty..
            return false;
        }
    }
}
