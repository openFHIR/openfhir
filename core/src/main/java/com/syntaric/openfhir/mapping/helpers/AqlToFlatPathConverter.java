package com.syntaric.openfhir.mapping.helpers;

import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_INVALID_PATH_RM_TYPES;
import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_UNDERSCORABLES;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.terminology.OfCoding;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;

import java.util.*;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.springframework.stereotype.Component;

/**
 * Converts an AQL-style openEHR path (as stored on a MappingHelper) into the simplified flat path
 * used in flat JSON (EHRbase FLAT format), and detects the RM type of the leaf node.
 */
@Component
@RequiredArgsConstructor
public class AqlToFlatPathConverter {

    /**
     * Result of converting an AQL path to a flat path.
     *
     * @param flatPath the flat JSON path, e.g. {@code growth_chart/body_weight/any_event[n]/weight}
     * @param rmType   the detected RM type of the leaf node, or {@code null} if not resolved
     * @param valid    {@code false} when the path could not be fully resolved in the template
     */
    public record Result(String flatPath, String rmType, boolean valid, List<String> possibleTypes,
                         List<OfCoding> availableCodings) {

    }

    public record WalkSegmentsResult(String rmType, List<String> possibleRmTypes, List<OfCoding> availableCodings) {

    }

    private final OpenFhirStringUtils openFhirStringUtils;
    private final OpenFhirMapperUtils openFhirMapperUtils;

    /**
     * Converts the {@code fullOpenEhrPath} stored on a {@link MappingHelper} to a flat path,
     * also setting {@link MappingHelper#setFullOpenEhrFlatPath} and
     * {@link MappingHelper#setDetectedType} as a side effect.
     */
    public Result convert(final MappingHelper mappingHelper, final WebTemplate webTemplate) {
        final Result result = convert(mappingHelper.getFullOpenEhrPath(), mappingHelper.getHardcodedType(),
                webTemplate);
        final String flatPath = result.flatPath();
        mappingHelper.setFullOpenEhrFlatPath(flatPath);
//        mappingHelper.setDetectedType(result.rmType());
        mappingHelper.setFullOpenEhrFlatPathWithMatchingRegex(toMatchingRegex(flatPath));
        mappingHelper.setPossibleRmTypes(result.possibleTypes());
        mappingHelper.setAvailableCodings(result.availableCodings());

        final String flatPathPipeSuffix = openFhirMapperUtils.replaceAqlSuffixWithFlatSuffix(flatPath,
                result.rmType());
        mappingHelper.setFlatPathPipeSuffix(flatPathPipeSuffix);
        return result;
    }

    /**
     * Derives the regex-matching form of a flat path by stripping {@code [n]} occurrence markers
     * and then applying {@link OpenFhirStringUtils#addRegexPatternToSimplifiedFlatFormat}.
     */
    public String toMatchingRegex(final String flatPath) {
        if (StringUtils.isBlank(flatPath)) {
            return flatPath;
        }
        final String withoutN = flatPath.replace(RECURRING_SYNTAX, "");
        return openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(withoutN);
    }

    /**
     * Converts an AQL-style openEHR path to a simplified flat path.
     *
     * @param aqlPath     full AQL path, e.g.
     *                    {@code openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]}
     * @param forcedType  optional RM type hint from the FHIRConnect model (may be {@code null})
     * @param webTemplate the parsed OPT web template to walk
     * @return conversion result with flat path, detected type, and validity flag
     */
    public Result convert(final String aqlPath, final String forcedType, final WebTemplate webTemplate) {
        if (StringUtils.isBlank(aqlPath)) {
            return new Result(aqlPath, null, false, null, null);
        }

        // $composition paths: strip the $composition prefix and walk the remaining segments through the web template
        if (aqlPath.startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC)) {
            return convertCompositionPath(aqlPath, forcedType, webTemplate);
        }

        // The first segment is the archetype id (e.g. openEHR-EHR-OBSERVATION.body_weight.v2).
        // It is not in the web-template tree — the tree's direct children already represent archetypes.
        final int firstSlash = aqlPath.indexOf("/");
        final String archetypeId = firstSlash < 0 ? aqlPath : aqlPath.substring(0, firstSlash);
        final String[] segments = firstSlash < 0 ? new String[0] : splitPath(aqlPath.substring(firstSlash + 1));

        final WebTemplateNode tree = webTemplate.getTree();
        final Set<String> forcedTypes = openFhirStringUtils.getPossibleRmTypeValue(forcedType);
        final StringJoiner flat = new StringJoiner("/");

