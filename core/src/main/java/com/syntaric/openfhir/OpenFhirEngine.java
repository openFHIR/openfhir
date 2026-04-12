package com.syntaric.openfhir;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.manager.FhirConnectManager;
import com.syntaric.openfhir.manager.OptManager;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.tofhir.ToFhir;
import com.syntaric.openfhir.mapping.toopenehr.ToOpenEhr;
import com.syntaric.openfhir.metrics.MappingMetricsLogger;
import com.syntaric.openfhir.metrics.MappingTimer;
import com.syntaric.openfhir.util.OpenEhrTemplateUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Component
@Slf4j
public class OpenFhirEngine {

    private final ToOpenEhr fhirToOpenEhr;
    private final ToFhir openEhrToFhir;
    private final FhirConnectManager fhirConnectManager;
    private final OptManager optManager;
    private final ToAql toAql;
    private final FhirContext fhirContext;
    private final OpenEhrTemplateUtils templateUtils;
    private final FlatJsonUnmarshaller flatJsonUnmarshaller;
    private final ProdDefaultOpenFhirMappingContext prodOpenFhirMappingContext;
    private final OpenFhirStringUtils openFhirStringUtils;
    private final FhirPathR4 fhirPathR4;
    private final Gson gson;
    private final MappingMetricsLogger metricsLogger;

    @Autowired
    public OpenFhirEngine(final ToOpenEhr fhirToOpenEhr,
                          final ToFhir openEhrToFhir,
                          final FhirConnectManager fhirConnectManager,
                          final OptManager optManager,
                          final ToAql toAql,
                          final FhirContext fhirContext,
                          final OpenEhrTemplateUtils templateUtils,
                          final FlatJsonUnmarshaller flatJsonUnmarshaller,
                          final ProdDefaultOpenFhirMappingContext prodOpenFhirMappingContext,
                          final OpenFhirStringUtils openFhirStringUtils,
                          final FhirPathR4 fhirPathR4,
                          final Gson gson,
                          final MappingMetricsLogger metricsLogger) {
        this.fhirToOpenEhr = fhirToOpenEhr;
        this.openEhrToFhir = openEhrToFhir;
        this.fhirConnectManager = fhirConnectManager;
        this.optManager = optManager;
        this.toAql = toAql;
        this.fhirContext = fhirContext;
        this.templateUtils = templateUtils;
        this.flatJsonUnmarshaller = flatJsonUnmarshaller;
        this.prodOpenFhirMappingContext = prodOpenFhirMappingContext;
        this.openFhirStringUtils = openFhirStringUtils;
        this.fhirPathR4 = fhirPathR4;
        this.gson = gson;
        this.metricsLogger = metricsLogger;
    }

