package com.syntaric.openfhir.mapping.toaql;

import ca.uhn.fhir.model.api.annotation.SearchParamDefinition;
import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.FhirConnectConst;
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
            final List<ToAql.ToAqlModels> relevantModels = mapQueryToMappingHelper(queryParam.getName(), resourceType, modelsToMap);
            if (!queryParam.isHandled() && (relevantModels == null || relevantModels.isEmpty())) {
                toAqlResponse.addUnhandledParam(queryParam.getName(), ToAqlResponse.UnhandledParamType.ERROR,
                        "No Mapping found that would match this param name (fhirpath) and is not included in the AQL.");
            } else {
                for (final ToAql.ToAqlModels model : relevantModels) {
                    for (final MappingHelper helper : model.getMappingHelpers()) {
                        helperValues.add(new HelperValue(model.getContext(), helper, queryParam));
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
            final String compositionArchetype = forContext.get(0).getHelper().getArchetype();
            final List<ToAqlResponse.AqlResponse> aqls = createAqls(forContext, compositionArchetype, narrowToTemplate);
            toAqlResponse.addAqls(aqls);
        }

        if(toAqlResponse.getAqls() == null || toAqlResponse.getAqls().isEmpty()) {
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
            final String commaDelimetedArchetypes = mappingHelpers.stream().map(MappingHelper::getArchetype).distinct().collect(Collectors.joining(","));
            final String entryAql = "SELECT h FROM EHR e CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}'";
            response.addAql(new ToAqlResponse.AqlResponse(String.format(entryAql, getEntryType(archetype), commaDelimetedArchetypes), ToAqlResponse.AqlType.ENTRY));

            if (narrowToTemplate) {
                final String compositionAql = "SELECT c from EHR e CONTAINS COMPOSITION c [%s] CONTAINS %s [%s] WHERE e/ehr_id/value='{{ehrid}}'";
                response.addAql(new ToAqlResponse.AqlResponse(String.format(compositionAql, compositionArchetype, getEntryType(archetype), commaDelimetedArchetypes), ToAqlResponse.AqlType.COMPOSITION));
            } else {
                final String compositionAql = "SELECT c FROM EHR e CONTAINS COMPOSITION c CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}'";
                response.addAql(new ToAqlResponse.AqlResponse(String.format(compositionAql, getEntryType(archetype), commaDelimetedArchetypes), ToAqlResponse.AqlType.COMPOSITION));
            }
        }
        return response;
    }

    private List<ToAqlResponse.AqlResponse> createAqls(final List<HelperValue> helperValues,
                                                       final String compositionArchetype,
                                                       final boolean narrowToTemplate) {
        final List<ToAqlResponse.AqlResponse> responses = new ArrayList<>();
        final List<HelperValue> typedEntries = helperValues.stream()
                .filter(this::possibleTypesAreAcceptableForAqlTranslation)
                .toList();
        if (typedEntries.isEmpty()) {
            return responses;
        }
        final String entryAql = createEntryAql(typedEntries);
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
        if (helper.getPossibleRmTypes().size() == 1 && helper.getPossibleRmTypes().contains(FhirConnectConst.OPENEHR_TYPE_CLUSTER)) {
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
     * "SELECT h from EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.height.v2] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value=500"
     * Multiple params:
     * "SELECT h from EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.height.v2] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[...]/value=500 AND h/data[...]/value=200"
     */
    String createEntryAql(final List<HelperValue> helperValues) {
        final HelperValue first = helperValues.get(0);
        final String fullOpenEhrPath = first.helper.getFullOpenEhrPath();
        if (fullOpenEhrPath.startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC)) {
            return null; // has to be composition aql, not entry
        }
        final String archetype = first.helper.getArchetype();
        final String entryType = getEntryType(fullOpenEhrPath);
        final StringBuilder conditions = new StringBuilder();
        for (final HelperValue hv : helperValues) {
            if (hv.getValue().isHandled()) {
                continue;
            }
            if (!conditions.isEmpty()) {
                conditions.append(" AND h/");
            }
            hv.getValue().setHandled(true);
            conditions.append(getPathWithoutArchetype(hv.getHelper().getFullOpenEhrPath(), hv.getHelper().getArchetype()));
            conditions.append(openEhrAqlPopulator.getDataTypeAwareAqlSuffix(hv.getValue().getValue(), hv.getHelper().getPossibleRmTypes()));
        }
        return String.format("SELECT h FROM EHR e CONTAINS %s h [%s] WHERE e/ehr_id/value='{{ehrid}}' AND h/%s",
                entryType, archetype, conditions);
    }

    /**
     * Single param:
     * "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and c/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]/value=500"
     * Multiple params:
     * "SELECT c from EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}' and c/content[openEHR-EHR-OBSERVATION.height.v2]/data[...]/value=500 AND c/content[openEHR-EHR-OBSERVATION.height.v2]/data[...]/value=200"
     * or narrowed down to the template:
     * "SELECT c from EHR e CONTAINS COMPOSITION c [openEHR-EHR-COMPOSITION.growth_chart.v0] WHERE e/ehr_id/value='{{ehrid}}' and c/content[...]/...=500 AND c/content[...]/...=200"
     */
    String createCompositionAql(final List<HelperValue> helperValues,
                                final String compositionArchetype,
                                final boolean narrowToTemplate) {
        final StringBuilder conditions = new StringBuilder();
        for (final HelperValue hv : helperValues) {
            if (hv.getValue().isHandled()) {
                continue;
            }
            final boolean isOnComposition = hv.getHelper().getFullOpenEhrPath().startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC);
            final String archetype = hv.getHelper().getFullOpenEhrPath().split("/")[0];
            if (isOnComposition) {
                if (!conditions.isEmpty()) {
                    conditions.append(" AND c");
                } else {
                    conditions.append("c");
                }
            } else {
                if (!conditions.isEmpty()) {
                    conditions.append(" AND c/content[");
                    conditions.append(archetype).append("]/");
                } else {
                    conditions.append("c/content[");
                    conditions.append(archetype).append("]/");
                }
            }
            hv.getValue().setHandled(true);
            conditions.append(getPathWithoutArchetype(hv.getHelper().getFullOpenEhrPath(), archetype));
            conditions.append(openEhrAqlPopulator.getDataTypeAwareAqlSuffix(hv.getValue().getValue(), hv.getHelper().getPossibleRmTypes()));
        }
        if (narrowToTemplate) {
            return String.format("SELECT c from EHR e CONTAINS COMPOSITION c CONTAINS %s [%s] WHERE e/ehr_id/value='{{ehrid}}' and %s",
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
     * openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004] -> data[at0002]/events[at0003]/data[at0001]/items[at0004]
     */
    String getPathWithoutArchetype(final String fullOpenEhrPath, final String archetype) {
        return fullOpenEhrPath
                .replace(FhirConnectConst.OPENEHR_COMPOSITION_FC, "")
                .replace(String.format("%s/", archetype), "");
    }

    List<ToAql.ToAqlModels> mapQueryToMappingHelper(final String paramName,
                                                    final String resourceType,
                                                    final List<ToAql.ToAqlModels> modelsToMap) {

        final String fhirPathForQueryName = getFhirPathForQueryName(resourceType, paramName);
        if (fhirPathForQueryName == null) {
            log.warn("No FHIR path found for query param '{}'", paramName);
            return List.of();
        }

        final List<ToAql.ToAqlModels> result = new ArrayList<>();
        for (final ToAql.ToAqlModels model : modelsToMap) {
            final List<MappingHelper> matched = findByFhirPath(model.getMappingHelpers(), fhirPathForQueryName);
            if (!matched.isEmpty()) {
                result.add(ToAql.ToAqlModels.builder()
                        .context(model.getContext())
                        .modelMappers(model.getModelMappers())
                        .mappingHelpers(matched)
                        .build());
            }
        }
        return result;
    }

    List<MappingHelper> findByFhirPath(final List<MappingHelper> helpers, final String fhirPath) {
        final List<MappingHelper> matched = new ArrayList<>();
        if (helpers == null) {
            return matched;
        }
        final List<String> paths = Arrays.stream(fhirPath.split("\\|"))
                .map(String::trim)
                .toList();
        for (final MappingHelper helper : helpers) {
            if(helper.getFullFhirPath() == null) {
                continue;
            }
            boolean isArchetypeOnlyMapping = helper.getFullFhirPath().equals(helper.getGeneratingResourceType());
            if (!isArchetypeOnlyMapping
                    && (paths.stream().anyMatch(p -> p.contains(helper.getFullFhirPath()) || helper.getFullFhirPath().contains(p)))) {
                matched.add(helper);
            }
            matched.addAll(findByFhirPath(helper.getChildren(), fhirPath));
        }
        return matched;
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
            return Class.forName(Observation.class.getPackageName() + "." + resourceType); // which means this is bound to R4!
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

        HelperValue(final FhirConnectContextEntity context, final MappingHelper helper, final FhirQueryParam value) {
            this.context = context;
            this.helper = helper;
            this.value = value;
        }
    }
}
