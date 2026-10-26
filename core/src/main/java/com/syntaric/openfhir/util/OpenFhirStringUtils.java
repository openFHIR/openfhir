package com.syntaric.openfhir.util;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OpenFhirStringUtils {

    public static final String RESOLVE = "resolve()";
    public static final String WHERE = "where";
    public static final String RECURRING_SYNTAX = "[n]";
    public static final String RECURRING_SYNTAX_ESCAPED = "\\[n]";

    /**
     * Adds regex pattern to the simplified flat path so that we can match all entries in a flat json
     *
     * @param simplifiedFlat simplified path as given in the fhir connect model mapings
     * @return simplified path with regex pattern
     */
    public String addRegexPatternToSimplifiedFlatFormat(final String simplifiedFlat) {
        final String[] parts = simplifiedFlat.split("/");
        final boolean lastOneHasPipe = parts[parts.length - 1].contains("|");
        if (lastOneHasPipe) {
            final String[] partsWithoutLast = Arrays.copyOf(parts, parts.length - 1);
            final String[] lastPart = parts[parts.length - 1].split("\\|");
            return String.join("(:\\d+)?/", partsWithoutLast) + "(:\\d+)?/" + lastPart[0] + "(:\\d+)?\\|" + lastPart[1];
        } else {
            return String.join("(:\\d+)?/", parts) + "(:\\d+)?(\\|.*)?";
        }
    }

    /**
     * Replaces the last occurrence of charToReplace within the given string. When the string doesn't contain
     * charToReplace at all there is nothing to replace, so the string is returned unchanged — callers used to
     * rely on this never happening and turned a -1 index into a StringIndexOutOfBoundsException.
     */
    public String replaceLastIndexOf(final String string, final String charToReplace, final String replaceWith) {
        int start = string.lastIndexOf(charToReplace);
        if (start < 0) {
            return string;
        }
        return string.substring(0, start) +
                replaceWith +
                string.substring(start + charToReplace.length());
    }

    /**
     * Returnes last index from the openEHR path, i.e. when passing in a:1/b:1/c/d:3, the '3' will be returned
     *
     * @param path path where we're extracting the index from
     * @return index as Integer extracted from the given openEHR path
     */
    public Integer getLastIndex(final String path) {
        String RIGHT_MOST_INDEX = ":(\\d+)(?!.*:)";
        final String match = getByRegex(path, RIGHT_MOST_INDEX);
        if (match == null) {
            return -1;
        }
        return Integer.valueOf(match);
    }

    private static final String CAST_TYPE = "as\\(([^()]*)\\)";

    /**
     * Name of the HAPI model class (without the package) the first {@code as(...)} cast of the path casts to, i.e.
     * {@code StringType} for {@code value.as(string)}. The cast type is normalized first, see
     * {@link #normalizeCastType(String)}.
     *
     * @return the class name, or null when the path has no cast
     */
    public String getCastType(final String path) {
        final String castString = getByRegex(path, CAST_TYPE);
        return castString == null ? null : castTypeToClassName(normalizeCastType(castString));
    }

    /**
     * Every {@code as(...)} cast type of the path, as written in the path (not normalized).
     */
    public List<String> getRawCastTypes(final String path) {
        final List<String> castTypes = getByRegexAll(path, CAST_TYPE);
        return castTypes == null ? List.of() : castTypes;
    }

    /**
     * FHIR primitive types in their FHIR spelling (lowercase first letter), keyed by the lower-cased name so a cast
     * matches them however it is capitalised. The corresponding HAPI model classes are named after the type with a
     * capitalised first letter and a `Type` suffix, i.e. `dateTime` -> `DateTimeType`. A version that lacks one of
     * them (no `integer64` before R4B) simply has no class for it.
     * <p>
     * Complex types (`Quantity`, `CodeableConcept`, `Period`, ...) are deliberately not part of this map as their
     * HAPI model classes carry no such suffix and are already named exactly like the cast type.
     */
    private static final Map<String, String> FHIR_PRIMITIVE_TYPES = Stream.of(
                    "base64Binary", "boolean", "canonical", "code", "date", "dateTime", "decimal", "id", "instant",
                    "integer", "integer64", "markdown", "oid", "positiveInt", "string", "time", "unsignedInt", "uri",
                    "url", "uuid")
            .collect(Collectors.toUnmodifiableMap(type -> type.toLowerCase(Locale.ROOT), type -> type));

    /**
     * FHIRPath System types and the FHIR type a cast to them is read as. FHIR elements are never of a System type,
     * so a cast like `as(System.String)` selects nothing in a FHIRPath engine; it is taken to mean the FHIR type.
     */
    private static final Map<String, String> SYSTEM_TYPES = Map.of(
            "Boolean", "boolean", "String", "string", "Integer", "integer", "Long", "integer64", "Decimal", "decimal",
            "Date", "date", "DateTime", "dateTime", "Time", "time", "Quantity", "Quantity");

    /**
     * System types that more than one FHIR primitive is a specialisation of, with the FHIR types a cast to them
     * does NOT select once it is read as the FHIR type of {@link #SYSTEM_TYPES}.
     */
    private static final Map<String, String> AMBIGUOUS_SYSTEM_TYPES = Map.of(
            "String", "code, id, markdown, uri, url, canonical, oid, uuid, base64Binary",
            "Integer", "positiveInt, unsignedInt",
            "DateTime", "instant");

    private static final Set<String> WARNED_SYSTEM_TYPES = ConcurrentHashMap.newKeySet();

    private static final String FHIR_NAMESPACE = "FHIR.";
    private static final String SYSTEM_NAMESPACE = "System.";

    private static final Pattern FHIR_PATH_CAST = Pattern.compile("\\b(as|ofType)\\(\\s*([A-Za-z][\\w.]*)\\s*\\)");

    /**
     * Normalizes the type of a FHIRPath cast to the spelling FHIR uses: primitives start lowercase
     * ({@code string}, {@code dateTime}), complex types and resources uppercase ({@code Quantity}). Accepted are:
     * <ul>
     *     <li>primitives in any capitalisation: {@code string}, {@code String}, {@code datetime} -> {@code string},
     *     {@code string}, {@code dateTime}</li>
     *     <li>the legacy HAPI class spelling: {@code StringType} -> {@code string}</li>
     *     <li>the {@code FHIR.} namespace: {@code FHIR.string} -> {@code string}</li>
     *     <li>System types, read as the matching FHIR type: {@code System.String} -> {@code string}</li>
     *     <li>complex types, capitalised: {@code quantity} -> {@code Quantity}</li>
     * </ul>
     * A type that is none of these (an unknown System type) is returned as is.
     */
    public static String normalizeCastType(final String castType) {
        if (StringUtils.isBlank(castType)) {
            return castType;
        }
        String type = castType.trim();
        if (type.startsWith(SYSTEM_NAMESPACE)) {
            return systemTypeAsFhirType(type.substring(SYSTEM_NAMESPACE.length()), castType);
        }
        if (type.startsWith(FHIR_NAMESPACE)) {
            type = type.substring(FHIR_NAMESPACE.length());
        }
        final String primitive = FHIR_PRIMITIVE_TYPES.get(type.toLowerCase(Locale.ROOT));
        if (primitive != null) {
            return primitive;
        }
        if (type.endsWith("Type")) {
            final String legacyPrimitive = FHIR_PRIMITIVE_TYPES.get(
                    StringUtils.removeEnd(type, "Type").toLowerCase(Locale.ROOT));
            if (legacyPrimitive != null) {
                return legacyPrimitive;
            }
        }
        return StringUtils.capitalize(type);
    }

    private static String systemTypeAsFhirType(final String systemType, final String castType) {
        final String fhirType = SYSTEM_TYPES.get(systemType);
        if (fhirType == null) {
            return castType;
        }
        if (AMBIGUOUS_SYSTEM_TYPES.containsKey(systemType) && WARNED_SYSTEM_TYPES.add(systemType)) {
            log.warn("Cast to '{}' is read as a cast to FHIR '{}'; FHIR elements of type {} are not selected by it. "
                             + "Cast to the FHIR type instead.",
                     castType, fhirType, AMBIGUOUS_SYSTEM_TYPES.get(systemType));
        }
        return fhirType;
    }

    /**
     * Name of the HAPI model class (without the package) implementing the given normalized cast type: primitives get
     * a `Type` suffix (`dateTime` -> `DateTimeType`), complex types are named exactly like the cast type.
     */
    public static String castTypeToClassName(final String normalizedCastType) {
        if (normalizedCastType != null && FHIR_PRIMITIVE_TYPES.containsValue(normalizedCastType)) {
            return StringUtils.capitalize(normalizedCastType) + "Type";
        }
        return normalizedCastType;
    }

    /**
     * Normalizes the type of every {@code as(...)} and {@code ofType(...)} cast in a FHIRPath expression, see
     * {@link #normalizeCastType(String)}, so that every accepted spelling evaluates alike. HAPI's FHIRPath engine
     * rejects HAPI class names ({@code as(StringType)}) and lowercase complex types, matches {@code as(...)}
     * case-insensitively but {@code ofType(...)} case-sensitively, and selects nothing for a System type.
     */
    public static String normalizeFhirPathCasts(final String fhirPath) {
        if (fhirPath == null || (!fhirPath.contains("as(") && !fhirPath.contains("ofType("))) {
            return fhirPath;
        }
        final Matcher matcher = FHIR_PATH_CAST.matcher(fhirPath);
        final StringBuilder normalized = new StringBuilder();
        while (matcher.find()) {
            final String cast = matcher.group(1) + "(" + normalizeCastType(matcher.group(2)) + ")";
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(cast));
        }
        matcher.appendTail(normalized);
        return normalized.toString();
    }

    private String getByRegex(final String path,
                              final String regex) {
        final List<String> byRegexAll = getByRegexAll(path, regex);
        if (byRegexAll == null) {
            return null;
        }
        return byRegexAll.get(0);
    }

    private List<String> getByRegexAll(final String path,
                                       final String regex) {
        final Pattern compiledPattern = Pattern.compile(regex);
        final Matcher matcher = compiledPattern.matcher(path);

        final List<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches;
    }

    /**
     * fixes fhirPath casting, as BooleanType is not a valid FHIR path, but boolean is (see
     * {@link #normalizeFhirPathCasts(String)})
     *
     * @param originalFhirPath path as it exists up until now
     * @return fhir path with casting
     */
    public String fixFhirPathCasting(final String originalFhirPath) {
        final String replacedCasting = normalizeFhirPathCasts(originalFhirPath);
        // now check if resolve() was preceeded with a case to a specific Resource; if that has happened, it needs to be
        // removed because it's not handled properly by fhirPath evaluation engine
        final String[] splitPath = replacedCasting.split("\\.");
        final StringJoiner building = new StringJoiner(".");
        for (int i = 0; i < splitPath.length; i++) {
            final String firstPath = splitPath[i];
            if (splitPath.length - 1 == i) {
                building.add(firstPath);
                break;
            }
            final String secondPath = splitPath[i + 1];
            if (firstPath.startsWith("as(") && secondPath.equals(RESOLVE)) {
                i++;
                building.add(secondPath);
            } else {
                building.add(firstPath);
            }
        }
        return building.toString();
    }

    /**
     * Gets all entries from the flat path that match simplified openehr path with regex pattern
     *
     * @param withRegex           simplified openehr path with regex pattern
     * @param compositionFlatPath composition in a flat path format
     * @return a list of Strings that match the given flat path with regex pattern
     */
    public List<String> getAllEntriesThatMatch(final String withRegex, final JsonObject compositionFlatPath) {
        Pattern compiledPattern = Pattern.compile(withRegex);
        final List<String> match = new ArrayList<>();
        for (Map.Entry<String, JsonElement> flatEntry : compositionFlatPath.entrySet()) {
            final Matcher matcher = compiledPattern.matcher(flatEntry.getKey());

            final List<String> matches = new ArrayList<>();

            while (matcher.find() && !isNotSame(flatEntry.getKey(), matcher.group())) {
                matches.add(matcher.group());
            }
            if (matches.isEmpty()) {
                continue;
            }
            match.addAll(matches);
        }
        return match;
    }

    /**
     * Will return all entries from compositionFlatPath where key starts with path. However all up until the pipe
     * need to match
     */
    public List<String> getAllEntriesThatMatchIgnoringPipe(final String path, final JsonObject compositionFlatPath) {
        final List<String> match = new ArrayList<>();
        for (final Map.Entry<String, JsonElement> flatEntry : compositionFlatPath.entrySet()) {
//            if (flatEntry.getKey().split("\\|")[0].equals(path.split("\\|")[0])) {
            if (flatEntry.getKey().split("\\|")[0].startsWith(path.split("\\|")[0])) {
                match.add(flatEntry.getValue().getAsString());
            }
        }
        return match;
    }


    /**
     * If the only difference is a digit, for example
     * diagnose/diagnose:0/klinischer_status/klinischer_status2
     * matching
     * diagnose/diagnose:0/klinischer_status/klinischer_status
     * then we need to make sure it's actually not a match
     */
    private boolean isNotSame(final String lookingFor, final String found) {
        final String diff = lookingFor.replace(found, "");
        return StringUtils.isNotBlank(diff) && Character.isDigit(diff.charAt(0));
    }

    public String extractWhereCondition(final String path) {
        String start = "where(";  // We start after 'where('
        int startIndex = path.indexOf(start);

        if (startIndex == -1) {
            return null; // No match found
        }

        int openParenthesisCount = 1;  // Start counting after 'where('
        int endIndex = startIndex + start.length();  // Start looking from the character after 'where('

        // Traverse the string and count parentheses
        while (endIndex < path.length()) {
            char currentChar = path.charAt(endIndex);

            if (currentChar == '(') {
                openParenthesisCount++;
            } else if (currentChar == ')') {
                openParenthesisCount--;

                // If the count reaches 0, we've found the matching closing parenthesis
                if (openParenthesisCount == 0) {
                    break;
                }
            }
            endIndex++;
        }

        if (openParenthesisCount != 0) {
            return null; // Parentheses weren't balanced
        }

        // Return the matched string
        return path.substring(startIndex, endIndex + 1); // Include the closing parenthesis

    }

    public List<String> splitFhirPathTopLevel(final String path) {
        if (StringUtils.isBlank(path)) {
            return List.of();
        }
        final List<String> parts = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < path.length(); i++) {
            final char ch = path.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
            if (ch == '.' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    /**
     * i.e.
     * growth_chart/body_weight/any_event:2/weight|unit
     * and
     * growth_chart/body_weight/any_event:2/weight|magnitude
     * will be joined together, as they are a single object
     */
    public Map<String, List<String>> joinValuesThatAreOne(final List<String> matchingEntries) {
        final Map<String, List<String>> matchings = new HashMap<>();
        for (String matchingEntry : matchingEntries) {
            final String[] split = matchingEntry.split("\\|");
            final String root = split[0];
            if (split.length == 1) {
                final List<String> list = new ArrayList<>();
                list.add(root);
                matchings.put(root, list);
            } else {
                if (!matchings.containsKey(root)) {
                    matchings.put(root, new ArrayList<>());
                }
                matchings.get(root).add(matchingEntry);
            }
        }
        return matchings;
    }


    /**
     * [$snomed.1104341000000101]"
     */
    public Coding getStringFromCriteria(final String criteria) {
        if (criteria == null) {
            return null;
        }
        if (criteria.contains("[")) {
            // legacy behavior
            final String[] criterias = criteria.replace("[",
                            "") // todo: crazy stuff in the FHIR Connect spec...... criteria is a string array, $loinc, ...
                    .replace("]", "").split(",");
            // todo: should be an OR inbetween these separate criterias.. right now it just takes the first
            final String codingCode = criterias[0]
                    .replace("$loinc.", "")
                    .replace("$snomed.", "");
            final String system;
            if (criteria.contains("$loinc")) {
                system = "http://loinc.org";
            } else if (criteria.contains("$snomed")) {
                system = "http://snomed.info/sct";
            } else {
                system = criteria.replace("[",
                                "") // // todo: crazy stuff in the FHIR Connect spec...... criteria is a string array, $loinc, ...
                        .replace("]", "").split("\\.")[0];
            }
            return new Coding(system, codingCode, null);
        } else {
            return new Coding(null, criteria, null);
        }
    }

    /**
     * Replaces parts of the original string with parts from the replacement string, based on specific patterns.
     * <p>
     * The original and replacement strings are split by "/" and processed part by part. The following rules are
     * applied:
     * - If a replacement part contains a numeric suffix in the format "part:number", the corresponding part from the
     * replacement is used.
     * - If a replacement part contains a suffix in the format "part[n]", the original structure of the part is
     * retained.
     * - If no special pattern is found, the replacement part is used, unless it's significantly different from the
     * original part, in which case the original part is kept.
     * - The method returns the new string with appropriate replacements and maintains the "/" as the separator.
     *
     * @param original    the original string to be processed (parts separated by "/")
     * @param replacement the replacement string to be used (parts separated by "/")
     * @return a new string where parts from the original are replaced with parts from the replacement
     */
    public String replacePattern(String original, String replacement) {
        // Split the original and replacement strings into parts based on "/"
        String[] originalParts = original.split("/");
        String[] replacementParts = replacement.split("/");

        StringBuilder result = new StringBuilder();

        // Iterate through the parts and replace the parts from the original with the replacement, when needed
        for (int i = 0; i < originalParts.length; i++) {
            if (i < replacementParts.length && replacementParts[i].matches(".*:\\d+")) {
                // If replacement part has a numeric suffix, use it
                result.append(replacementParts[i]);
            } else if (i < replacementParts.length && replacementParts[i].matches(".*\\[\\d*]")) {
                // If the replacement part has a [n] suffix, use the original structure
                result.append(originalParts[i]);
            } else if (i < replacementParts.length) {
                // Use the original part
                final String orig = originalParts[i].contains(RECURRING_SYNTAX) ? replaceLastIndexOf(originalParts[i],
                        RECURRING_SYNTAX,
                        "")
                        : originalParts[i];
                final String repl = replacementParts[i].contains(":") ? replacementParts[i].replace(":", "")
                                                                        .replace(String.valueOf(getLastIndex(replacementParts[i])), "") : replacementParts[i];
                if (!orig.startsWith(repl)) { // means it's a completely different one, need to take original
                    result.append(originalParts[i]);
                } else {
                    result.append(replacementParts[i]);
                }
            } else {
                // If no matching replacement, use the original part
                result.append(originalParts[i]);
            }

            // Add the separator
            if (i < originalParts.length - 1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    public boolean childHasParentRecurring(final String child, final String parent) {
        final List<String> childSplit = Arrays.asList(child.split("/"));
        final List<String> parentSplit = Arrays.asList(parent.split("/"));

        for (int i = 0; i < childSplit.size(); i++) {
            String childPath = childSplit.get(i);
            if (i >= parentSplit.size()) {
                return true;
            }
            if (childPath.contains("|")) {
                childPath = childPath.split("\\|")[0];
            }
            final String parentPath = parentSplit.get(i);
            if (childPath.endsWith("[n]") && parentPath.contains(":")) {
                final String baseChildPath = childPath.replace("[n]", "");
                final String baseParentPath = parentPath.substring(0, parentPath.indexOf(":"));
                if (baseChildPath.equals(baseParentPath)) {
                    return true;
                }
            }
            if (!childPath.equals(parentPath)) {
                return false;
            }
        }
        return true;
    }
}
