package com.syntaric.openfhir.mapping.tofhir;

import ca.uhn.fhir.util.FhirTerser;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;

@Slf4j
public class ToFhirPrePostProcessor implements ToFhirPrePostProcessorInterface {

    final protected FhirContextRegistry fhirContextRegistry;

    public ToFhirPrePostProcessor(final FhirContextRegistry fhirContextRegistry) {
        this.fhirContextRegistry = fhirContextRegistry;
    }

    @Override
    public IBaseBundle postProcess(final IBaseBundle mappedResource,
                              final FhirConnectContext context,
                              final List<Composition> compositions,
                              final WebTemplate webTemplate) {
        stripEmptyContained(mappedResource, getVersion(context));
        return mappedResource;
    }

    @Override
    public void preProcess(final FhirConnectContext context, final List<Composition> compositions,
                           final WebTemplate webTemplate) {

    }

    @Override
    public void preProcessContentItems(FhirConnectContext context, List<ContentItem> contentItems, WebTemplate webTemplate) {

    }

    protected Spec.Version getVersion(final FhirConnectContext context) {
        if (context != null && context.getSpec() != null && context.getSpec().getVersion() != null) {
            return context.getSpec().getVersion();
        }
        return Spec.Version.R4;
    }

    public void stripEmptyContained(final IBaseBundle bundle, final Spec.Version fhirVersion) {
        final FhirTerser terser = fhirContextRegistry.getContext(fhirVersion).newTerser();
        final List<IBaseResource> resources = getResourcesFromBundle(bundle, fhirVersion);
        int resourceIndex = 1;
        for (final IBaseResource resource : resources) {
            final List<IBaseReference> allReferences =
                    terser.getAllPopulatedChildElementsOfType(resource, IBaseReference.class);
            for (final IBaseReference reference : allReferences) {
                final IBaseResource contained = (IBaseResource) reference.getResource();
                if (resourceIsEmpty(contained)) {
                    reference.setResource(null);
                    reference.setReference(null);
                } else {
                    reference.setReference(String.format("#%s", resourceIndex));
                    contained.setId(String.format("#%s", resourceIndex));
                    resourceIndex++;
                }
            }
        }
    }

    private List<IBaseResource> getResourcesFromBundle(final IBaseBundle bundle, final Spec.Version fhirVersion) {
        return switch (fhirVersion) {
            case STU3 -> ((org.hl7.fhir.dstu3.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent::getResource)
                    .filter(r -> r != null)
                    .map(r -> (IBaseResource) r)
                    .toList();
            case R4B -> ((org.hl7.fhir.r4b.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent::getResource)
                    .filter(r -> r != null)
                    .map(r -> (IBaseResource) r)
                    .toList();
            case R5 -> ((org.hl7.fhir.r5.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.r5.model.Bundle.BundleEntryComponent::getResource)
                    .filter(r -> r != null)
                    .map(r -> (IBaseResource) r)
                    .toList();
            default -> ((org.hl7.fhir.r4.model.Bundle) bundle).getEntry().stream()
                    .map(org.hl7.fhir.r4.model.Bundle.BundleEntryComponent::getResource)
                    .filter(r -> r != null)
                    .map(r -> (IBaseResource) r)
                    .toList();
        };
    }

    private boolean resourceIsEmpty(final IBaseResource resource) {
        if (resource == null) return true;
        try {
            if (resource instanceof org.hl7.fhir.dstu3.model.Base stu3Base) {
                return stu3Base.isEmpty();
            }
            if (resource instanceof org.hl7.fhir.r4.model.Base r4Base) {
                final org.hl7.fhir.r4.model.Base copy = r4Base.copy();
                copy.setIdBase(null);
                return copy.isEmpty();
            }
            if (resource instanceof org.hl7.fhir.r4b.model.Base r4bBase) {
                final org.hl7.fhir.r4b.model.Base copy = r4bBase.copy();
                copy.setIdBase(null);
                return copy.isEmpty();
            }
            if (resource instanceof org.hl7.fhir.r5.model.Base r5Base) {
                final org.hl7.fhir.r5.model.Base copy = r5Base.copy();
                copy.setIdBase(null);
                return copy.isEmpty();
            }
            return resource.isEmpty();
        } catch (Exception e) {
            // because copy can be tricky but if it turns out to be tricky, it means element isn't empty..
            return false;
        }
    }
}
