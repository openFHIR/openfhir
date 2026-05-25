package com.syntaric.openfhir.mapping.toaql;

import ca.uhn.fhir.model.api.annotation.SearchParamDefinition;
import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.context.ContextQuery;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.hl7.fhir.r4.model.Observation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
            final List<ContextQuery> queries = context.getFhirConnectContext().getContext().getQuery();
            if (queries != null) {
                for (final ContextQuery query : queries) {
                    if (ToAql.queryMatchesAnyRule(resourceType, queryParams, query.getRules())) {
                        return new ToAqlResponse().addAql(query.getAql(), ToAqlResponse.AqlType.COMPOSITION);
                    }
                }
            }

            final List<HelperValue> forContext = helperValues.stream()
                    .filter(hv -> hv.context == context)
                    .toList();
            final String mainArchetype = forContext.get(0).getMainArchetype();
            final List<ToAqlResponse.AqlResponse> aqls = createAqls(forContext, context.getFhirConnectContext().getContext().getStart(),
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

        return toAqlResponse;
    }

    ToAqlResponse archetypeOnlyAql(final List<ToAql.ToAqlModels> modelsToMap,
                                   final boolean narrowToTemplate) {
        final ToAqlResponse response = new ToAqlResponse();
        for (ToAql.ToAqlModels aModel : modelsToMap) {
            final String compositionArchetype = aModel.getContext().getFhirConnectContext().getContext().getStart();
            final List<MappingHelper> mappingHelpers = aModel.getMappingHelpers();
            final String archetype = mappingHelpers.get(0).getArchetype();
            final String commaDelimetedArchetypes = mappingHelpers.stream().map(MappingHelper::getArchetype).distinct()
                    .collect(Collectors.joining(","));
            final String entryAql = "SELECT h FROM EHR e CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}'";
            response.addAql(new ToAqlResponse.AqlResponse(
                    String.format(entryAql, getEntryType(archetype), commaDelimetedArchetypes),
                    ToAqlResponse.AqlType.ENTRY));

            if (narrowToTemplate) {
                final String compositionAql = "SELECT c from EHR e CONTAINS COMPOSITION c [%s] CONTAINS %s [%s] WHERE e/ehr_id/value='{{ehrid}}'";
                response.addAql(new ToAqlResponse.AqlResponse(
                        String.format(compositionAql, compositionArchetype, getEntryType(archetype),
                                      commaDelimetedArchetypes), ToAqlResponse.AqlType.COMPOSITION));
            } else {
                final String compositionAql = "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}'";
                response.addAql(new ToAqlResponse.AqlResponse(
                        String.format(compositionAql, getEntryType(archetype), commaDelimetedArchetypes),
                        ToAqlResponse.AqlType.COMPOSITION));
            }
        }
        return response;
    }

    private List<ToAqlResponse.AqlResponse> createAqls(final List<HelperValue> helperValues,
                                                       final String compositionArchetype,
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
            responses.add(new ToAqlResponse.AqlResponse(entryAql, ToAqlResponse.AqlType.ENTRY));
        }
        // reset handled
        helperValues.forEach(h -> h.getValue().setHandled(false));
        final String compositionAql = createCompositionAql(typedEntries, compositionArchetype, narrowToTemplate);
        if (isValidAql(compositionAql)) {
            responses.add(new ToAqlResponse.AqlResponse(compositionAql, ToAqlResponse.AqlType.COMPOSITION));
        }
        return responses;
    }

    boolean possibleTypesAreAcceptableForAqlTranslation(final HelperValue helperValue) {
        final MappingHelper helper = helperValue.getHelper();
        if (helper.getPossibleRmTypes() == null
                || helper.getPossibleRmTypes().isEmpty()
                || FhirConnectConst.OPENEHR_TYPE_NONE.equals(helper.getHardcodedType())) {
            return false;
        }
        if (helper.getPossibleRmTypes().size() == 1 && helper.getPossibleRmTypes()
                .contains(FhirConnectConst.OPENEHR_TYPE_CLUSTER)) {
            return false;
        }
        return true;
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
     */
    String createEntryAql(final List<HelperValue> helperValues,
                          final String compositionArchetype) {
        final HelperValue first = helperValues.get(0);

        final String archetype = first.helper.getArchetype();
        final String entryType = getEntryType(archetype);
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
                                                                  compositionArchetype);

            if (pathWithoutArchetype != null) {
                final String str = stripPinpointedAqlPath(pathWithoutArchetype);
                conditions.append(str.startsWith("/") ? str : ("/"+str));
                conditions.append(openEhrAqlPopulator.getDataTypeAwareAqlSuffix(pathWithoutArchetype,
                                                                                StringUtils.isEmpty(manualOpenEhrValue)
                                                                                        ? hv.getValue().getValue()
                                                                                        : manualOpenEhrValue,
                                                                                helper.getPossibleRmTypes()));
            }
        }
        return String.format("SELECT h FROM EHR e CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}' AND h%s",
                             entryType, compositionArchetype, conditions.toString().startsWith("/") ? conditions : ("/"+conditions));
    }

    private String stripPinpointedAqlPath(final String path) {
        return path
                .replace("/defining_code/terminology_id", "")
                .replace("/defining_code/code_string", "");
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
                conditions.append(stripPinpointedAqlPath(pathWithoutArchetype));
                conditions.append(openEhrAqlPopulator.getDataTypeAwareAqlSuffix(pathWithoutArchetype,
                                                                                StringUtils.isEmpty(manualOpenEhrValue)
                                                                                        ? hv.getValue().getValue()
                                                                                        : manualOpenEhrValue,
                                                                                helper.getPossibleRmTypes()));
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
