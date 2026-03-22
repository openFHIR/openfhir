package com.syntaric.openfhir.mapping.tofhir;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.OpenEhrCachedUtils;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToFhir {

    final private FlatJsonMarshaller flatJsonMarshaller;
    final private OpenEhrCachedUtils openEhrApplicationScopedUtils;
    final private Gson gson;
    final private HelpersCreator helpersCreator;
    final private ToFhirPrePostProcessorInterface toFhirPrePostProcessor;
    final private ToFhirMappingEngine toFhirMappingEngine;
    final private ContentItemCompositionBuilder contentItemCompositionBuilder;

    @Autowired
    public ToFhir(final FlatJsonMarshaller flatJsonMarshaller,
                  final OpenEhrCachedUtils openEhrApplicationScopedUtils,
                  final Gson gson,
                  final HelpersCreator helpersCreator,
                  final ToFhirPrePostProcessorInterface toFhirPrePostProcessor,
                  final ToFhirMappingEngine toFhirMappingEngine,
                  final ContentItemCompositionBuilder contentItemCompositionBuilder) {
        this.flatJsonMarshaller = flatJsonMarshaller;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.gson = gson;
        this.helpersCreator = helpersCreator;
        this.toFhirPrePostProcessor = toFhirPrePostProcessor;
        this.toFhirMappingEngine = toFhirMappingEngine;
        this.contentItemCompositionBuilder = contentItemCompositionBuilder;
    }

    public Bundle contentItemsToFhir(final FhirConnectContext context,
                                     final List<ContentItem> contentItems,
                                     final OPERATIONALTEMPLATE operationaltemplate) {
        toFhirPrePostProcessor.preProcessContentItems(context, contentItems, operationaltemplate);

        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
        final Composition composition = contentItemCompositionBuilder.buildComposition(contentItems,
                webTemplate);
        return compositionsToFhir(context, List.of(composition), operationaltemplate);
    }

    public Bundle compositionsToFhir(final FhirConnectContext context,
                                     final List<Composition> compositions,
                                     final OPERATIONALTEMPLATE operationaltemplate) {
        // create flat from composition
        final WebTemplate webTemplate = openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);

        final String templateId = OpenFhirMappingContext.normalizeTemplateId(
                context.getContext().getTemplate().getId());

        toFhirPrePostProcessor.preProcess(context, compositions, operationaltemplate);

        final Bundle returningBundle = prepareBundle();
        for (final Composition composition : compositions) {
            final String flatJson = flatJsonMarshaller.toFlatJson(composition, webTemplate);
            final JsonObject flatJsonObject = gson.fromJson(flatJson, JsonObject.class);

            final Map<String, List<MappingHelper>> mappingHelpers = helpersCreator.constructHelpers(templateId,
                                                                                                    context.getContext()
                                                                                                            .getStart(),
                                                                                                    context.getContext()
                                                                                                            .getArchetypes(),
                                                                                                    webTemplate);

            final Bundle creatingBundle = toFhirMappingEngine.mapToFhir(mappingHelpers, flatJsonObject);

            returningBundle.getEntry().addAll(creatingBundle.getEntry());
        }

        final Bundle relevantBundle = extractBundleFromBundle(returningBundle);

        return toFhirPrePostProcessor.postProcess(relevantBundle, context, compositions, operationaltemplate);
    }

    private Bundle extractBundleFromBundle(final Bundle returningBundle) {
        for (final BundleEntryComponent bundleEntryComponent : returningBundle.getEntry()) {
            if (bundleEntryComponent.getResource() instanceof Bundle bundle) {
                return bundle;
            }
        }
        return returningBundle;
    }

    private Bundle prepareBundle() {
        final Bundle bundle = new Bundle();
        bundle.setType(BundleType.COLLECTION);
        return bundle;
    }
}
