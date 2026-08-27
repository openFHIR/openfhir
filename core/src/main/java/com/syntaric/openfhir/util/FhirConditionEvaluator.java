package com.syntaric.openfhir.util;

import ca.uhn.fhir.fhirpath.IFhirPath;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Evaluates FHIR Connect fhirConditions programmatically against in-memory FHIR elements.
 *
 * <p>Historically conditions were compiled into the FHIRPath string itself (a {@code .where(...)}
 * clause spliced into the mapping's path). This component replaces that string splicing: the plain
 * mapping path is evaluated as-is and the conditions are applied in Java, at the exact position the
 * where() clause used to occupy.
 *
 * <p>Only "one of", "not of" and "type" operators filter path evaluation; "empty" / "not empty"
 * are gated separately (see {@code ToOpenEhrMappingEngine#fhirEmptyNotEmptyPasses}) and are
 * ignored here. Multiple targetAttributes and multiple criterias are OR-implied within a single
 * condition; AND semantics require multiple conditions.
 */
@Component
@Slf4j
public class FhirConditionEvaluator {

    private final OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public FhirConditionEvaluator(final OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
    }

    /**
     * True for operators that filter which elements a fhir path evaluation yields ("one of",
     * "not of", "type"); false for presence gates ("empty", "not empty") that are evaluated
     * elsewhere.
     */
    public static boolean isPathFilteringCondition(final Condition condition) {
        if (condition == null) {
            return false;
        }
        final String operator = condition.getOperator();
        return FhirConnectConst.CONDITION_OPERATOR_ONE_OF.equals(operator)
                || FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(operator)
                || FhirConnectConst.CONDITION_OPERATOR_TYPE.equals(operator);
    }

    public static boolean hasPathFilteringConditions(final List<Condition> conditions) {
        return conditions != null && conditions.stream().anyMatch(FhirConditionEvaluator::isPathFilteringCondition);
    }

    /**
     * Evaluates the helper's plain fhir path on {@code toResolveOn} while applying the helper's
     * path-filtering fhirConditions at the position the condition's targetRoot dictates.
     *
     * <p>The split point is derived from the amended condition targetRoot (T) and the helper's
     * full fhir path (F): the plain path's leading segments up to the common F/T prefix are
     * evaluated first, each resulting element is filtered by the condition predicate (evaluated
     * via the remaining T segments plus each targetAttribute), and the rest of the plain path is
     * then evaluated on the surviving elements.
     *
     * @return the filtered evaluation results, or {@code null} when evaluation fails (already
     * logged) — the same contract as plain path evaluation
     */
    public List<? extends IBase> evaluateWithConditions(final MappingHelper helper,
                                                        final String plainFhirPath,
                                                        final IBase toResolveOn,
                                                        final IFhirPath fhirPath,
                                                        final Class<? extends IBase> baseClass) {
        try {
            final List<Condition> conditions = pathFilteringConditions(helper.getFhirConditions());
            final List<String> fullPathSegments = openFhirStringUtils.splitFhirPathTopLevel(helper.getFullFhirPath());
            int common = fullPathSegments.size();
            for (final Condition condition : conditions) {
                if (condition.getMappedPathEndAttributePrefix() != null) {
                    // legacy placement: the predicate applies to the mapped path's results
                    continue;
                }
                common = Math.min(common, commonPrefixLength(fullPathSegments,
                        openFhirStringUtils.splitFhirPathTopLevel(condition.getTargetRoot())));
            }
            final int restCount = fullPathSegments.size() - common;

            final List<String> plainSegments = openFhirStringUtils.splitFhirPathTopLevel(plainFhirPath);
            final int prefixCount = Math.max(0, plainSegments.size() - restCount);
            final String prefixPath = String.join(".", plainSegments.subList(0, prefixCount));
            final String restPath = String.join(".", plainSegments.subList(prefixCount, plainSegments.size()));

            final String preparedPrefix = StringUtils.isEmpty(prefixPath)
                    ? ""
                    : prepareForEvaluation(prefixPath, toResolveOn);
            final List<? extends IBase> candidates = StringUtils.isEmpty(preparedPrefix)
                    ? Collections.singletonList(toResolveOn)
                    : fhirPath.evaluate(toResolveOn, preparedPrefix, baseClass);

            final List<IBase> survivors = new ArrayList<>();
            for (final IBase candidate : candidates) {
                boolean passes = true;
                for (final Condition condition : conditions) {
                    final String targetRootRelative;
                    if (condition.getMappedPathEndAttributePrefix() != null) {
                        targetRootRelative = condition.getMappedPathEndAttributePrefix();
                    } else {
                        final List<String> targetRootSegments =
                                openFhirStringUtils.splitFhirPathTopLevel(condition.getTargetRoot());
                        targetRootRelative = String.join(".",
                                targetRootSegments.subList(common, targetRootSegments.size()));
                    }
                    if (!elementPassesCondition(condition, targetRootRelative, candidate, fhirPath, baseClass)) {
                        passes = false;
                        break;
                    }
                }
                if (passes) {
                    survivors.add(candidate);
                }
            }

            if (StringUtils.isEmpty(restPath)) {
                return survivors;
            }
            final List<IBase> results = new ArrayList<>();
            for (final IBase survivor : survivors) {
                final String preparedRest = prepareForEvaluation(restPath, survivor);
                if (StringUtils.isEmpty(preparedRest)) {
                    results.add(survivor);
                } else {
                    results.addAll(fhirPath.evaluate(survivor, preparedRest, baseClass));
                }
            }
            return results;
        } catch (final Exception e) {
            log.error("Error trying to evaluate path {} with fhirConditions", plainFhirPath);
            return null;
        }
    }

    /**
     * Checks the helper's path-filtering fhirConditions directly against the parent-root element
     * the helper maps to (a helper with an empty or {@code $fhirRoot} path). Every path-filtering
     * condition must pass; a condition passes when ANY element at its (relativized) targetRoot
     * satisfies the condition predicate.
     */
    public boolean parentRootPassesConditions(final MappingHelper helper,
                                              final IBase toResolveOn,
                                              final IFhirPath fhirPath,
                                              final Class<? extends IBase> baseClass) {
        try {
            for (final Condition condition : pathFilteringConditions(helper.getFhirConditions())) {
                if (!conditionPassesOnRoot(condition, helper.getFullFhirPath(), toResolveOn, fhirPath, baseClass)) {
                    return false;
                }
            }
            return true;
        } catch (final Exception e) {
            log.error("Error evaluating fhirConditions on parent root for path {}", helper.getFullFhirPath());
            return false;
        }
    }

    /**
     * Checks a single condition against a resource; the condition's targetRoot is relativized
     * against the resource's own type. Used for preprocessor conditions (STU3 starting-resource
     * filtering) and context resolution (profile matching).
     */
    public boolean resourcePassesCondition(final Condition condition,
                                           final IBase resource,
                                           final IFhirPath fhirPath,
                                           final Class<? extends IBase> baseClass) {
        try {
            return conditionPassesOnRoot(condition, null, resource, fhirPath, baseClass);
        } catch (final Exception e) {
            log.error("Error evaluating fhirCondition with targetRoot {} on resource {}",
                    condition.getTargetRoot(), resource.fhirType());
            return false;
        }
    }

    private boolean conditionPassesOnRoot(final Condition condition,
                                          final String fullFhirPath,
                                          final IBase root,
                                          final IFhirPath fhirPath,
                                          final Class<? extends IBase> baseClass) {
        if (!isPathFilteringCondition(condition)) {
            return true;
        }
        final List<String> targetRootSegments = openFhirStringUtils.splitFhirPathTopLevel(condition.getTargetRoot());
        final int skip;
        if (StringUtils.isNotEmpty(fullFhirPath)) {
            skip = commonPrefixLength(openFhirStringUtils.splitFhirPathTopLevel(fullFhirPath), targetRootSegments);
        } else {
            skip = !targetRootSegments.isEmpty() && root.fhirType().equalsIgnoreCase(targetRootSegments.get(0)) ? 1 : 0;
        }
        final String relativeRoot = String.join(".", targetRootSegments.subList(skip, targetRootSegments.size()));
        final List<? extends IBase> elements = StringUtils.isEmpty(relativeRoot)
                ? Collections.singletonList(root)
                : fhirPath.evaluate(root, relativeRoot, baseClass);
        return elements.stream()
                .anyMatch(element -> elementPassesCondition(condition, "", element, fhirPath, baseClass));
    }

    /**
     * The core condition predicate, evaluated on a single element. All targetAttributes are
     * OR-implied, as are all criterias: for "one of" the element passes when ANY attribute has
     * ANY value equal to ANY criteria code, "not of" is its negation, and "type" passes when any
     * attribute value's string contains any criteria code.
     */
    private boolean elementPassesCondition(final Condition condition,
                                           final String targetRootRelative,
                                           final IBase element,
                                           final IFhirPath fhirPath,
                                           final Class<? extends IBase> baseClass) {
        final List<String> criteriaCodes = condition.getCriterias().stream()
                .map(criteria -> openFhirStringUtils.getStringFromCriteria(criteria).getCode())
                .toList();
        final List<String> values = new ArrayList<>();
        for (final String targetAttribute : condition.getTargetAttributes()) {
            final String attributePath = StringUtils.isEmpty(targetRootRelative)
                    ? targetAttribute
                    : targetRootRelative + "." + targetAttribute;
            for (final IBase value : fhirPath.evaluate(element, attributePath, baseClass)) {
                values.add(valueAsString(value));
            }
        }
        if (FhirConnectConst.CONDITION_OPERATOR_TYPE.equals(condition.getOperator())) {
            return values.stream().anyMatch(value -> criteriaCodes.stream().anyMatch(value::contains));
        }
        final boolean match = values.stream().anyMatch(criteriaCodes::contains);
        if (FhirConnectConst.CONDITION_OPERATOR_NOT_OF.equals(condition.getOperator())) {
            return !match;
        }
        return match;
    }

    private String valueAsString(final IBase value) {
        return value instanceof IPrimitiveType<?> primitive ? primitive.getValueAsString() : value.toString();
    }

    private List<Condition> pathFilteringConditions(final List<Condition> conditions) {
        if (conditions == null) {
            return List.of();
        }
        return conditions.stream().filter(FhirConditionEvaluator::isPathFilteringCondition).toList();
    }

    /**
     * Mirrors the path preparation of plain path evaluation: the leading segment is stripped when
     * it matches the type of the element being evaluated on (unless that element is an
     * Extension), and Enumeration casts are dropped. A single-segment path matching the element
     * type is consumed entirely (empty result — the caller then uses the element itself), which
     * replicates how the legacy spliced path {@code coding.where(...)} lost its leading segment
     * to the type strip.
     */
    private String prepareForEvaluation(final String path, final IBase toResolveOn) {
        final String prepared;
        if (!toResolveOn.fhirType().equalsIgnoreCase("Extension")
                && toResolveOn.fhirType().equalsIgnoreCase(path.split("\\.")[0])) {
            final int firstDot = path.indexOf(".");
            prepared = firstDot == -1 ? "" : path.substring(firstDot + 1);
        } else {
            prepared = path;
        }
        return prepared.replace(".as(Enumeration)", "");
    }

    private static int commonPrefixLength(final List<String> first, final List<String> second) {
        int index = 0;
        while (index < first.size() && index < second.size() && first.get(index).equals(second.get(index))) {
            index++;
        }
        return index;
    }
}
