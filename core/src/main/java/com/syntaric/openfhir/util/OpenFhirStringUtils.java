package com.syntaric.openfhir.util;


import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Component;

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

    public String replaceLastIndexOf(final String string, final String charToReplace, final String replaceWith) {
        int start = string.lastIndexOf(charToReplace);
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

    public String getCastType(final String path) {
        String CAST_TYPE = "as\\(([^()]*)\\)";
        final String castString = getByRegex(path, CAST_TYPE);
        return adjustCastingToClassName(castString);
    }

    /**
     * FHIR primitive types, as they appear in a FHIR Path cast expression, i.e. `value.as(Integer)`. The corresponding
     * HAPI model classes are all named after the primitive type with a `Type` suffix, i.e. `IntegerType`.
     * <p>
     * Complex types (`Quantity`, `CodeableConcept`, `Period`, ...) are deliberately not part of this set as their HAPI
     * model classes carry no such suffix and are already named exactly like the cast type.
     */
    private static final Set<String> FHIR_PRIMITIVE_TYPES = Set.of(
            "Base64Binary", "Boolean", "Canonical", "Code", "Date", "DateTime", "Decimal", "Id", "Instant", "Integer",
            "Markdown", "Oid", "PositiveInt", "String", "Time", "UnsignedInt", "Uri", "Url", "Uuid");

    /**
     * Adjusts the type a FHIR Path is casting to, to the name of the HAPI model class implementing it. For primitive
     * types this means appending a `Type` suffix (`Integer` -> `IntegerType`), for complex types the cast type already
     * matches the class name and is returned unchanged (`Quantity` -> `Quantity`).
     *
     * @param castingTo type as extracted from the `as(...)` expression
     * @return name of the corresponding HAPI model class, without the package
     */
    private String adjustCastingToClassName(final String castingTo) {
        if (castingTo == null || castingTo.endsWith("Type")) {
            return castingTo;
        }
        return FHIR_PRIMITIVE_TYPES.contains(castingTo) ? castingTo + "Type" : castingTo;
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
     * fixes fhirPath casting, as BooleanType is not a valid FHIR path, but boolean is.. similar to StringType > String,
     * ..
     *
     * @param originalFhirPath path as it exists up until now
     * @return fhir path with casting
     */
    public String fixFhirPathCasting(final String originalFhirPath) {
        final String replacedCasting = replaceCasting(originalFhirPath);
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

    private String replaceCasting(final String originalFhirPath) {
        return originalFhirPath.replace("as(BooleanType)", "as(Boolean)")
                .replace("as(DateTimeType)", "as(DateTime)")
                .replace("as(TimeType)", "as(Time)")
                .replace("as(StringType)", "as(String)");
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

    public Set<String> getPossibleRmTypeValue(final String val) {
        if (val == null) {
            return null;
        }
        return switch (val) {
            case "QUANTITY" -> new HashSet<>(
                    Arrays.asList(FhirConnectConst.DV_QUANTITY, FhirConnectConst.DV_COUNT, FhirConnectConst.DV_ORDINAL,
                            FhirConnectConst.DV_PROPORTION));
            case "DATETIME" -> Collections.singleton(FhirConnectConst.DV_DATE_TIME);
            case "TIME" -> Collections.singleton(FhirConnectConst.DV_TIME);
            case "DATE" -> Collections.singleton(FhirConnectConst.DV_DATE);
            case "CODEABLECONCEPT" -> Collections.singleton(FhirConnectConst.DV_CODED_TEXT);
            case "CODING" -> Collections.singleton(FhirConnectConst.CODE_PHRASE);
            case "STRING" -> Collections.singleton(FhirConnectConst.DV_TEXT);
            case "BOOL" -> Collections.singleton(FhirConnectConst.DV_BOOL);
            case "IDENTIFIER" -> Collections.singleton(FhirConnectConst.DV_IDENTIFIER);
            case "MEDIA" -> Collections.singleton(FhirConnectConst.DV_MULTIMEDIA);
            case "PROPORTION" -> Collections.singleton(FhirConnectConst.DV_PROPORTION);
            default -> Collections.singleton(val);
        };
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
