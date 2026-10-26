package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.terminology.OfCoding;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_INVALID_PATH_RM_TYPES;
import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_UNDERSCORABLES;
import static com.syntaric.openfhir.util.OpenFhirStringUtils.RECURRING_SYNTAX;

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
     * @param flatPath          the flat JSON path, e.g. {@code growth_chart/body_weight/any_event[n]/weight}; when
     *                          {@code unresolvedSegment} is set, only the prefix that was resolved
     * @param rmType            the detected RM type of the leaf node, or {@code null} if not resolved
     * @param valid             {@code false} when no RM type was resolved for the path (also the case for
     *                          CLUSTER / slot / bare archetype paths) or when {@code unresolvedSegment} is set
     * @param unresolvedSegment the AQL segment addressing an archetype node (e.g. {@code items[at0004]}) that
     *                          does not exist in the template below the node matched before it; {@code null}
     *                          when every such segment was found. A trailing RM attribute ({@code value},
     *                          {@code defining_code/code_string} ...) has no template node and is not reported
     * @param node              the template node the path ends at, null when it ends at none
     */
    public record Result(String flatPath, String rmType, boolean valid, List<String> possibleTypes,
                         List<OfCoding> availableCodings, String unresolvedSegment, WebTemplateNode node) {

    }

    public record WalkSegmentsResult(String rmType, List<String> possibleRmTypes, List<OfCoding> availableCodings,
                                     String unresolvedSegment, WebTemplateNode node) {

    }

    private final OpenFhirStringUtils openFhirStringUtils;

    /**
     * Converts the {@code fullOpenEhrPath} stored on a {@link MappingHelper} to a flat path,
     * also setting {@link MappingHelper#setFullOpenEhrFlatPath}, {@link MappingHelper#setDetectedType} and
     * {@link MappingHelper#setOpenEhrPathUnresolved} as a side effect.
     */
    public Result convert(final MappingHelper mappingHelper, final WebTemplate webTemplate, final Boolean isManualCodedText) {
        final Result pinned = convertAttribute(mappingHelper, webTemplate);
        if (pinned != null) {
            return pinned;
        }
        // a pipe no data value type has (|function of a participation) is passed on as written, after the node
        final RmAttributePins.PathPipe pathPipe = RmAttributePins.pathPipe(mappingHelper.getFullOpenEhrPath());
        final Result result = convert(pathPipe == null ? mappingHelper.getFullOpenEhrPath() : pathPipe.path(),
                                      webTemplate);
        final String flatPath = result.flatPath();
        mappingHelper.setFullOpenEhrFlatPath(flatPath);
        mappingHelper.setOpenEhrNodeRmType(result.node() == null ? null : result.node().getRmType());
        mappingHelper.setOpenEhrPathUnresolved(result.unresolvedSegment() != null);

        mappingHelper.setFullOpenEhrFlatPathWithMatchingRegex(toMatchingRegex(flatPath));

        if(mappingHelper.getPossibleRmTypes() == null) {
            mappingHelper.setPossibleRmTypes(result.possibleTypes());

            final List<String> possibleRmTypes = mappingHelper.getPossibleRmTypes();
            if (shouldAddManualCodedText(possibleRmTypes, isManualCodedText)) {
                mappingHelper.setPossibleRmTypes(List.of(FhirConnectConst.DV_CODED_TEXT));
            }
        }


        mappingHelper.setAvailableCodings(result.availableCodings());

        mappingHelper.setFlatPathPipeSuffix(pathPipe == null ? "" : "|" + pathPipe.pipe());

        // if multiple rm types are here and children are really only manual mappings,
        // then rm types need to be propagated on
        if (mappingHelper.getChildren() != null) {
            boolean childrenAreManuals = mappingHelper.getChildren().stream().allMatch(ch -> ch.getManualOpenEhrValue() != null);
            if(childrenAreManuals) {
                mappingHelper.getChildren().forEach(ch -> ch.setPossibleRmTypes(mappingHelper.getPossibleRmTypes()));
            }
        }

        return result;
    }

    /**
     * An attribute of a data value a path ends in, resolved against the template.
     *
     * @param attribute         the attribute, null when the path addresses one the holder's types do not have
     *                          ({@code items[at0004]/value/magnitude} on a coded text); for a holder the template
     *                          does not have, whichever data value type's attribute it is spelled like
     * @param flatPath          the flat path of the node holding the attribute, with the type segment of a choice
     *                          element ({@code .../quantity_value}); the attribute's pipe goes on it
     * @param aqlPath           the attribute's strict AQL path ({@code .../items[at0130]/value/defining_code/code_string})
     * @param holder            the holder's own conversion
     * @param availableCodings  the codes the template allows the attribute's data value
     * @param unresolvedSegment what of the path does not exist: the holder, or the attribute when {@code attribute}
     *                          is null; null when it all exists
     */
    record AttributeResult(RmAttributePins.Attribute attribute, String flatPath, String aqlPath, Result holder,
                           List<OfCoding> availableCodings, String unresolvedSegment) {

        /** The flat key below {@code flatPath} the attribute is: its pipe, or nothing for the node itself. */
        String pipe() {
            return attribute.isElementItself() ? "" : "|" + attribute.flatAttribute();
        }
    }

    /**
     * The attribute of a data value {@code path} ends in, in any of its spellings (see {@link RmAttributePins}):
     * {@code items[at0004]/value/magnitude}, {@code items[at0130]/defining_code/code_string},
     * {@code null_flavour/value}, {@code items[at0130]/|code} ...
     *
     * <p>An attribute several of a choice element's types have ({@code value}, {@code magnitude} ...) is that of the
     * types {@code preferredType} (the mapping's {@code with.type}) names, when it names some of them; otherwise the
     * first type, in template order, that has it.
     *
     * @return null when the path ends in no attribute or (spelled any other way than strict AQL) none of the
     * holder's types has it: the path is then an ordinary one. A path through an ELEMENT's value whose types do not
     * have the attribute, or whose holder is not in the template, does not exist: see
     * {@link AttributeResult#unresolvedSegment()}.
     */
    AttributeResult resolveAttribute(final String path, final String preferredType, final WebTemplate webTemplate) {
        if (webTemplate == null) {
            return null;
        }
        for (final RmAttributePins.Pin pin : RmAttributePins.parse(path)) {
            final Result holder = convert(pin.holderPath(), webTemplate);
            if (holder.unresolvedSegment() != null) {
                // the path does not exist, but still names its attribute (for AQL, which does not need the template)
                final RmAttributePins.Attribute anyTypes = pin.pipe() == null
                        ? RmAttributePins.anyTypes(pin.rmPath())
                        : null;
                if (anyTypes == null) {
                    return null;
                }
                final boolean element = pin.throughValue() || pin.holderPath().endsWith("]");
                return new AttributeResult(anyTypes, holder.flatPath(),
                                           RmAttributePins.aqlPath(pin.holderPath(), element, anyTypes), holder,
                                           null, holder.unresolvedSegment());
            }
            final boolean holderIsElement = FhirConnectConst.ELEMENT.equals(holder.rmType());
            final List<String> types = holderIsElement ? holder.possibleTypes()
                    : holder.rmType() == null ? null : List.of(holder.rmType());
            if (types == null || types.isEmpty()) {
                continue;
            }
            final List<String> candidates = RmTypes.preferred(types, preferredType);
            final RmAttributePins.Attribute attribute = RmAttributePins.resolve(pin, holderIsElement, candidates);
            if (attribute == null) {
                if (pin.throughValue() && holderIsElement) {
                    return new AttributeResult(null, holder.flatPath(), null, holder, holder.availableCodings(),
                                               "value/" + pin.rmPath());
                }
                continue;
            }
            final String typeSegment = holderIsElement
                    ? RmAttributePins.typeSegment(types, attribute.rmType())
                    : null;
            final String flatPath = typeSegment == null ? holder.flatPath() : holder.flatPath() + "/" + typeSegment;
            return new AttributeResult(attribute, flatPath,
                                       RmAttributePins.aqlPath(pin.holderPath(), holderIsElement, attribute),
                                       holder, dataValueCodings(holder.node(), holderIsElement, attribute.rmType()),
                                       null);
        }
        return null;
    }

    /**
     * The codes the template allows a data value of {@code rmType}: on the holder, or on the ELEMENT's value of that
     * type, and the nodes below it ({@code defining_code}).
     */
    private static List<OfCoding> dataValueCodings(final WebTemplateNode holder, final boolean holderIsElement,
                                                   final String rmType) {
        if (holder == null) {
            return null;
        }
        final List<WebTemplateNode> values = holderIsElement
                ? holder.getChildren().stream()
                .filter(c -> rmType.equals(c.getRmType())
                        && ("value".equals(c.getId()) || c.getId().endsWith("_value")))
                .toList()
                : List.of(holder);
        final List<WebTemplateNode> nodes = new ArrayList<>(values);
        values.forEach(v -> collectAll(v.getChildren(), nodes));
        return nodes.stream()
                .flatMap(n -> getPossibleCodings(n).stream())
                .distinct()
                .toList();
    }

    /**
     * A mapping on an attribute of a data value: the helper maps the holder's flat path and that attribute's pipe
     * ({@code |magnitude}), or the node itself where the attribute is the whole value in the flat format
     * ({@code value} of a DV_TEXT).
     *
     * @return null when the path is an ordinary one, see {@link #resolveAttribute}
     */
    private Result convertAttribute(final MappingHelper mappingHelper, final WebTemplate webTemplate) {
        final AttributeResult resolved = resolveAttribute(mappingHelper.getFullOpenEhrPath(),
                                                          mappingHelper.getHardcodedType(), webTemplate);
        if (resolved == null) {
            return null;
        }
        final String flatPath = resolved.flatPath();
        mappingHelper.setFullOpenEhrFlatPath(flatPath);
        final WebTemplateNode holderNode = resolved.holder().node();
        mappingHelper.setOpenEhrNodeRmType(holderNode == null ? null : holderNode.getRmType());
        mappingHelper.setFullOpenEhrFlatPathWithMatchingRegex(toMatchingRegex(flatPath));
        mappingHelper.setAvailableCodings(resolved.availableCodings());
        mappingHelper.setOpenEhrPathUnresolved(resolved.unresolvedSegment() != null);
        final RmAttributePins.Attribute attribute = resolved.attribute();
        if (attribute == null) {
            return new Result(flatPath, null, false, List.of(), resolved.availableCodings(),
                              resolved.unresolvedSegment(), resolved.holder().node());
        }
        // a pipe attribute takes the value as is, whatever the node's type; the node itself has the type
        final String rmType = attribute.isElementItself() ? attribute.rmType() : FhirConnectConst.ELEMENT;
        if (mappingHelper.getPossibleRmTypes() == null) {
            mappingHelper.setPossibleRmTypes(List.of(rmType));
        }
        mappingHelper.setFlatPathPipeSuffix(resolved.pipe());
        mappingHelper.setPinnedAttribute(attribute);
        mappingHelper.setPinnedAqlPath(resolved.aqlPath());
        final boolean valid = resolved.unresolvedSegment() == null;
        return new Result(flatPath, valid ? rmType : null, valid, List.of(rmType), resolved.availableCodings(),
                          resolved.unresolvedSegment(), resolved.holder().node());
    }

    private boolean shouldAddManualCodedText(final List<String> possibleRmTypes,
                                             final Boolean isManualCodedText) {
        if (isManualCodedText == null || !isManualCodedText) {
            return false;
        }
        if (possibleRmTypes == null || possibleRmTypes.isEmpty()) {
            return true;
        }
        return !possibleRmTypes.contains(FhirConnectConst.DV_CODED_TEXT)
                && !possibleRmTypes.contains(FhirConnectConst.CODE_PHRASE)
                && !possibleRmTypes.contains(FhirConnectConst.ELEMENT)
                && !possibleRmTypes.contains(FhirConnectConst.OPENEHR_TYPE_CLUSTER);
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
     * @param webTemplate the parsed OPT web template to walk
     * @return conversion result with flat path, detected type, and validity flag
     */
    public Result convert(final String aqlPath, final WebTemplate webTemplate) {
        if (StringUtils.isBlank(aqlPath)) {
            return new Result(aqlPath, null, false, null, null, null, null);
        }

        // $composition paths: strip the $composition prefix and walk the remaining segments through the web template
        if (aqlPath.startsWith(FhirConnectConst.OPENEHR_COMPOSITION_FC)) {
            return convertCompositionPath(aqlPath, webTemplate);
        }

        // The first segment is the archetype id (e.g. openEHR-EHR-OBSERVATION.body_weight.v2).
        // It is not in the web-template tree — the tree's direct children already represent archetypes.
        final int firstSlash = aqlPath.indexOf("/");
        final String archetypeId = firstSlash < 0 ? aqlPath : aqlPath.substring(0, firstSlash);
        final String[] segments = firstSlash < 0 ? new String[0] : splitPath(aqlPath.substring(firstSlash + 1));

        final WebTemplateNode tree = webTemplate.getTree();
        final StringJoiner flat = new StringJoiner("/");

        // Find the composition-level node for this archetype (e.g. id="body_weight").
        // It contributes the first flat segment even though it is absent from the AQL segments.
        final WebTemplateNode archetypeRoot = tree.getChildren().stream()
                .filter(n -> n.getAqlPath(true).contains("[" + archetypeId + "]")
                        || n.getAqlPath(false).contains("[" + archetypeId + "]"))
                .findFirst().orElse(null);

        if (archetypeRoot == null) {
            // Archetype not found in this template — return as-is (invalid)
            final String flatPath = firstSlash < 0 ? aqlPath : tree.getId() + "/" + String.join("/", segments);
            return new Result(fixFlatPath(flatPath), null, false,
                    null, null, null, null);
        }

        flat.add(archetypeRoot.isMulti() ? flatId(archetypeRoot) + RECURRING_SYNTAX : flatId(archetypeRoot));

        // Flatten all descendants of the archetype root for O(1) per-segment lookup
        final List<WebTemplateNode> descendants = new ArrayList<>();
        collectAll(archetypeRoot.getChildren(), descendants);

        final WalkSegmentsResult result = walkSegments(descendants, segments, flat);

        final String flatPath = removeStructuralSegments(tree.getId() + "/" + flat);
        final String amendedFlatPath = fixFlatPath(flatPath);
        // Valid when every archetype node of the path exists in the template and an rmType was resolved
        final boolean valid = result.unresolvedSegment() == null && !result.possibleRmTypes().isEmpty();

        return new Result(amendedFlatPath, valid ? result.rmType() : null, valid, result.possibleRmTypes(),
                result.availableCodings(), result.unresolvedSegment(), result.node());
    }

    private String fixFlatPath(final String flatPath) {
        if (flatPath != null && flatPath.contains("other_participations")) {
            return flatPath.replace("other_participations", "_other_participation");
        }
        return flatPath;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // $composition path handling
    // -----------------------------------------------------------------------------------------------------------------

    /**
     * Handles paths starting with {@code $composition/} by stripping the prefix and walking the
     * remaining AQL segments through the web template tree from the composition root.
     */
    private Result convertCompositionPath(final String aqlPath, final WebTemplate webTemplate) {
        final String suffix = aqlPath.substring(FhirConnectConst.OPENEHR_COMPOSITION_FC.length());
        if (suffix.isEmpty()) {
            return new Result(webTemplate.getTree().getId(), null, true, null, null, null, webTemplate.getTree());
        }

//        final String[] segments = suffix.split("/");

        final String[] segments = splitPath(suffix);


        final WebTemplateNode tree = webTemplate.getTree();
        final StringJoiner flat = new StringJoiner("/");

        // Collect all descendants of the composition root (includes context, other_context, etc.)
        final List<WebTemplateNode> descendants = new ArrayList<>();
        collectAll(tree.getChildren(), descendants);

        final WalkSegmentsResult walkResult = walkSegments(descendants, segments, flat);

        final String flatPath = removeStructuralSegments(tree.getId() + (flat.length() > 0 ? "/" + flat : ""));
        final String amendedFlatPath = fixFlatPath(flatPath);
        final boolean valid = walkResult.unresolvedSegment() == null
                && walkResult.possibleRmTypes() != null && !walkResult.possibleRmTypes().isEmpty();
        return new Result(amendedFlatPath, valid ? walkResult.rmType() : null, valid, walkResult.possibleRmTypes(),
                walkResult.availableCodings(), walkResult.unresolvedSegment(), walkResult.node());
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
     * the node whose absolute AQL path ends with {@code "/" + segment}.  After the first match, only
     * descendants of the previous match qualify.
     *
     * @return the resolved RM type of the leaf node ({@code null} if unresolved), and the segment that
     * addresses an archetype node missing from the template, if any
     */
    private WalkSegmentsResult walkSegments(final List<WebTemplateNode> descendants,
                                            final String[] segments,
                                            final StringJoiner flat) {
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
                if (addressesArchetypeNode(segment)) {
                    // The segment addresses an archetype node (items[at0004], items[openEHR-EHR-CLUSTER.x.v1])
                    // that is not in the template below the node matched so far: the path does not exist.
                    return new WalkSegmentsResult(null, Collections.emptyList(), null, segment, null);
                }
                // An RM attribute tail has no template node: one of a data value is resolved before the walk (see
                // resolveAttribute), any other (a participation's function ...) stays on the node it belongs to
                return new WalkSegmentsResult(rmType,
                        Collections.singletonList(rmType),
                        getPossibleCodings(lastFound), null, null);
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

            rmType = found.getRmType();
        }

        // After all segments, check children of the leaf for auto-appended value sub-paths and type refinement
        final List<String> possibleTypes = new ArrayList<>();
        final List<OfCoding> possibleCodings = new ArrayList<>();
        if (lastFound != null) {
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
                possibleCodings, null, lastFound);
    }

    /**
     * Finds the single web-template node whose absolute AQL path ends with {@code "/" + segment}.
     * Once a node has been matched ({@code lastFound}), a segment that addresses an archetype node
     * (it carries a node predicate, e.g. {@code items[at0004]}) only matches a descendant of it: a
     * same-named node elsewhere in the template is not the node the path addresses. An RM attribute
     * segment ({@code defining_code}, {@code value} ...) is often not modelled below its parent in the
     * web template, so it still falls back to a same-named node elsewhere, which carries its RM type.
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
        // Prefer the candidate that descends from the last matched node, favouring the
        // most direct (shallowest) descendant so that nodes nested inside sub-archetypes
        // (e.g. address.v1/items[at0006]) are not chosen over the direct child.
        if (lastFound != null) {
            final String parentAql = lastFound.getAqlPath(true);
            final WebTemplateNode descendant = candidates.stream()
                    .filter(n -> n.getAqlPath(true).startsWith(parentAql + "/"))
                    .min(java.util.Comparator.comparingInt(n -> n.getAqlPath(true).length()))
                    .orElse(null);
            if (descendant != null || addressesArchetypeNode(segment)) {
                return descendant;
            }
        }
        return candidates.get(0);
    }

    private static boolean addressesArchetypeNode(final String segment) {
        return segment.contains("[");
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Type resolution
    // -----------------------------------------------------------------------------------------------------------------

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
