package com.syntaric.openfhir.mapping.toopenehr;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.FhirTerser;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datatypes.CodePhrase;
import com.nedap.archie.rm.generic.PartySelf;
import com.nedap.archie.rm.support.identification.TerminologyId;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
@Slf4j
public class ToOpenEhrPrePostProcessor implements ToOpenEhrPrePostProcessorInterface {

    private final FhirContextRegistry fhirContextRegistry;

    public ToOpenEhrPrePostProcessor(final FhirContextRegistry fhirContextRegistry) {
        this.fhirContextRegistry = fhirContextRegistry;
    }

    @Override
    public void postProcess(final Composition composition) {
// default values; set if not already set by mappings
        if (composition.getLanguage() == null) {
            composition.setLanguage(new CodePhrase(new TerminologyId("ISO_639-1"), "en"));
        }
        if (composition.getTerritory() == null) {
            composition.setTerritory(new CodePhrase(new TerminologyId("ISO_3166-1"), "DE"));
        }
        if (composition.getComposer() == null) {
            composition.setComposer(new PartySelf());
        }
    }

    @Override
    public void postProcess(final JsonObject compositionFlatFormat) {

    }

    @Override
    public void preProcess(final FhirConnectContext context, final IAnyResource startingResource) {
        if(startingResource instanceof Bundle bundle) {
            fixReferencedResources(bundle, fhirContextRegistry.getContext(getVersion(context)));
        }
    }

    private Spec.Version getVersion(final FhirConnectContext context) {
        if (context != null && context.getSpec() != null && context.getSpec().getVersion() != null) {
            return context.getSpec().getVersion();
        }
        return Spec.Version.R4;
    }

    /**
     * Will loop through all Resources within a Bundle and if it's referenced from elsewhere, add it to contained
     * because our resolve() only works if reference has a contains, see FhirProducer setEvaluationContext on
     * fhirPath
     */
    private void fixReferencedResources(final Bundle bundle, final FhirContext fhirContext) {
        bundle.getEntry().forEach(entry -> {
            final FhirTerser fhirTerser = fhirContext.newTerser();
            final List<Reference> allReferences = fhirTerser.getAllPopulatedChildElementsOfType(
                    entry.getResource(), Reference.class);
            // check if they're resolveable from within the Bundle
            for (final Reference aReference : allReferences) {
                final String referenceString = aReference.getReference();
                if (aReference.getResource() != null) {
                    // is ok
                    if (referenceString.startsWith("#")) {
                        aReference.setReference(referenceString.substring(1));
                    }
                    continue;
                }
                if (StringUtils.isBlank(referenceString)) {
                    continue;
                }
                final Resource referencedResource = bundle.getEntry().stream()
                        .filter(en -> en.getResource().getId() != null && (
                                en.getResource().getId().equals(referenceString)
                                        || en.getResource().getId().equals(referenceString.replace("#", ""))))
                        .map(BundleEntryComponent::getResource)
                        .findAny().orElse(null);
                if (referenceString.startsWith("#")) {
                    aReference.setReference(referenceString.substring(1));
                }
                aReference.setResource(referencedResource);
            }
        });
    }
}