        // Find the composition-level node for this archetype (e.g. id="body_weight").
        // It contributes the first flat segment even though it is absent from the AQL segments.
        final WebTemplateNode archetypeRoot = tree.getChildren().stream()
                .filter(n -> n.getAqlPath(true).contains("[" + archetypeId + "]")
                        || n.getAqlPath(false).contains("[" + archetypeId + "]"))
                .findFirst().orElse(null);

        if (archetypeRoot == null) {
            // Archetype not found in this template — return as-is (invalid)
            return new Result(firstSlash < 0 ? aqlPath : tree.getId() + "/" + String.join("/", segments), null, false,
                    null, null);
        }

        flat.add(archetypeRoot.isMulti() ? flatId(archetypeRoot) + RECURRING_SYNTAX : flatId(archetypeRoot));

        // Flatten all descendants of the archetype root for O(1) per-segment lookup
        final List<WebTemplateNode> descendants = new ArrayList<>();
        collectAll(archetypeRoot.getChildren(), descendants);

        final WalkSegmentsResult result = walkSegments(descendants, segments, flat, forcedTypes);

        final String flatPath = removeStructuralSegments(tree.getId() + "/" + flat);

        // Valid when an rmType was resolved
        final boolean valid = !result.possibleRmTypes().isEmpty();

        return new Result(flatPath, valid ? result.rmType() : null, valid, result.possibleRmTypes(),
                result.availableCodings());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // $composition path handling
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Handles paths starting with {@code $composition/} by stripping the prefix and walking the
     * remaining AQL segments through the web template tree from the composition root.
     */
    private Result convertCompositionPath(final String aqlPath, final String forcedType,
                                          final WebTemplate webTemplate) {
        final String suffix = aqlPath.substring(FhirConnectConst.OPENEHR_COMPOSITION_FC.length());
        if (suffix.isEmpty()) {
            return new Result(webTemplate.getTree().getId(), null, true, null, null);
        }

//        final String[] segments = suffix.split("/");

        final String[] segments = splitPath(suffix);


        final WebTemplateNode tree = webTemplate.getTree();
        final Set<String> forcedTypes = openFhirStringUtils.getPossibleRmTypeValue(forcedType);
        final StringJoiner flat = new StringJoiner("/");

        // Collect all descendants of the composition root (includes context, other_context, etc.)
        final List<WebTemplateNode> descendants = new ArrayList<>();
        collectAll(tree.getChildren(), descendants);

        final WalkSegmentsResult walkResult = walkSegments(descendants, segments, flat, forcedTypes);

        final String flatPath = removeStructuralSegments(tree.getId() + (flat.length() > 0 ? "/" + flat : ""));
        final boolean valid = walkResult.possibleRmTypes() != null && !walkResult.possibleRmTypes().isEmpty();
        return new Result(flatPath, valid ? walkResult.rmType() : null, valid, walkResult.possibleRmTypes(),
                walkResult.availableCodings());
    }

