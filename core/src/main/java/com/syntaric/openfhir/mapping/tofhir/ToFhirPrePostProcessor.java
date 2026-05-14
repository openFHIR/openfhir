package com.syntaric.openfhir.mapping.tofhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleBuilder;
import ca.uhn.fhir.util.FhirTerser;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Slf4j
public class ToFhirPrePostProcessor implements ToFhirPrePostProcessorInterface {

    final String IPS_PROFILE = "http://hl7.org/fhir/uv/ips/StructureDefinition/Composition-uv-ips";

    final private boolean containedToSeparateEntites;

    final protected FhirContextRegistry fhirContextRegistry;

    public ToFhirPrePostProcessor(final FhirContextRegistry fhirContextRegistry,
                                  @Value("${openfhir.contained-to-separate-entities:true}") boolean containedToSeparateEntites) {
        this.fhirContextRegistry = fhirContextRegistry;
        this.containedToSeparateEntites = containedToSeparateEntites;
    }

    @Override
    public IBaseBundle postProcess(final IBaseBundle mappedResource,
                              final FhirConnectContext context,
                              final List<Composition> compositions,
                              final WebTemplate webTemplate) {
        stripEmptyContained(mappedResource, getVersion(context));

        if (IPS_PROFILE.equals(context.getContext().getProfile().getUrl())) {
            // IPS
            postProcessIps((org.hl7.fhir.r4.model.Bundle) mappedResource);
        }

        return moveContainedToSeparateEntries(mappedResource, getVersion(context));
    }

    @Override
    public void preProcess(final FhirConnectContext context, final List<Composition> compositions,
                           final WebTemplate webTemplate) {

    }

    @Override
    public void preProcessContentItems(FhirConnectContext context, List<ContentItem> contentItems, WebTemplate webTemplate) {

    }

    public IBaseBundle moveContainedToSeparateEntries(final IBaseBundle bundle, final Spec.Version fhirVersion) {
        if(!containedToSeparateEntites) {
            return bundle;
        }
        final FhirContext ctx = fhirContextRegistry.getContext(fhirVersion);
        final FhirTerser fhirTerser = ctx.newTerser();
        final BundleBuilder bundleBuilder = new BundleBuilder(ctx);

        getResourcesFromBundle(bundle, fhirVersion).forEach(resource -> {
            final List<IBaseReference> allReferences = fhirTerser.getAllPopulatedChildElementsOfType(
                    resource, IBaseReference.class);
            bundleBuilder.addCollectionEntry(resource);
            for (final IBaseReference reference : allReferences) {
                final IBaseResource containedResource = reference.getResource();
                if (containedResource == null || containedResource.isEmpty()) {
                    continue;
                }

                if (!containedResource.getIdElement().isEmpty()
                        && !containedResource.getIdElement().getValue().startsWith("#")) {
                    reference.setReference(String.format("%s/%s", containedResource.fhirType(),
                            containedResource.getIdElement().getIdPart()));
                } else {
                    final String generatedResId = "urn:uuid:" + UUID.randomUUID();
                    reference.setReference(generatedResId);
                    containedResource.setId(generatedResId);
                }
                bundleBuilder.addCollectionEntry(containedResource);
                reference.setResource(null);
            }
        });

        return copyBundleMetadata(bundle, bundleBuilder.getBundle(), fhirVersion);
    }

    private IBaseBundle copyBundleMetadata(final IBaseBundle source, final IBaseBundle target,
                                           final Spec.Version fhirVersion) {
        switch (fhirVersion) {
            case STU3 -> {
                final org.hl7.fhir.dstu3.model.Bundle src = (org.hl7.fhir.dstu3.model.Bundle) source;
                final org.hl7.fhir.dstu3.model.Bundle tgt = (org.hl7.fhir.dstu3.model.Bundle) target;
                tgt.setMeta(src.getMeta());
                if (src.getType() != null) {
                    tgt.setType(src.getType());
                }
                tgt.setIdentifier(src.getIdentifier());
            }
            case R4B -> {
                final org.hl7.fhir.r4b.model.Bundle src = (org.hl7.fhir.r4b.model.Bundle) source;
                final org.hl7.fhir.r4b.model.Bundle tgt = (org.hl7.fhir.r4b.model.Bundle) target;
                tgt.setMeta(src.getMeta());
                if (src.getType() != null) {
                    tgt.setType(src.getType());
                }
                tgt.setTimestamp(src.getTimestamp());
                tgt.setIdentifier(src.getIdentifier());
            }
            case R5 -> {
                final org.hl7.fhir.r5.model.Bundle src = (org.hl7.fhir.r5.model.Bundle) source;
                final org.hl7.fhir.r5.model.Bundle tgt = (org.hl7.fhir.r5.model.Bundle) target;
                tgt.setMeta(src.getMeta());
                if (src.getType() != null) {
                    tgt.setType(src.getType());
                }
                tgt.setTimestamp(src.getTimestamp());
                tgt.setIdentifier(src.getIdentifier());
            }
            default -> {
                final org.hl7.fhir.r4.model.Bundle src = (org.hl7.fhir.r4.model.Bundle) source;
                final org.hl7.fhir.r4.model.Bundle tgt = (org.hl7.fhir.r4.model.Bundle) target;
                tgt.setMeta(src.getMeta());
                if (src.getType() != null) {
                    tgt.setType(src.getType());
                }
                tgt.setTimestamp(src.getTimestamp());
                tgt.setIdentifier(src.getIdentifier());
            }
        }
        return target;
    }

    private void postProcessIps(final org.hl7.fhir.r4.model.Bundle mappedResource) {
        mappedResource.setIdentifier(new org.hl7.fhir.r4.model.Identifier().setSystem("urn:oid:2.16.840.1.113883.3.72").setValue(UUID.randomUUID().toString()));
        mappedResource.setType(org.hl7.fhir.r4.model.Bundle.BundleType.DOCUMENT);
        mappedResource.getMeta().setProfile(List.of(new org.hl7.fhir.r4.model.CanonicalType("http://hl7.org/fhir/uv/ips/StructureDefinition/Bundle-uv-ips")));
        mappedResource.setTimestamp(new Date());
        org.hl7.fhir.r4.model.Composition compositionResource = (org.hl7.fhir.r4.model.Composition) mappedResource.getEntryFirstRep().getResource();
        if(compositionResource.getDate() == null) {
            compositionResource.setDate(new Date());
        }
        if(compositionResource.getAuthor().isEmpty()) {
            compositionResource.addAuthor(new org.hl7.fhir.r4.model.Reference().setDisplay("openFHIR"));
        }
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
