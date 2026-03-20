package com.syntaric.openfhir.mapping.tofhir;

import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.composition.ContentItem;
import com.nedap.archie.rm.composition.Section;
import com.nedap.archie.rm.datavalues.DvText;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.ehrbase.openehr.sdk.webtemplate.webtemplateskeletonbuilder.WebTemplateSkeletonBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ContentItemCompositionBuilder {

    public Composition buildComposition(final List<ContentItem> contentItemList,
                                        final WebTemplate webTemplate) {
        final Composition composition = WebTemplateSkeletonBuilder.build(webTemplate, false);
        final WebTemplateNode treeRoot = webTemplate.getTree();

        // Cache of already-created sections keyed by aqlPath of the container WebTemplateNode,
        // so the same section instance is reused when multiple ContentItems belong to it.
        final Map<String, Section> sectionByAqlPath = new HashMap<>();

        for (final ContentItem contentItem : contentItemList) {
            final String archetypeNodeId = contentItem.getArchetypeNodeId();

            final List<WebTemplateNode> path = findPathToArchetype(treeRoot.getChildren(), archetypeNodeId);

            if (path == null || path.isEmpty()) {
                // Archetype not found in webtemplate tree — add directly to composition as fallback
                composition.addContent(contentItem);
                continue;
            }

            if (path.size() == 1) {
                // ContentItem sits directly under the composition
                composition.addContent(contentItem);
            } else {
                // ContentItem is nested under one or more Section containers
                insertIntoComposition(composition, contentItem, path, sectionByAqlPath);
            }
        }

        return composition;
    }

    /**
     * Recursively searches for the path of WebTemplateNodes from {@code nodes} down to the node
     * whose aqlPath contains the given archetypeId. Returns the full path including the matching
     * node itself, or {@code null} if not found.
     */
    private List<WebTemplateNode> findPathToArchetype(final List<WebTemplateNode> nodes,
                                                      final String archetypeNodeId) {
        for (final WebTemplateNode node : nodes) {
            if (nodeMatchesArchetype(node, archetypeNodeId)) {
                final List<WebTemplateNode> path = new ArrayList<>();
                path.add(node);
                return path;
            }
            final List<WebTemplateNode> childPath = findPathToArchetype(node.getChildren(), archetypeNodeId);
            if (childPath != null) {
                final List<WebTemplateNode> path = new ArrayList<>();
                path.add(node);
                path.addAll(childPath);
                return path;
            }
        }
        return null;
    }

    private boolean nodeMatchesArchetype(final WebTemplateNode node, final String archetypeNodeId) {
        final String aqlPath = node.getAqlPath(true);
        return aqlPath.contains("[" + archetypeNodeId + "]");
    }

    /**
     * Inserts a ContentItem into the composition by following the given path.
     * Intermediate Section containers are created or reused via {@code sectionByAqlPath}.
     * The path has size >= 2: path[0..n-2] are container nodes, path[n-1] is the target archetype.
     */
    private void insertIntoComposition(final Composition composition,
                                       final ContentItem contentItem,
                                       final List<WebTemplateNode> path,
                                       final Map<String, Section> sectionByAqlPath) {
        Section currentSection = null;

        for (int i = 0; i < path.size() - 1; i++) {
            final WebTemplateNode containerNode = path.get(i);
            final String containerAqlPath = containerNode.getAqlPath(true);

            Section section = sectionByAqlPath.get(containerAqlPath);
            if (section == null) {
                section = buildSection(containerNode);
                sectionByAqlPath.put(containerAqlPath, section);
                if (currentSection == null) {
                    composition.addContent(section);
                } else {
                    currentSection.addItem(section);
                }
            }
            currentSection = section;
        }

        if (currentSection != null) {
            currentSection.addItem(contentItem);
        } else {
            composition.addContent(contentItem);
        }
    }

    private Section buildSection(final WebTemplateNode node) {
        final Section section = new Section();
        section.setArchetypeNodeId(node.getNodeId());
        section.setName(new DvText(node.getName()));
        return section;
    }
}
