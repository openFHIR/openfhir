package com.syntaric.openfhir.mapping.tofhir;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.HelpersCreator;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.metrics.MappingMetricsLogger;
import com.syntaric.openfhir.metrics.MappingTimer;
import com.syntaric.openfhir.util.OpenEhrTemplateUtils;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.marshal.FlatJsonMarshaller;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ToFhir {

    final private FlatJsonMarshaller flatJsonMarshaller;
    final private OpenEhrTemplateUtils openEhrApplicationScopedUtils;
    final private Gson gson;
    final private HelpersCreator helpersCreator;
    final private ToFhirPrePostProcessorInterface toFhirPrePostProcessor;
    final private ToFhirMappingEngine toFhirMappingEngine;
    final private ContentItemCompositionBuilder contentItemCompositionBuilder;
    final private MappingMetricsLogger metricsLogger;

    @Autowired
    public ToFhir(final FlatJsonMarshaller flatJsonMarshaller,
                  final OpenEhrTemplateUtils openEhrApplicationScopedUtils,
                  final Gson gson,
                  final HelpersCreator helpersCreator,
                  final ToFhirPrePostProcessorInterface toFhirPrePostProcessor,
                  final ToFhirMappingEngine toFhirMappingEngine,
                  final ContentItemCompositionBuilder contentItemCompositionBuilder,
                  final MappingMetricsLogger metricsLogger) {
        this.flatJsonMarshaller = flatJsonMarshaller;
        this.openEhrApplicationScopedUtils = openEhrApplicationScopedUtils;
        this.gson = gson;
        this.helpersCreator = helpersCreator;
        this.toFhirPrePostProcessor = toFhirPrePostProcessor;
        this.toFhirMappingEngine = toFhirMappingEngine;
        this.contentItemCompositionBuilder = contentItemCompositionBuilder;
        this.metricsLogger = metricsLogger;
    }

    public IBaseBundle contentItemsToFhir(final FhirConnectContext context,
                                     final List<ContentItem> contentItems,
                                     final WebTemplate webTemplate) {
        toFhirPrePostProcessor.preProcessContentItems(context, contentItems, webTemplate);

        final Composition composition = contentItemCompositionBuilder.buildComposition(contentItems,
                webTemplate);
        return compositionsToFhir(context, List.of(composition), webTemplate);
    }

    public IBaseBundle compositionsToFhir(final FhirConnectContext context,
                                     final List<Composition> compositions,
                                     final WebTemplate webTemplate) {
        // create flat from composition
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(
                context.getContext().getTemplate().getId());

        toFhirPrePostProcessor.preProcess(context, compositions, webTemplate);

        final Spec.Version fhirVersion = context.getSpec() != null && context.getSpec().getVersion() != null
                ? context.getSpec().getVersion() : Spec.Version.R4;

        final IBaseBundle returningBundle = toFhirMappingEngine.prepareBundle(fhirVersion);
        int compositionIndex = 0;
        for (final Composition composition : compositions) {
            final String compositionContext = "template=" + templateId + " composition#" + compositionIndex++;

            final MappingTimer flatTimer = MappingTimer.start();
            final String flatJson = flatJsonMarshaller.toFlatJson(composition, webTemplate);
            final JsonObject flatJsonObject = gson.fromJson(flatJson, JsonObject.class);
            metricsLogger.record("compositionsToFhir.flatJson", compositionContext, flatTimer.elapsedMs());

            final MappingTimer helpersTimer = MappingTimer.start();
            final Map<String, List<MappingHelper>> mappingHelpers = helpersCreator.constructHelpers(templateId,
                                                                                                    context.getContext()
                                                                                                            .getStart(),
                                                                                                    context.getContext()
                                                                                                            .getArchetypes(),
                                                                                                    webTemplate);
            metricsLogger.record("compositionsToFhir.constructHelpers", compositionContext, helpersTimer.elapsedMs());

            final MappingTimer mapTimer = MappingTimer.start();
            final IBaseBundle creatingBundle = toFhirMappingEngine.mapToFhir(mappingHelpers, flatJsonObject, fhirVersion);
            metricsLogger.record("compositionsToFhir.mapToFhir", compositionContext, mapTimer.elapsedMs());

            toFhirMappingEngine.mergeEntries(returningBundle, creatingBundle, fhirVersion);
        }

        final IBaseBundle relevantBundle = extractBundleFromBundle(returningBundle, fhirVersion);
        return toFhirPrePostProcessor.postProcess(relevantBundle, context, compositions, webTemplate);
    }

    private IBaseBundle extractBundleFromBundle(final IBaseBundle returningBundle, final Spec.Version fhirVersion) {
        for (final IBaseResource entry : toFhirMappingEngine.getBundleResources(returningBundle, fhirVersion)) {
            if (entry instanceof IBaseBundle nestedBundle) {
                return nestedBundle;
            }
        }
        return returningBundle;
    }
}
