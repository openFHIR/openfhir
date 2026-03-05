package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrConditionEvaluator {

    private OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public OpenEhrConditionEvaluator(final OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
    }

    /**
     * Returns true for flat JSON keys that are not a subpath of the root being narrowed on.
     * Such entries always pass through unchanged, regardless of the condition outcome.
     *
     * <p>The root is derived from the occurrence prefixes by stripping the occurrence index
     * (e.g. {@code ":0"}) — giving the common parent path (e.g. {@code "diagnose/diagnose"}).
     * A key is outside the subtree if it does not start with that root path.
     *
     * <p>Example: occurrencePrefixes = ["diagnose/diagnose:0", "diagnose/diagnose:1"]
     * → root = "diagnose/diagnose"
     * → "diagnose/diagnose:0/kodierte_diagnose|code" is inside the subtree → false
     * → "diagnose/context/start_time" is outside the subtree → true
     * → "kds_fall_einfach/institutionsaufenthalt/encoding" is outside the subtree → true
     *
     * @param key flat JSON key to test
     * @param occurrencePrefixes occurrence-level prefixes (with occurrence index, e.g. "diagnose/diagnose:0")
     */
    private static boolean isOutsideNarrowingSubtree(final String key, final List<String> occurrencePrefixes) {
        if (occurrencePrefixes.isEmpty()) {
            return true;
        }
        // Derive the root by stripping the ":N" occurrence index from the first prefix
        final String sample = occurrencePrefixes.get(0);
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile(":\\d+$").matcher(sample);
        final String root = m.find() ? sample.substring(0, m.start()) : sample;
        return !key.startsWith(root);
    }

    /**
     * Derives occurrence-level prefixes from the matched group prefixes by stripping the fixed
     * suffix contributed by the condition's targetRoot below the recurring node.
     *
     * <p>Example: targetRootFlatPath = "diagnose/diagnose[n]/klinischer_status",
     * groupPrefix = "diagnose/diagnose:0/klinischer_status"
     * → occurrence prefix = "diagnose/diagnose:0"
     *
     * <p>If no fixed suffix can be detected (the recurring node IS the group root), the group
     * prefix itself is returned unchanged.
     */
    private static List<String> toOccurrencePrefixes(final List<String> groupPrefixes,
                                                     final String targetRootFlatPath) {
        if (StringUtils.isBlank(targetRootFlatPath)) {
            return groupPrefixes;
        }
        // Find the portion after the last occurrence of a recurring segment (':' digit pattern).
        // We detect the suffix by finding where the occurrence index ends in a sample group prefix.
        // Use the first group prefix as a reference; the suffix is whatever comes after the first
        // segment that contains a colon-digit occurrence marker.
        if (groupPrefixes.isEmpty()) {
            return groupPrefixes;
        }
        final String sample = groupPrefixes.get(0);
        // The occurrence index segment looks like "diagnose:0" — find the end of ":digits"
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile(":\\d+").matcher(sample);
        if (!m.find()) {
            // No occurrence index found — group prefixes are already occurrence-level
            return groupPrefixes;
        }
        final int occurrenceEnd = m.end();
        // Everything after the occurrence index in the sample is the fixed sub-path suffix
        final String suffix = sample.substring(occurrenceEnd);
        if (suffix.isEmpty()) {
            return groupPrefixes;
        }
        return groupPrefixes.stream()
                .map(gp -> gp.endsWith(suffix) ? gp.substring(0, gp.length() - suffix.length()) : gp)
                .collect(Collectors.toList());
    }

    private JsonObject handleOneOfOperatorSplit(final Condition openEhrCondition,
                                                final List<String> extractedValueKeys,
                                                final JsonObject fullFlatPath) {
        if (extractedValueKeys.isEmpty()) {
            // no such flat path even exists — nothing to narrow by, return as-is
            return fullFlatPath;
        }
        // Determine which group prefixes satisfy the criteria.
        // Multiple targetAttributes are treated as OR — a group matches if ANY attribute satisfies the criteria.
        final Set<String> matchingPrefixes = new HashSet<>();
        for (final String groupPrefix : extractedValueKeys) {
            boolean groupMatches = false;
            for (final String targetAttribute : openEhrCondition.getTargetAttributesFlatPath()) {
                final boolean subPath = targetAttribute.startsWith("|");
                if (subPath && !groupPrefix.endsWith(targetAttribute)) {
                    continue;
                }
                final String keyToCheckFor = subPath ? groupPrefix : groupPrefix + "/" + targetAttribute;
                final JsonPrimitive extractedValueJson = fullFlatPath.getAsJsonPrimitive(keyToCheckFor);
                final String extractedValue = extractedValueJson == null ? "" : extractedValueJson.getAsString();

                if (StringUtils.isNotEmpty(extractedValue) && openEhrCondition.getCriterias()
                        .contains(extractedValue)) {
                    groupMatches = true;
                    break;
                }
            }
            if (groupMatches) {
                matchingPrefixes.add(groupPrefix);
            } else {
                log.info(
                        "Group {} did not match any targetAttribute criteria {}, therefore excluding it from mapping.",
                        groupPrefix, openEhrCondition.getCriterias());
            }
        }

        if (matchingPrefixes.isEmpty()) {
            log.info("No matching groups found for openEhrCondition {}. Returning empty JsonObject.", openEhrCondition);
            return new JsonObject();
        }

        // Build the result: keep entries that belong to a matching occurrence, plus any context entries.
        // matchingPrefixes are group-level (e.g. diagnose:0/klinischer_status); we need occurrence-level
        // prefixes for the matching groups so that sibling entries (e.g. diagnose:0/kodierte_diagnose)
        // of a matching occurrence are also included.
        final List<String> occurrencePrefixes = toOccurrencePrefixes(extractedValueKeys,
                                                                     openEhrCondition.getTargetRootFlatPath());
        final List<String> matchingOccurrencePrefixes = toOccurrencePrefixes(
                List.copyOf(matchingPrefixes), openEhrCondition.getTargetRootFlatPath());
        final JsonObject modifiedJsonObject = new JsonObject();
        for (final Map.Entry<String, com.google.gson.JsonElement> entry : fullFlatPath.entrySet()) {
            final String key = entry.getKey();
            final boolean belongsToMatchingOccurrence = matchingOccurrencePrefixes.stream().anyMatch(key::startsWith);
            final boolean isContext = isOutsideNarrowingSubtree(key, occurrencePrefixes);
            if (belongsToMatchingOccurrence || isContext) {
                modifiedJsonObject.add(key, entry.getValue());
            }
        }
        return modifiedJsonObject;
    }

    private JsonObject handleEmptyOperatorSplit(final Condition openEhrCondition,
                                                final List<String> extractedValueKeys,
                                                final JsonObject fullFlatPath) {
        if (extractedValueKeys.isEmpty()) {
            // no such flat path even exists — nothing to narrow by, return as-is
            return fullFlatPath;
        }
        // Determine which group prefixes have the targeted path absent (i.e. satisfy "empty")
        final Set<String> matchingPrefixes = new HashSet<>();
        for (final String groupPrefix : extractedValueKeys) {
            boolean anyAttributePresent = false;
            for (final String targetAttribute : openEhrCondition.getTargetAttributesFlatPath()) {
                final String openEhrKey = String.format("%s/%s", groupPrefix, targetAttribute);
                final List<String> matchingEntries = openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        openEhrKey, fullFlatPath);
                if (!matchingEntries.isEmpty()) {
                    anyAttributePresent = true;
                    break;
                }
            }
            if (!anyAttributePresent) {
                matchingPrefixes.add(groupPrefix);
            } else {
                log.info(
                        "Flat path {}/[attribute] didn't evaluate to empty, as per condition, therefore excluding {} from mapping.",
                        groupPrefix, groupPrefix);
            }
        }

        // Build the result: keep entries that belong to a matching occurrence, plus context entries
        final List<String> occurrencePrefixes = toOccurrencePrefixes(extractedValueKeys,
                                                                     openEhrCondition.getTargetRootFlatPath());
        final List<String> matchingOccurrencePrefixes = toOccurrencePrefixes(
                List.copyOf(matchingPrefixes), openEhrCondition.getTargetRootFlatPath());
        final JsonObject modifiedJsonObject = new JsonObject();
        if (matchingOccurrencePrefixes.isEmpty()) {
            return modifiedJsonObject;
        }
        for (final Map.Entry<String, com.google.gson.JsonElement> entry : fullFlatPath.entrySet()) {
            final String key = entry.getKey();
            final boolean belongsToMatchingOccurrence = matchingOccurrencePrefixes.stream().anyMatch(key::startsWith);
            final boolean isContext = isOutsideNarrowingSubtree(key, occurrencePrefixes);
            if (belongsToMatchingOccurrence || isContext) {
                modifiedJsonObject.add(key, entry.getValue());
            }
        }
        return modifiedJsonObject;
    }

    private JsonObject handleNotEmptyOperatorSplit(final Condition openEhrCondition,
                                                   final List<String> extractedValueKeys,
                                                   final JsonObject fullFlatPath) {
        if (extractedValueKeys.isEmpty()) {
            return fullFlatPath;
        }
        // Determine which group prefixes have the targeted path present (i.e. satisfy "not empty")
        final Set<String> matchingPrefixes = new HashSet<>();
        for (final String groupPrefix : extractedValueKeys) {
            boolean anyAttributePresent = false;
            for (final String targetAttribute : openEhrCondition.getTargetAttributesFlatPath()) {
                final String openEhrKey;
                if (StringUtils.isEmpty(targetAttribute)) {
                    openEhrKey = groupPrefix;
                } else {
                    openEhrKey = String.format("%s/%s", groupPrefix, targetAttribute);
                }
                final List<String> matchingEntries = openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(
                        openEhrKey, fullFlatPath);
                if (!matchingEntries.isEmpty()) {
                    anyAttributePresent = true;
                    break;
                }
            }
            if (anyAttributePresent) {
                matchingPrefixes.add(groupPrefix);
            } else {
                log.info(
                        "Flat path {}/[attribute] evaluated to empty, as per 'not empty' condition, therefore excluding {} from mapping.",
                        groupPrefix, groupPrefix);
            }
        }

        // Build the result: keep entries that belong to a matching occurrence, plus all other non-group entries
        final List<String> occurrencePrefixes = toOccurrencePrefixes(extractedValueKeys,
                                                                     openEhrCondition.getTargetRootFlatPath());
        final List<String> matchingOccurrencePrefixes = toOccurrencePrefixes(
                List.copyOf(matchingPrefixes), openEhrCondition.getTargetRootFlatPath());
        final JsonObject modifiedJsonObject = new JsonObject();
        if (matchingOccurrencePrefixes.isEmpty()) {
            return modifiedJsonObject;
        }
        for (final Map.Entry<String, com.google.gson.JsonElement> entry : fullFlatPath.entrySet()) {
            final String key = entry.getKey();
            final boolean belongsToMatchingOccurrence = matchingOccurrencePrefixes.stream().anyMatch(key::startsWith);
            final boolean isContext = isOutsideNarrowingSubtree(key, occurrencePrefixes);
            if (belongsToMatchingOccurrence || isContext) {
                modifiedJsonObject.add(key, entry.getValue());
            }
        }
        return modifiedJsonObject;
    }


    /**
     * If a mapping has openehrCondition, then the whole JsonObject representing flatPath Composition needs to be split
     * in a way so that iteration of the mapping only extracts from the relevant part of the JsonObject
     *
     * @return a split JsonObject if openEhrCondition is not null, otherwise the original fullFlatPath
     */
    public JsonObject splitByOpenEhrCondition(final JsonObject fullFlatPath,
                                              final Condition openEhrCondition) {
        if (openEhrCondition == null) {
            return fullFlatPath;
        }

        final List<String> narrowingCriteria = narrowingCriteria(openEhrCondition, fullFlatPath);

        switch (openEhrCondition.getOperator()) {
            case FhirConnectConst.CONDITION_OPERATOR_ONE_OF -> {
                return handleOneOfOperatorSplit(openEhrCondition, narrowingCriteria, fullFlatPath);
            }
            case FhirConnectConst.CONDITION_OPERATOR_EMPTY -> {
                return handleEmptyOperatorSplit(openEhrCondition, narrowingCriteria, fullFlatPath);
            }
            case FhirConnectConst.CONDITION_OPERATOR_NOT_EMPTY -> {
                return handleNotEmptyOperatorSplit(openEhrCondition, narrowingCriteria, fullFlatPath);
            }
        }
        return fullFlatPath;
    }

    public List<String> narrowingCriteria(final Condition openEhrCondition,
                                          final JsonObject fullFlatPath) {
        final String flatRoot = openEhrCondition.getTargetRootFlatPath();

        // Strip [n] recurring markers before building the regex so the pattern matches real indices (:0, :1 …)
        final String flatRootStripped = flatRoot.replace(OpenFhirStringUtils.RECURRING_SYNTAX, "");
        final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(flatRootStripped);
        return openFhirStringUtils.getAllEntriesThatMatch(withRegex, fullFlatPath).stream().distinct().collect(
                Collectors.toList());
    }

}
