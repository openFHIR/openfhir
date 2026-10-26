package com.syntaric.openfhir.mapping.toaql;

import ca.uhn.fhir.model.api.annotation.SearchParamDefinition;
import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.RmAttributePins;
import com.syntaric.openfhir.mapping.helpers.RmTypes;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ToAqlMappingEngine {

    final private OpenEhrAqlPopulator openEhrAqlPopulator;

    @Autowired
    public ToAqlMappingEngine(OpenEhrAqlPopulator openEhrAqlPopulator) {
        this.openEhrAqlPopulator = openEhrAqlPopulator;
    }

    public ToAqlResponse map(final List<ToAql.ToAqlModels> modelsToMap,
                             final String resourceType,
                             final List<FhirQueryParam> queryParams,
                             final boolean narrowToTemplate) {
        final ToAqlResponse toAqlResponse = new ToAqlResponse();
        // if there are no params, we should just use the base ones, i.e. SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.body_weight.v2] WHERE e/ehr_id/value='{{ehrid}}'
        if (queryParams == null || queryParams.isEmpty()) {
            return archetypeOnlyAql(modelsToMap, narrowToTemplate);
        }
        // For each param, collect all matched (context, helper, value) triples
        final List<HelperValue> helperValues = new ArrayList<>();

        for (final FhirQueryParam queryParam : queryParams) {
            final List<ToAql.ToAqlModels> relevantModels = mapQueryToMappingHelper(queryParam, resourceType,
                                                                                   modelsToMap);
            if (!queryParam.isHandled() && (relevantModels == null || relevantModels.isEmpty())) {
                toAqlResponse.addUnhandledParam(queryParam.getName(), ToAqlResponse.UnhandledParamType.ERROR,
                                                "No Mapping found that would match this param name (fhirpath) and is not included in the AQL.");
            } else {
                for (final ToAql.ToAqlModels model : relevantModels) {
                    for (final MappingHelper helper : model.getMappingHelpers()) {
                        helperValues.add(new HelperValue(model.getContext(), helper, queryParam, model.getMainArchetype()));
                    }
                }
            }
        }

        // Group by context and build one AQL per context combining all matched conditions
        final List<FhirConnectContextEntity> distinctContexts = helperValues.stream()
                .map(hv -> hv.context)
                .distinct()
                .toList();
        for (final FhirConnectContextEntity context : distinctContexts) {
            final List<HelperValue> forContext = helperValues.stream()
                    .filter(hv -> hv.context == context)
                    .toList();
            final String mainArchetype = forContext.get(0).getMainArchetype();
            final List<ToAqlResponse.AqlResponse> aqls = createAqls(forContext, context.getFhirConnectContext().getContext(),
                    mainArchetype, narrowToTemplate);
            toAqlResponse.addAqls(aqls);
        }

        if (toAqlResponse.getAqls() == null || toAqlResponse.getAqls().isEmpty()) {
            final ToAqlResponse archOnlyResponse = archetypeOnlyAql(modelsToMap, narrowToTemplate);
            for (FhirQueryParam queryParam : queryParams) {
                if (!queryParam.isHandled()) {
                    archOnlyResponse.addUnhandledParam(queryParam.getName(), ToAqlResponse.UnhandledParamType.ERROR,
                                                       "Parameter was not handled nor included in the AQL.");
                }
            }
            return archOnlyResponse;
        }

        // a param all of whose mappings address what a WHERE cannot compare (an EVENT, a CLUSTER ...) is left out
        helperValues.stream()
                .map(HelperValue::getValue)
                .distinct()
                .filter(queryParam -> !queryParam.isHandled())
                .forEach(queryParam -> toAqlResponse.addUnhandledParam(
                        queryParam.getName(), ToAqlResponse.UnhandledParamType.WARNING,
                        "The mappings of this param address no data value an AQL can compare; it is not included "
                                + "in the AQL."));

        return toAqlResponse;
    }

    ToAqlResponse archetypeOnlyAql(final List<ToAql.ToAqlModels> modelsToMap,
                                   final boolean narrowToTemplate) {
        final ToAqlResponse response = new ToAqlResponse();
        for (ToAql.ToAqlModels aModel : modelsToMap) {
            final Context context = aModel.getContext().getFhirConnectContext().getContext();
            final String compositionArchetype = context.getStart();
            final List<MappingHelper> mappingHelpers = aModel.getMappingHelpers();
            final String archetype = mappingHelpers.get(0).getArchetype();
            final String commaDelimetedArchetypes = mappingHelpers.stream().map(MappingHelper::getArchetype).distinct()
                    .collect(Collectors.joining(","));
            final String entryAql = "SELECT h FROM EHR e CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}'";
            response.addAql(new ToAqlResponse.AqlResponse(
                    String.format(entryAql, getEntryType(archetype), commaDelimetedArchetypes),
                    ToAqlResponse.AqlType.ENTRY,
                    context.getTemplate().getId()));

            if (narrowToTemplate) {
                final String compositionAql = "SELECT c from EHR e CONTAINS COMPOSITION c [%s] CONTAINS %s [%s] WHERE e/ehr_id/value='{{ehrid}}'";
                response.addAql(new ToAqlResponse.AqlResponse(
                        String.format(compositionAql, compositionArchetype, getEntryType(archetype),
                                      commaDelimetedArchetypes), ToAqlResponse.AqlType.COMPOSITION,
                        context.getTemplate().getId()));
            } else {
                final String compositionAql = "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}'";
                response.addAql(new ToAqlResponse.AqlResponse(
                        String.format(compositionAql, getEntryType(archetype), commaDelimetedArchetypes),
                        ToAqlResponse.AqlType.COMPOSITION,
                        context.getTemplate().getId()));
            }
        }
        return response;
    }

    private List<ToAqlResponse.AqlResponse> createAqls(final List<HelperValue> helperValues,
                                                       final Context context,
                                                       final String mainArchetype,
                                                       final boolean narrowToTemplate) {
        final List<ToAqlResponse.AqlResponse> responses = new ArrayList<>();
        final List<HelperValue> typedEntries = helperValues.stream()
                .filter(this::possibleTypesAreAcceptableForAqlTranslation)
                .toList();
        if (typedEntries.isEmpty()) {
            return responses;
        }
        final String entryAql = createEntryAql(typedEntries, mainArchetype);
        if (isValidAql(entryAql)) {
            responses.add(new ToAqlResponse.AqlResponse(entryAql, ToAqlResponse.AqlType.ENTRY,
                                                        context.getTemplate().getId()));
        }
        // reset handled
        helperValues.forEach(h -> h.getValue().setHandled(false));
        final String compositionAql = createCompositionAql(typedEntries, context.getStart(), narrowToTemplate);
        if (isValidAql(compositionAql)) {
            responses.add(new ToAqlResponse.AqlResponse(compositionAql, ToAqlResponse.AqlType.COMPOSITION,
                                                        context.getTemplate().getId()));
        }
        return responses;
    }

    /**
     * Whether the helper's openEHR path can be compared in a WHERE: an attribute of a data value, or a whole data
     * value of a type that has an attribute to compare. A node that holds no data value (EVENT, CLUSTER, a
     * participation ...) has nothing to compare a search value to.
     */
    boolean possibleTypesAreAcceptableForAqlTranslation(final HelperValue helperValue) {
        final MappingHelper helper = helperValue.getHelper();
        if (FhirConnectConst.OPENEHR_TYPE_NONE.equals(helper.getHardcodedType())) {
            return false;
        }
        return RmAttributePins.pinnedBy(helper) != null || comparedAttribute(helper) != null;
    }

    /**
     * The attribute a whole data value is compared by: that of its type the mapping's {@code with.type} picks, or
     * the first of the types the template allows that has one. A search value is compared to a coded text's code, a
     * manual mapping's fixed value to its text.
     */
    private static RmAttributePins.Attribute comparedAttribute(final MappingHelper helper) {
        final List<String> types = RmTypes.preferred(helper);
        if (types == null) {
            return null;
        }
        final RmAttributePins.Attribute compared = types.stream()
                .map(RmAttributePins::compared)
                .filter(java.util.Objects::nonNull)
                .findFirst().orElse(null);
        if (compared != null && StringUtils.isNotEmpty(helper.getManualOpenEhrValue())
                && FhirConnectConst.DV_CODED_TEXT.equals(compared.rmType())) {
            // a manual mapping's fixed value is written to a coded text as its text (|value), so it is compared so
            return RmAttributePins.attribute(FhirConnectConst.DV_CODED_TEXT, "value");
        }
        return compared;
    }

    private boolean isValidAql(final String aql) {
        if (StringUtils.isEmpty(aql)) {
            return false;
        }
        try {
            AqlQueryParser.parse(aql);
        } catch (final Exception e) {
            log.error("Generated aql '{}' didn't parse. Err: {}", aql, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Single param:
     * "SELECT h from EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.height.v2] WHERE e/ehr_id/value='{{ehrid}}'
     * AND h/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value=500"
     * Multiple params:
     * "SELECT h from EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.height.v2] WHERE e/ehr_id/value='{{ehrid}}'
     * AND h/data[...]/value=500 AND h/data[...]/value=200"
     * The conditions' paths are relative to {@code entryArchetype}, so that is what {@code h} contains, also for a
     * mapping of a model slotted into it (a CLUSTER: {@code h/data[at0001]/items[openEHR-EHR-CLUSTER...]/...}).
     */
    String createEntryAql(final List<HelperValue> helperValues,
                          final String entryArchetype) {
        final String entryType = getEntryType(entryArchetype);
        final StringBuilder conditions = new StringBuilder();
        for (final HelperValue hv : helperValues) {
//            if (hv.getValue().isHandled()) {
//                continue;
//            }
            final String manualOpenEhrValue = hv.getHelper().getManualOpenEhrValue();
            if (!conditions.isEmpty()) {
                conditions.append(" AND h");
            }
            hv.getValue().setHandled(true);
            final MappingHelper helper = hv.getHelper();
            String pathWithoutArchetype = getPathWithoutArchetype(helper.getFullOpenEhrPath(),
                                                                  entryArchetype);

            if (pathWithoutArchetype != null) {
                final String condition = condition(helper, pathWithoutArchetype,
                                                   StringUtils.isEmpty(manualOpenEhrValue)
                                                           ? hv.getValue().getValue()
                                                           : manualOpenEhrValue);
                conditions.append(condition.startsWith("/") ? condition : ("/" + condition));
            }
        }
        return String.format("SELECT h FROM EHR e CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}' AND h%s",
                             entryType, entryArchetype, conditions.toString().startsWith("/") ? conditions : ("/"+conditions));
    }

    /**
     * The WHERE condition of one mapping on {@code path}, comparing an attribute on its strict AQL path: the one the
     * path ends in ({@code .../value/magnitude}, {@code .../defining_code/code_string}), or for a whole data value the
     * one its type is compared by ({@code magnitude} of a quantity, {@code defining_code/code_string} of a coded
     * text), through the ELEMENT's value when the path addresses an ELEMENT.
     */
    private String condition(final MappingHelper helper, final String path, final String expectedValue) {
        final RmAttributePins.Attribute pinned = RmAttributePins.pinnedBy(helper);
        if (pinned != null) {
            return openEhrAqlPopulator.comparison(strictAttributePath(helper, path), pinned, expectedValue);
        }
        final RmAttributePins.Attribute compared = comparedAttribute(helper);
        return openEhrAqlPopulator.comparison(RmAttributePins.aqlPath(path, addressesElement(helper, path), compared),
                                              compared, expectedValue);
    }

    /**
     * Whether {@code path} addresses an ELEMENT, whose data value is its {@code value}, rather than a node that is a
     * data value itself ({@code context/start_time}, {@code items[at0004]/value}). A helper not resolved against a
     * template says nothing; an archetype node ({@code items[at0004]}) is then taken for an ELEMENT.
     */
    private static boolean addressesElement(final MappingHelper helper, final String path) {
        final String nodeRmType = helper.getOpenEhrNodeRmType();
        return nodeRmType == null ? path.endsWith("]") : FhirConnectConst.ELEMENT.equals(nodeRmType);
    }

    /**
     * {@code path} with the attribute spelled the strict AQL way, whatever spelling the mapping used:
     * {@code items[at0130]/defining_code} becomes {@code items[at0130]/value/defining_code/code_string}.
     */
    private static String strictAttributePath(final MappingHelper helper, final String path) {
        final String written = helper.getFullOpenEhrPath();
        final String strict = helper.getPinnedAqlPath();
        final int difference = StringUtils.indexOfDifference(written, strict);
        if (difference < 0) {
            return path;
        }
        final String writtenTail = written.substring(difference);
        return path.substring(0, path.length() - writtenTail.length()) + strict.substring(difference);
    }

    /**
     * Single param:
     * "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and
     * c/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value=500"
     * Multiple params:
     * "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and
     * c/content[openEHR-EHR-OBSERVATION.height.v2]/data[...]/value=500 AND
     * c/content[openEHR-EHR-OBSERVATION.height.v2]/data[...]/value=200"
     * or narrowed down to the template:
     * "SELECT c from EHR e CONTAINS COMPOSITION c [openEHR-EHR-COMPOSITION.growth_chart.v0] WHERE
     * e/ehr_id/value='{{ehrid}}' and c/content[...]/...=500 AND c/content[...]/...=200"
     */
    String createCompositionAql(final List<HelperValue> helperValues,
                                final String compositionArchetype,
                                final boolean narrowToTemplate) {
        final StringBuilder conditions = new StringBuilder();
        for (final HelperValue hv : helperValues) {
//            if (hv.getValue().isHandled()) {
//                continue;
//            }
            final String manualOpenEhrValue = hv.getHelper().getManualOpenEhrValue();
            final boolean isOnComposition = hv.getHelper().getFullOpenEhrPath()
                    .startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC);

            if (isOnComposition) {
                if (!conditions.isEmpty()) {
                    conditions.append(" AND c");
                } else {
                    conditions.append("c");
                }
            } else {
                if (!conditions.isEmpty()) {
                    conditions.append(" AND c/content[");
                    conditions.append(compositionArchetype).append("]/");
                } else {
                    conditions.append("c/content[");
                    conditions.append(compositionArchetype).append("]/");
                }
            }
            hv.getValue().setHandled(true);
            final MappingHelper helper = hv.getHelper();
            String pathWithoutArchetype = getPathWithoutArchetype(helper.getFullOpenEhrPath(),
                                                                  compositionArchetype);

            if (pathWithoutArchetype != null) {
                conditions.append(condition(helper, pathWithoutArchetype,
                                            StringUtils.isEmpty(manualOpenEhrValue)
                                                    ? hv.getValue().getValue()
                                                    : manualOpenEhrValue));
            }
        }
        if (narrowToTemplate) {
            return String.format(
                    "SELECT c from EHR e CONTAINS COMPOSITION c CONTAINS %s [%s] WHERE e/ehr_id/value='{{ehrid}}' and %s",
                    getEntryType(compositionArchetype), compositionArchetype, conditions);
        } else {
            return String.format("SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and %s",
                                 conditions);
        }
    }

    /**
     * openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004] -> OBSERVATION
     */
    String getEntryType(final String fullOpenEhrPath) {
        return fullOpenEhrPath.substring(0, fullOpenEhrPath.indexOf(".")).replace("openEHR-EHR-", "");
    }

    /**
     * openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004] ->
     * data[at0002]/events[at0003]/data[at0001]/items[at0004]
     */
    String getPathWithoutArchetype(final String fullOpenEhrPath, final String archetype) {
        final String fullPath = fullOpenEhrPath
                .replace(FhirConnectConst.OPENEHR_COMPOSITION_FC, "")
                .replace(String.format("%s/", archetype), "");
        if (fullPath.contains(archetype)) {
            final String relevantArchetypeOnly = fullPath.split(archetype)[1];
            return relevantArchetypeOnly.substring(relevantArchetypeOnly.indexOf("]") + 1);
        }
        return fullPath;
    }

    List<ToAql.ToAqlModels> mapQueryToMappingHelper(final FhirQueryParam queryParam,
                                                    final String resourceType,
                                                    final List<ToAql.ToAqlModels> modelsToMap) {

        final String fhirPathForQueryName = getFhirPathForQueryName(resourceType, queryParam.getName());
        if (fhirPathForQueryName == null) {
            log.warn("No FHIR path found for query param '{}'", queryParam.getName());
            return List.of();
        }

        final List<ToAql.ToAqlModels> result = new ArrayList<>();
        for (final ToAql.ToAqlModels model : modelsToMap) {
            final String mainArchetype = model.getMappingHelpers().get(0).getArchetype();
            final List<MappingHelper> matched = findByFhirPath(model.getMappingHelpers(), fhirPathForQueryName,
                                                               queryParam);
            if (!matched.isEmpty()) {
                result.add(ToAql.ToAqlModels.builder()
                                   .context(model.getContext())
                                   .modelMappers(model.getModelMappers())
                                   .mappingHelpers(matched)
                                   .mainArchetype(mainArchetype)
                                   .build());
            }
        }
        return result;
    }

    List<MappingHelper> findByFhirPath(final List<MappingHelper> helpers,
                                       final String fhirPathForQueryName, final FhirQueryParam queryParam) {
        final List<MappingHelper> matched = new ArrayList<>();
        if (helpers == null) {
            return matched;
        }
        final List<String> paths = Arrays.stream(fhirPathForQueryName.split("\\|"))
                .map(String::trim)
                .toList();
        for (final MappingHelper helper : helpers) {
            if (helper.getFullFhirPath() == null) {
                continue;
            }
            if (FhirConnectConst.UNIDIRECTIONAL_TOFHIR.equals(helper.getUnidirectional())) {
                continue;
            }
            boolean isArchetypeOnlyMapping = helper.getFullFhirPath().equals(helper.getGeneratingResourceType());

            if (!isArchetypeOnlyMapping
                    && (paths.stream()
                    .anyMatch(p -> {
                        final String amendedIfMultiples = p.split(" as ")[0].trim();
                        final String withoutResourceType = amendedIfMultiples.substring(amendedIfMultiples.indexOf(".") + 1);
                        final String fhir = helper.getFhir() == null ? "" : helper.getFhir();
                        return
//                                withoutResourceType.contains(helper.getFullFhirPath())
//                                || helper.getFullFhirPath().contains(withoutResourceType)
                                fhir.equals(withoutResourceType) || fhir.startsWith(withoutResourceType) || (withoutResourceType.contains(fhir) && StringUtils.isNotEmpty(fhir));
                    }))
                    && !(FhirConnectConst.OPENEHR_TYPE_NONE.equals(helper.getHardcodedType()))
                    && (paths.stream().anyMatch(p -> {
                final String amendedIfMultiples = p.split(" as ")[0].trim();
                final String withoutResourceType = amendedIfMultiples.substring(amendedIfMultiples.indexOf(".") + 1);
                return fhirConditionPasses(queryParam, helper, withoutResourceType);
            }))) {
                matched.add(helper);
            }

            if (paths.stream().anyMatch(p -> {
                final String withoutResourceType = paths.get(0).substring(paths.get(0).indexOf(".") + 1);
                return fhirConditionPasses(queryParam, helper, withoutResourceType);
            })) {
                matched.addAll(findByFhirPath(helper.getChildren(), fhirPathForQueryName, queryParam));
            }
        }
        return matched;
    }

    boolean fhirConditionPasses(final FhirQueryParam queryParam, final MappingHelper helper,
                                final String queryParamPath) {
        if (queryParam == null) {
            // I think only relevant from tests?
            return true;
        }
        final List<Condition> fhirConditions = helper.getFhirConditions();
        if (fhirConditions == null || fhirConditions.isEmpty()) {
            return true;
        }

        return fhirConditions.stream().allMatch(condition -> {
            final List<String> targetAttributes = condition.getTargetAttributes();
            final String targetRoot = condition.getTargetRoot();
            if (targetAttributes != null && targetAttributes.stream()
                    .noneMatch(ta -> (String.format("%s.%s", targetRoot, ta)).contains(queryParamPath)
                            || queryParamPath.contains(String.format("%s.%s", targetRoot, ta)))) {
                return true;
            }
            final String operator = condition.getOperator();
            if (FhirConnectConst.CONDITION_OPERATOR_ONE_OF.equals(operator)) {
                return condition.getCriterias().stream().anyMatch(c -> c.equals(queryParam.getValue()));
            } else if (FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(operator)) {
                return condition.getCriterias().stream().noneMatch(c -> c.equals(queryParam.getValue()));
            } else {
                return true;
            }
        });
    }

    public String getFhirPathForQueryName(final String resourceType, final String paramName) {
        final List<SearchParamDefinition> searchParamDefinitions = getAllFields(getResourceClass(resourceType))
                .stream()
                .filter(x -> x.isAnnotationPresent(SearchParamDefinition.class))
                .map(x -> x.getAnnotation(SearchParamDefinition.class))
                .toList();

        return searchParamDefinitions.stream()
                .filter(sf -> sf.name().equals(paramName))
                .map(SearchParamDefinition::path)
                .findFirst().orElse(null);
    }

    private List<Field> getAllFields(final Class clazz) {
        final List<Field> allFields = FieldUtils.getAllFieldsList(clazz);
        if (IAnyResource.class.isAssignableFrom(clazz)) {
            allFields.addAll(FieldUtils.getAllFieldsList(IAnyResource.class));
        }
        return allFields;
    }

    private Class getResourceClass(final String resourceType) {
        try {
            return Class.forName(
                    Observation.class.getPackageName() + "." + resourceType); // which means this is bound to R4!
        } catch (final ClassNotFoundException e) {
            log.error("Unable to get class for {}", resourceType, e);
            return null;
        }
    }

    @Getter
    private static class HelperValue {

        private final FhirConnectContextEntity context;
        private final MappingHelper helper;
        private final FhirQueryParam value;
        private final String mainArchetype;

        HelperValue(final FhirConnectContextEntity context, final MappingHelper helper, final FhirQueryParam value,
                    final String mainArchetype) {
            this.context = context;
            this.helper = helper;
            this.value = value;
            this.mainArchetype = mainArchetype;
        }
    }
}
