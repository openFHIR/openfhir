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
        switch (fhirVersion) {
            case STU3  -> stripEmptyContainedStu3((org.hl7.fhir.dstu3.model.Bundle) bundle);
            case R4B   -> stripEmptyContainedR4b((org.hl7.fhir.r4b.model.Bundle) bundle);
            case R5    -> stripEmptyContainedR5((org.hl7.fhir.r5.model.Bundle) bundle);
            default    -> stripEmptyContainedR4((org.hl7.fhir.r4.model.Bundle) bundle);
        }
    }

    private void stripEmptyContainedR4(final org.hl7.fhir.r4.model.Bundle bundle) {
        final FhirTerser terser = fhirContextRegistry.getContext(Spec.Version.R4).newTerser();
        int resourceIndex = 1;
        for (final org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            final org.hl7.fhir.r4.model.Resource resource = entry.getResource();
            if (resource == null) continue;
            final List<org.hl7.fhir.r4.model.Reference> allReferences =
                    terser.getAllPopulatedChildElementsOfType(resource, org.hl7.fhir.r4.model.Reference.class);
            for (final org.hl7.fhir.r4.model.Reference reference : allReferences) {
                final IBaseResource contained = reference.getResource();
                if (resourceIsEmpty((org.hl7.fhir.r4.model.Base) contained)) {
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

    private void stripEmptyContainedStu3(final org.hl7.fhir.dstu3.model.Bundle bundle) {
        final FhirTerser terser = fhirContextRegistry.getContext(Spec.Version.STU3).newTerser();
        int resourceIndex = 1;
        for (final org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            final org.hl7.fhir.dstu3.model.Resource resource = entry.getResource();
            if (resource == null) continue;
            final List<org.hl7.fhir.dstu3.model.Reference> allReferences =
                    terser.getAllPopulatedChildElementsOfType(resource, org.hl7.fhir.dstu3.model.Reference.class);
            for (final org.hl7.fhir.dstu3.model.Reference reference : allReferences) {
                final IBaseResource contained = reference.getResource();
                if (resourceIsEmpty((org.hl7.fhir.dstu3.model.Base) contained)) {
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

    private void stripEmptyContainedR4b(final org.hl7.fhir.r4b.model.Bundle bundle) {
        final FhirTerser terser = fhirContextRegistry.getContext(Spec.Version.R4B).newTerser();
        int resourceIndex = 1;
        for (final org.hl7.fhir.r4b.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            final org.hl7.fhir.r4b.model.Resource resource = entry.getResource();
            if (resource == null) continue;
            final List<org.hl7.fhir.r4b.model.Reference> allReferences =
                    terser.getAllPopulatedChildElementsOfType(resource, org.hl7.fhir.r4b.model.Reference.class);
            for (final org.hl7.fhir.r4b.model.Reference reference : allReferences) {
                final IBaseResource contained = reference.getResource();
                if (resourceIsEmpty((org.hl7.fhir.r4b.model.Base) contained)) {
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

    private void stripEmptyContainedR5(final org.hl7.fhir.r5.model.Bundle bundle) {
        final FhirTerser terser = fhirContextRegistry.getContext(Spec.Version.R5).newTerser();
        int resourceIndex = 1;
        for (final org.hl7.fhir.r5.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            final org.hl7.fhir.r5.model.Resource resource = entry.getResource();
            if (resource == null) continue;
            final List<org.hl7.fhir.r5.model.Reference> allReferences =
                    terser.getAllPopulatedChildElementsOfType(resource, org.hl7.fhir.r5.model.Reference.class);
            for (final org.hl7.fhir.r5.model.Reference reference : allReferences) {
                final IBaseResource contained = reference.getResource();
                if (resourceIsEmpty((org.hl7.fhir.r5.model.Base) contained)) {
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

    private boolean resourceIsEmpty(final org.hl7.fhir.r4.model.Base containedResource) {
        if (containedResource == null) {
            return true;
        }
        try {
            final org.hl7.fhir.r4.model.Base copy = containedResource.copy();
            copy.setIdBase(null);
            return copy.isEmpty();
        } catch (Exception e) {
            // because copy can be tricky but if it turns out to be tricky, it means element isn't empty..
            return false;
        }
    }

    private boolean resourceIsEmpty(final org.hl7.fhir.dstu3.model.Base containedResource) {
        if (containedResource == null) {
            return true;
        }
        try {
            return containedResource.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean resourceIsEmpty(final org.hl7.fhir.r4b.model.Base containedResource) {
        if (containedResource == null) {
            return true;
        }
        try {
            final org.hl7.fhir.r4b.model.Base copy = containedResource.copy();
            copy.setIdBase(null);
            return copy.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean resourceIsEmpty(final org.hl7.fhir.r5.model.Base containedResource) {
        if (containedResource == null) {
            return true;
        }
        try {
            final org.hl7.fhir.r5.model.Base copy = containedResource.copy();
            copy.setIdBase(null);
            return copy.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}