    /**
     * Returns context for when mapping from FHIR to openEHR, where context is either gotten from the provided
     * templateId, or if none is provided, it will loop through all user's contexts and try to apply
     * fhir condition on them.
     */
    private FhirConnectContextEntity getContextForFhir(final String templateId,
                                                       final String incomingFhirResource) {
        log.debug("Getting context for template {}", templateId);
        if (StringUtils.isNotBlank(templateId)) {
            return fhirConnectManager.findContextByTemplateId(templateId);
        }
        final List<FhirConnectContextEntity> allUserContexts = fhirConnectManager.allUserContextEntities();

        FhirConnectContextEntity fallbackContext = null;

        final Resource resource = parseIncomingFhirResource(incomingFhirResource);
        for (final FhirConnectContextEntity context : allUserContexts) {
            final Condition condition = getContextCondition(
                    context.getFhirConnectContext().getContext().getProfile().getUrl(),
                    resource.getResourceType().name());
            final String resourceType = resource.getResourceType().name();
            final String fhirPathWithCondition = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                    Arrays.asList(condition),
                    resourceType);
            if (StringUtils.isEmpty(fhirPathWithCondition) || fhirPathWithCondition.equals(resourceType)) {
                log.warn("No fhirpath defined for resource type, context relevant for all?");
                fallbackContext = context; // assign it to the variable in case there really is no other suitable one.. in which case, this will be returned (or the last occurrence of such a context mapper 'for all'
            } else {
                final Optional<Base> evaluated = fhirPathR4.evaluateFirst(resource, fhirPathWithCondition, Base.class);
                // if is present and is of type boolean, it also needs to be true
                // if is present and is not of type boolean, then the mere presence means the mapper is for this resource
                if (evaluated.isPresent() && ((!(evaluated.get() instanceof BooleanType)
                        || ((BooleanType) evaluated.get()).getValue()))) {
                    // mapper matches this Resource, it can handle it
                    log.info(
                            "Found a relevant context ({}) for this input fhir Resource. If there are more relevant other than this one, others will be ignored as this was the first one found.",
                            context.getId());
                    return context;
                }
            }
        }
        if (fallbackContext != null) {
            log.warn("Returning a fallback context for this input fhir Resource {}", fallbackContext.getId());
        }
        return fallbackContext;
    }

    private Condition getContextCondition(final String profileUrl, final String resourceType) {
        if (profileUrl == null || StringUtils.isEmpty(profileUrl)) {
            return null;
        }
        final Condition condition = new Condition();
        if (resourceType.equals("Bundle")) {
            condition.setTargetRoot("Bundle");
            condition.setTargetAttribute("entry.resource.meta.profile");
        } else {
            condition.setTargetRoot(resourceType);
            condition.setTargetAttribute("meta.profile");
        }
        condition.setOperator("one of");
        condition.setCriteria(profileUrl);
        return condition;
    }

    /**
     * Returns context for when mapping from openEHR to FHIR. It will first take the templateId from the incoming
     * payload (flatJson or Composition JSON) and then find it by user and templateId
     */
    private FhirConnectContextEntity getContextForOpenEhr(final String incomingOpenEhr,
                                                          final String incomingTemplateId) {
        log.debug("Getting context for template {}", incomingTemplateId);
        if (StringUtils.isNotBlank(incomingTemplateId)) {
            return fhirConnectManager.findContextByTemplateId(incomingTemplateId);
        }
        log.debug("Will try to obtain template id from the incoming openEhr object");
        final String templateId = getTemplateIdFromOpenEhr(incomingOpenEhr);
        if (StringUtils.isEmpty(templateId)) {
            log.warn("No templateId provided in the request nor could it be deduced from the payload.");
            return null;
        }
        return fhirConnectManager.findContextByTemplateId(templateId);
    }

    String getTemplateIdFromOpenEhr(final String incomingOpenEhr) {
        final JsonObject jsonObject;
        if (incomingOpenEhr.startsWith("[")) {
            // array
            final JsonArray arrayOfCompositions = gson.fromJson(incomingOpenEhr, JsonArray.class);
            jsonObject = arrayOfCompositions.get(0).getAsJsonObject();
        } else {
            jsonObject = gson.fromJson(incomingOpenEhr, JsonObject.class);
        }
        if (jsonObject.get("_type") != null && "COMPOSITION".equals(jsonObject.get("_type").getAsString())) {
            return jsonObject.get("archetype_details").getAsJsonObject().get("template_id").getAsJsonObject()
                    .get("value").getAsString();
        } else if (jsonObject.get("_type") != null) {
            // its a general ContentItem (i.e. OBSERVATION) and we can't know templateid
            return null;
        }
        final Set<String> keys = jsonObject.keySet();
        return new ArrayList<>(keys).get(0).split("/")[0];
    }


    /**
     * templateId is right now required. In the future, context mapper should also have a fhir path condition in there
     * so we could dynamically determine which context mapper is for which Request (incoming Bundle)
     * <p>
     * providing a templateId as an invoker though would mean performance optimization, although I am not sure
     * if the caller will always know which template to use?
     */
    public String toOpenEhr(final String incomingFhirResource, final String incomingTemplateId, final Boolean flat) {
        // get context and operational template
        final Resource resource = parseIncomingFhirResource(incomingFhirResource);
        final FhirConnectContextEntity fhirConnectContext = getContextForFhir(incomingTemplateId,
                incomingFhirResource);
        if (fhirConnectContext == null) {
            final String logMsg = String.format(
                    "Couldn't find any Context mapper for the given Resource. Make sure at least one Context mapper exists where fhir.resourceType is of this type (%s) and condition within the context mapper allows for it to be applied on this specific resource.",
                    resource.getResourceType().name());
            log.error(logMsg);
            throw new IllegalArgumentException(logMsg);
        }
        final String templateIdToUse = fhirConnectContext.getFhirConnectContext().getContext().getTemplate().getId();

        validatePrerequisites(fhirConnectContext, templateIdToUse);

        final WebTemplate webTemplate = templateUtils.parseWebTemplate(optManager.byTemplateIdAndOrganization(OpenFhirMappingContext.normalizeTemplateId(templateIdToUse)));

        prodOpenFhirMappingContext.initMappingCache(fhirConnectContext.getFhirConnectContext());


        if (flat != null && flat) {
            final JsonObject jsonObject = fhirToOpenEhr.fhirToFlatJsonObject(fhirConnectContext.getFhirConnectContext(),
                    resource,
                    webTemplate);
            return gson.toJson(jsonObject);
        } else {
            final Composition composition = fhirToOpenEhr.fhirToCompositionRm(
                    fhirConnectContext.getFhirConnectContext(),
                    resource,
                    webTemplate);
            return new CanonicalJson().marshal(composition);
        }
    }

    private Resource parseIncomingFhirResource(final String incomingFhirResource) {
        final IParser parser = fhirContext.newJsonParser();
        try {
            return parser.parseResource(Bundle.class, incomingFhirResource);
        } catch (final Exception e) {
            return (Resource) parser.parseResource(incomingFhirResource);
        }
    }

    public String toFhir(final String openEhrCompositionJson, final String incomingTemplateId) {
        final MappingTimer totalTimer = MappingTimer.start();

        // find the context mapper for the given template
        final FhirConnectContextEntity fhirConnectContext = getContextForOpenEhr(openEhrCompositionJson,
                incomingTemplateId);

        // validate prerequisites before starting any kind of mapping logic
        validatePrerequisites(fhirConnectContext,
                fhirConnectContext != null ? fhirConnectContext.getFhirConnectContext().getContext()
                                             .getTemplate().getId() : incomingTemplateId);

        final String templateIdToUse = fhirConnectContext.getFhirConnectContext().getContext().getTemplate()
                .getId(); // fhirConnectContext can not be null because prerequisites are validated above

        final MappingTimer cacheTimer = MappingTimer.start();
        final WebTemplate webTemplate = templateUtils.parseWebTemplate(optManager.byTemplateIdAndOrganization(OpenFhirMappingContext.normalizeTemplateId(templateIdToUse)));
        prodOpenFhirMappingContext.initMappingCache(fhirConnectContext.getFhirConnectContext());
        metricsLogger.record("toFhir.initCache", "template=" + templateIdToUse, cacheTimer.elapsedMs());

        final IncomingOpenEhrType incomingOpenEhrType = deduceIncomingPayloadType(openEhrCompositionJson);
        final MappingTimer mappingTimer = MappingTimer.start();
        final Bundle fhir;
        if (incomingOpenEhrType == IncomingOpenEhrType.COMPOSITION) {
            final List<Composition> compositions = parseCompositions(openEhrCompositionJson);
            fhir = openEhrToFhir.compositionsToFhir(fhirConnectContext.getFhirConnectContext(),
                    compositions,
                    webTemplate);
        } else if (incomingOpenEhrType == IncomingOpenEhrType.CONTENT_ITEM) {
            final List<ContentItem> contentItems = parseContentItem(openEhrCompositionJson);
            fhir = openEhrToFhir.contentItemsToFhir(fhirConnectContext.getFhirConnectContext(),
                    contentItems,
                    webTemplate);
        } else {
            // flat
            final List<Composition> compositions = parseFlat(openEhrCompositionJson, webTemplate);
            fhir = openEhrToFhir.compositionsToFhir(fhirConnectContext.getFhirConnectContext(),
                    compositions,
                    webTemplate);
        }
        metricsLogger.record("toFhir.mapping", "template=" + templateIdToUse + " type=" + incomingOpenEhrType,
                mappingTimer.elapsedMs());

        final String encoded = fhirContext.newJsonParser().encodeResourceToString(fhir);
        metricsLogger.record("toFhir.total", "template=" + templateIdToUse, totalTimer.elapsedMs());
        return encoded;
    }

    List<Composition> parseCompositions(final String marshalled) {
        final List<Composition> compositions = new ArrayList<>();
        final CanonicalJson canonicalJson = new CanonicalJson();
        if (marshalled.startsWith("[")) {
            // is array and most definitely in canonical
            final JsonArray arrayOfCompositions = gson.fromJson(marshalled, JsonArray.class);
            for (final JsonElement composition : arrayOfCompositions) {
                final String serializedComposition = composition.toString();
                compositions.add(canonicalJson.unmarshal(serializedComposition));
            }
            return compositions;
        } else {
            compositions.add(canonicalJson.unmarshal(marshalled));
        }
        if (compositions.stream().anyMatch(s -> s.getContent().isEmpty())) {
            log.error("Composition not properly unmarshalled. Empty content. Aborting translation.");
            throw new IllegalArgumentException(
                    "Composition not properly unmarshalled. Empty content. Aborting translation. See log for more info.");
        }
        return compositions;
    }

    List<ContentItem> parseContentItem(final String marshalled) {
        final List<ContentItem> contentItems = new ArrayList<>();
        final CanonicalJson canonicalJson = new CanonicalJson();
        if (marshalled.startsWith("[")) {
            // is array and most definitely in canonical
            final JsonArray arrayOfCompositions = gson.fromJson(marshalled, JsonArray.class);
            for (final JsonElement composition : arrayOfCompositions) {
                final String serializedComposition = composition.toString();
                contentItems.add(canonicalJson.unmarshal(serializedComposition, ContentItem.class));
            }
            return contentItems;
        } else {
            contentItems.add(canonicalJson.unmarshal(marshalled, ContentItem.class));
        }
        return contentItems;
    }

    List<Composition> parseFlat(final String marshalled,
                                final WebTemplate webTemplate) {
        final List<Composition> compositions = new ArrayList<>();
        compositions.add(flatJsonUnmarshaller.unmarshal(marshalled, webTemplate));
        if (compositions.stream().anyMatch(s -> s.getContent().isEmpty())) {
            log.error("Composition not properly unmarshalled. Empty content. Aborting translation.");
            throw new IllegalArgumentException(
                    "Composition not properly unmarshalled. Empty content. Aborting translation. See log for more info.");
        }
        return compositions;
    }

    /**
     * Validating prerequisites for the mapping, which are that fhir connect context mapper actually exists, that
     * operational template exists within the openFHIR state and that it's a valid one (can be parsed to WebTemplate).
     *
     * @param fhirConnectContext context mapper as found in the database based on the template id
     * @param templateId         template id of the operational template used for mapping
     */
    private void validatePrerequisites(final FhirConnectContextEntity fhirConnectContext, final String templateId) {
        if (fhirConnectContext == null) {
            final String format = String.format(
                    "Couldn't find a Context Mapper for the inbound request. If using flat format for the input body (or a ContentItem instead of a whole Composition), make sure you set query parameter 'templateId' that matches a fhirConnectContext.openEHR.templateId value. Current template id '%s'",
                    templateId);
            log.error(format);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, format);
        }
        final WebTemplate webTemplate = templateUtils.parseWebTemplate(optManager.byTemplateIdAndOrganization(OpenFhirMappingContext.normalizeTemplateId(templateId)));
        if (webTemplate == null) {
            log.error("Web template couldn't be created from an operation template {}.", templateId);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, String.format(
                    "Could not create WebTemplate from this OPT '%s'. Please validate the template or contact OpenFHIR support team.",
                    templateId));
        }
    }

    public ToAqlResponse toAql(final ToAqlRequest toAqlRequest) {
        try {
            ToAqlResponse aql = toAql.toAql(toAqlRequest);
            if (aql.getAqls() != null && StringUtils.isNotEmpty(toAqlRequest.getEhrId())) {
                for (ToAqlResponse.AqlResponse aqlAql : aql.getAqls()) {
                    aqlAql.setAql(aqlAql.getAql().replace("{{ehrid}}", toAqlRequest.getEhrId()));
                }
            }
            return aql;
        } catch (final Exception e) {
            log.error("Error trying to get AQL", e);
            throw e;
        }
    }

    private IncomingOpenEhrType deduceIncomingPayloadType(final String incomingOpenEhr) {
        final JsonObject jsonObject;
        if (incomingOpenEhr.startsWith("[")) {
            // array
            final JsonArray arrayOfCompositions = gson.fromJson(incomingOpenEhr, JsonArray.class);
            jsonObject = arrayOfCompositions.get(0).getAsJsonObject();
        } else {
            jsonObject = gson.fromJson(incomingOpenEhr, JsonObject.class);
        }
        if (jsonObject.get("_type") != null && "COMPOSITION".equals(jsonObject.get("_type").getAsString())) {
            return IncomingOpenEhrType.COMPOSITION;
        } else if (jsonObject.get("_type") != null) {
            return IncomingOpenEhrType.CONTENT_ITEM;
        }
        return IncomingOpenEhrType.FLAT;
    }

    public enum IncomingOpenEhrType {
        COMPOSITION, CONTENT_ITEM, FLAT
    }

}