    String[] splitPath(final String path) {

        final List<String> result = new ArrayList<>();

        if (path == null || path.isEmpty()) {
            return new String[]{};
        }

        final StringBuilder current = new StringBuilder();
        int bracketDepth = 0;

        for (int i = 0; i < path.length(); i++) {

            final char c = path.charAt(i);

            if (c == '[') {
                bracketDepth++;
                current.append(c);
            } else if (c == ']') {
                bracketDepth--;
                current.append(c);
            } else if (c == '/' && bracketDepth == 0) {

                if (!current.isEmpty()) {
                    result.add(current.toString());
                    current.setLength(0);
                }

            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            result.add(current.toString());
        }

        return result.toArray(new String[0]);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Core walk
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Iterates over the AQL {@code segments}, finding each one among {@code descendants} by matching
     * the node whose absolute AQL path ends with {@code "/" + segment}.  When multiple nodes match,
     * the one that is a descendant of the previous match is preferred.
     *
     * @return the resolved RM type of the leaf node, or {@code null} if unresolved
     */
    private WalkSegmentsResult walkSegments(final List<WebTemplateNode> descendants,
                                            final String[] segments,
                                            final StringJoiner flat,
                                            final Set<String> forcedTypes) {
        WebTemplateNode lastFound = null;
        String rmType = null;

        for (final String rawSegment : segments) {
            final String segment = rawSegment.replace("*", "/");

            if (StringUtils.isEmpty(rawSegment)) {
                continue;
            }

            if (segment.startsWith("_")) {
                // Pre-formatted flat token — pass through unchanged
                flat.add(segment);
                continue;
            }

            final WebTemplateNode found = findNode(descendants, segment, lastFound);
            if (found == null) {
                if ("terminology_id".equals(segment)) { // but what if it doesn't exist?!
                    flat.add(segment);
                    return new WalkSegmentsResult("CODE_PHRASE",
                                                  Collections.singletonList("CODE_PHRASE"),
                                                  null);
                }
                if ("coded_text_value".equals(segment)) { // but what if it doesn't exist?!
                    return new WalkSegmentsResult("DV_CODED_TEXT",
                                                  Collections.singletonList("DV_CODED_TEXT"),
                                                  null);
                }
                return new WalkSegmentsResult(rmType,
                        Collections.singletonList(rmType),
                        getPossibleCodings(lastFound));
            }

            lastFound = found;

            // Multi-occurrence nodes always produce a flat segment (e.g. any_event[n]).
            // Non-multi structural types (HISTORY, ITEM_TREE, etc.) are skipped here and
            // removed later by removeStructuralSegments; lastFound is still updated so the
            // next segment is scoped correctly.
            if (found.isMulti()) {
                flat.add(found.getId() + RECURRING_SYNTAX);
            } else if (!OPENEHR_INVALID_PATH_RM_TYPES.contains(found.getRmType())) {
                flat.add(flatId(found));
            }

            rmType = resolveType(forcedTypes, found, flat);
        }

        // After all segments, check children of the leaf for auto-appended value sub-paths and type refinement
        final List<String> possibleTypes = new ArrayList<>();
        final List<OfCoding> possibleCodings = new ArrayList<>();
        if (lastFound != null) {
//            rmType = resolveLeafType(lastFound.getChildren(), forcedTypes, flat, rmType);
            if (rmType != null &&
                    !FhirConnectConst.ELEMENT.equals(rmType)
                    && !FhirConnectConst.OPENEHR_TYPE_CLUSTER.equals(rmType)) {
                possibleTypes.add(rmType);
            }
            possibleTypes.addAll(getPossibleType(lastFound.getChildren(), rmType));
            possibleCodings.addAll(getPossibleCodings(lastFound));
        }

        return new WalkSegmentsResult(rmType,
                possibleTypes.stream().distinct().toList(),
                possibleCodings);
    }

    /**
     * Finds the single web-template node whose absolute AQL path ends with {@code "/" + segment}.
     * When multiple candidates exist, the one that descends from {@code lastFound} is preferred.
     */
    private static WebTemplateNode findNode(final List<WebTemplateNode> nodes,
                                            final String segment,
                                            final WebTemplateNode lastFound) {
        final String suffix = "/" + segment;
        final List<WebTemplateNode> candidates = nodes.stream()
                .filter(n -> n.getAqlPath(true).endsWith(suffix)
                        || n.getAqlPath(false).endsWith(suffix)
                        || n.getAqlPath(true).replace(" and name/value=", ", ").endsWith(suffix))
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        // Prefer the candidate that descends from the last matched node, favouring the
        // most direct (shallowest) descendant so that nodes nested inside sub-archetypes
        // (e.g. address.v1/items[at0006]) are not chosen over the direct child.
        if (lastFound != null) {
            final String parentAql = lastFound.getAqlPath(true);
            return candidates.stream()
                    .filter(n -> n.getAqlPath(true).startsWith(parentAql + "/"))
                    .min(java.util.Comparator.comparingInt(n -> n.getAqlPath(true).length()))
                    .orElse(candidates.get(0));
        }
        return candidates.get(0);
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Type resolution
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Once all path segments are consumed, inspect the leaf node's children to:
     * <ul>
     *   <li>auto-append specialised value sub-paths ({@code identifier_value}, {@code date_time_value},
     *       {@code quantity_value})</li>
     *   <li>refine the RM type from a {@code value} / {@code coded_text_value} / etc. child</li>
     * </ul>
     */
    private static String resolveLeafType(final List<WebTemplateNode> children,
                                          final Set<String> forcedTypes,
                                          final StringJoiner flat,
                                          final String currentRmType) {
        if (children == null || children.isEmpty()) {
            return currentRmType;
        }

        // todo: refactor this. We should just have all possible flatPaths registered like possibleRmTypes (or no leaf types and leaf types are added at extraction/population phase based on possible rmtypes)
        boolean identifierValue = appendIfPresent(children, "identifier_value", flat);
        if (!identifierValue) {
            boolean dateTimeValue = appendIfPresent(children, "date_time_value", flat);
            if (!dateTimeValue) {
                boolean quantityValue = appendIfPresent(children, "quantity_value", flat);
            }
        }

        if (forcedTypes == null || forcedTypes.isEmpty()) {
            return children.stream()
                    .filter(n -> "value".equals(n.getId())
                            || "identifier_value".equals(n.getId())
                            || "coded_text_value".equals(n.getId())
                            || "quantity_value".equals(n.getId())
                            || "date_time_value".equals(n.getId()))
                    .findAny()
                    .map(WebTemplateNode::getRmType)
                    .orElse(currentRmType);
        }
        return currentRmType;
    }

    private static List<String> getPossibleType(final List<WebTemplateNode> children,
                                                final String currentRmType) {
        if (children == null || children.isEmpty()) {
            return Collections.singletonList(currentRmType);
        }
        return children.stream()
                .filter(n -> "value".equals(n.getId()) || n.getId().endsWith("_value"))
                .filter(child -> !FhirConnectConst.ELEMENT.equals(child.getRmType())
                        && !FhirConnectConst.OPENEHR_TYPE_CLUSTER.equals(child.getRmType()))
                .distinct()
                .map(WebTemplateNode::getRmType).toList();
    }

    private static List<OfCoding> getPossibleCodings(final WebTemplateNode found) {
        if (found == null) {
            return null;
        }
        return found.getInputs().stream()
                .flatMap(input -> input.getList().stream()
                        .map(list -> new OfCoding(input.getTerminology(), list.getValue(), list.getLabel())))
                .toList();
    }

    /**
     * Resolves the RM type for a matched node, honouring any forced type hint and handling ELEMENT
     * nodes that expose a specialised value child (e.g. {@code coded_text_value}).
     */
    private static String resolveType(final Set<String> forcedTypes,
                                      final WebTemplateNode node,
                                      final StringJoiner flat) {
        if (forcedTypes == null || forcedTypes.isEmpty()) {
            return node.getRmType();
        }

        // Small leaf or CODE_PHRASE — trust the forced type directly
        if ((forcedTypes.size() == 1 && node.getChildren().size() <= 3)
                || (forcedTypes.size() == 1 && node.getChildren().size() <= 5
                && forcedTypes.contains(FhirConnectConst.CODE_PHRASE))) {
            return forcedTypes.iterator().next();
        }

        // ELEMENT: look for a value-carrying child; may need to append its id to the flat path
        if (FhirConnectConst.ELEMENT.equals(node.getRmType())) {
            final Set<String> childRmTypes = node.getChildren().stream()
                    .map(WebTemplateNode::getRmType)
                    .collect(Collectors.toSet());
            return node.getChildren().stream()
                    .filter(c -> c.getId().contains("value"))
                    .map(c -> {
                        if (!c.getId().equals("value")
                                && !FhirConnectConst.OPENEHR_CONSISTENT_SET.containsAll(childRmTypes)) {
                            flat.add(c.getId());
                        }
                        return c.getRmType();
                    })
                    .findAny()
                    .orElse(null);
        }

        return forcedTypes.stream()
                .filter(ft -> ft.equals(node.getRmType()))
                .findFirst()
                .orElse(forcedTypes.iterator().next());
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------------------------

    private static String flatId(final WebTemplateNode node) {
        final String id = node.getId();
        return OPENEHR_UNDERSCORABLES.contains(id) ? "_" + id : id;
    }

    private static void collectAll(final List<WebTemplateNode> nodes, final List<WebTemplateNode> out) {
        for (final WebTemplateNode n : nodes) {
            out.add(n);
            collectAll(n.getChildren(), out);
        }
    }

    private static boolean appendIfPresent(final List<WebTemplateNode> nodes,
                                           final String id,
                                           final StringJoiner flat) {
        Optional<WebTemplateNode> matched = nodes.stream().filter(n -> id.equals(n.getId())).findAny();
        matched.ifPresent(n -> flat.add(id));
        return matched.isPresent();
    }

    private static String removeStructuralSegments(final String path) {
        final List<String> kept = new ArrayList<>();
        for (final String part : path.split("/")) {
            if (!OPENEHR_INVALID_PATH_RM_TYPES.contains(part)) {
                kept.add(part);
            }
        }
        return String.join("/", kept);
    }
}
