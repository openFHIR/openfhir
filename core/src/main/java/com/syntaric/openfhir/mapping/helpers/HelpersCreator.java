package com.syntaric.openfhir.mapping.helpers;

import static com.syntaric.openfhir.fc.FhirConnectConst.FHIR_RESOURCE_FC;
import static com.syntaric.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;
import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_ARCHETYPE_FC;
import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_COMPOSITION_FC;
import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_ROOT_FC;
import static com.syntaric.openfhir.fc.FhirConnectConst.REFERENCE;
import static com.syntaric.openfhir.fc.FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR;

import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.OpenFhirFhirConnectModelMapper;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.FhirConnectReference;
import com.syntaric.openfhir.fc.schema.model.FollowedBy;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import com.syntaric.openfhir.fc.schema.model.Preprocessor;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.syntaric.openfhir.util.OpenFhirStringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HelpersCreator {

    final private OpenFhirMappingContext openFhirTemplateRepo;
    final private AqlToFlatPathConverter aqlToFlatPathConverter;

    @Autowired
    public HelpersCreator(final OpenFhirMappingContext openFhirTemplateRepo,
                          final AqlToFlatPathConverter aqlToFlatPathConverter) {
        this.openFhirTemplateRepo = openFhirTemplateRepo;
        this.aqlToFlatPathConverter = aqlToFlatPathConverter;
    }


    public Map<String, List<MappingHelper>> constructHelpers(final String templateId,
                                                             final String startingArchetype,
                                                             final List<String> archetypes,
                                                             final WebTemplate webTemplate) {
        final List<OpenFhirFhirConnectModelMapper> startingMappers = openFhirTemplateRepo.getMapperForArchetype(
                templateId,
                startingArchetype);
        final Map<String, List<MappingHelper>> allHelpersPerArchetype = new HashMap<>();

        if (startingMappers == null) {
            // means they're all on the same level, no starting one
            for (final String archetype : archetypes) {
                final List<OpenFhirFhirConnectModelMapper> mappers = openFhirTemplateRepo.getMapperForArchetype(
                        templateId,
                        archetype);
                final List<MappingHelper> mappingHelpers = constructHelpers(templateId, mappers, webTemplate);
                allHelpersPerArchetype.put(archetype, mappingHelpers);
            }
        } else {
            final List<MappingHelper> mappingHelpers = constructHelpers(templateId, startingMappers, webTemplate);
            allHelpersPerArchetype.put(startingArchetype, mappingHelpers);
        }
        return allHelpersPerArchetype;
    }

    public List<MappingHelper> constructHelpers(final String templateId,
                                                final List<OpenFhirFhirConnectModelMapper> mappers,
                                                final WebTemplate webTemplate) {
        final List<MappingHelper> allHelpers = new ArrayList<>();
        for (final OpenFhirFhirConnectModelMapper theMapper : mappers) {
            final List<MappingHelper> helpers = createHelpers(theMapper.getOriginalModel(),
                    theMapper.getFhirConfig()
                            .getResource(),
                    theMapper.getMappings(),
                    templateId,
                    webTemplate);
            allHelpers.addAll(helpers);
        }
        return allHelpers;
    }

    List<MappingHelper> createHelpers(final FhirConnectModel fhirConnectModel,
                                      final String fhirDataType,
                                      final List<Mapping> mappings,
                                      final String templateId,
                                      final WebTemplate webTemplate) {
        final List<MappingHelper> result = new ArrayList<>();
        createHelpers(fhirConnectModel, fhirDataType, fhirDataType, mappings, null, result, templateId, null, null, null,
                false, webTemplate, fhirConnectModel.getTerminology());
        return result;
    }

    public void createHelpers(final FhirConnectModel fhirConnectModel,
                              final String fhirDataType,
                              final String referenceDataType,
                              final List<Mapping> mappings,
                              final MappingHelper parentHelper,
                              final List<MappingHelper> creatingHelpers,
                              final String templateId,
                              final String fullSlotPath,
                              final String fullFhirSlotPath,
                              final String slotArchetype,
                              final boolean isFollowedBy,
                              final WebTemplate webTemplate,
                              final Terminology parentTerminology) {
        if (mappings == null) {
            log.error("No mappings in this mapping file id: {}, metadata.name: {}", fhirConnectModel.getId(),
                    fhirConnectModel.getMetadata().getName());
            return;
        }

        final String hierarchySplitFlatPath = getHierarchySplitFlatPath(fhirConnectModel, webTemplate, fullSlotPath,
                parentHelper);

        for (final Mapping mapping : mappings) {
            final MappingHelper mappingHelper = new MappingHelper();
            mappingHelper.setModelMetadataName(fhirConnectModel.getMetadata().getName());
            mappingHelper.setArchetype(fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype());
            mappingHelper.setMappingName(mapping.getName());
            mappingHelper.setGeneratingResourceType(fhirDataType);
            mappingHelper.setEnteredFromSlotArchetypeLink(slotArchetype != null);

            mappingHelper.setOriginalFhirPath(mapping.getWith().getFhir());
            mappingHelper.setOriginalOpenEhrPath(mapping.getWith().getOpenehr());

            mappingHelper.setProgrammedMapping(mapping.getMappingCode());

            mappingHelper.setOpenEhrHierarchySplitFlatPath(hierarchySplitFlatPath);
            mappingHelper.setPreprocessorFhirConditions(handlePreprocessorFhirConditions(fhirConnectModel,
                    mapping,
                    fhirDataType,
                    parentHelper,
                    fullFhirSlotPath));

            mappingHelper.setFollowedBy(isFollowedBy);

            // terminology
            final Terminology relevantTerminology = getRelevantTerminology(mapping, fhirConnectModel);
            mappingHelper.setTerminology(relevantTerminology == null ? parentTerminology : relevantTerminology);

            // Set FHIR paths
            setFhirPaths(mappingHelper, mapping, fhirDataType, referenceDataType, parentHelper, fullFhirSlotPath);

            // Set OpenEHR paths
            setOpenEhrPaths(mappingHelper, mapping.getWith().getOpenehr(),
                    fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype(), parentHelper,
                    fullSlotPath);

            // Find flat paths
            if (webTemplate != null) {
                aqlToFlatPathConverter.convert(mappingHelper, webTemplate, mapping.getManualCodedText());
                mappingHelper.setPreprocessorOpenEhrCondition(
                        handlePreprocessorOpenEhrCondition(fhirConnectModel,
                                webTemplate,
                                fullSlotPath));
            }

            // Set conditions with amended paths
            setConditions(mappingHelper, mapping, fhirDataType,
                    fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype(), parentHelper, fullSlotPath,
                    fullFhirSlotPath, webTemplate);

            mappingHelper.setHardcodedType(mapping.getWith().getType());

            mappingHelper.setUnidirectional(resolveUnidirectional(mapping, fhirConnectModel));

            // manual values
            if (mapping.getWith().getValue() != null) {
                if (UNIDIRECTIONAL_TOOPENEHR.equalsIgnoreCase(mapping.getUnidirectional())) {
                    mappingHelper.setManualOpenEhrValue(mapping.getWith().getValue());
                } else {
                    mappingHelper.setManualFhirValue(mapping.getWith().getValue());
                }
            }

            // Add to parent or top-level collection
            if (parentHelper != null) {
                parentHelper.getChildren().add(mappingHelper);
            } else {
                creatingHelpers.add(mappingHelper);
            }

            // Process nested mappings recursively
            final FollowedBy followedBy = mapping.getFollowedBy();
            final FhirConnectReference reference = mapping.getReference();
            if (reference != null) {
                mappingHelper.setResolveResourceType(reference.getResourceType());
                createHelpers(fhirConnectModel,
                        fhirDataType,
                        reference.getResourceType(),
                        reference.getMappings(),
                        mappingHelper,
                        null,
                        templateId,
                        fullSlotPath,
                        fullFhirSlotPath,
                        slotArchetype,
                        false,
                        webTemplate,
                        relevantTerminology);
            }
            if (followedBy != null) {
                createHelpers(fhirConnectModel,
                        fhirDataType,
                        fhirDataType,
                        followedBy.getMappings(),
                        mappingHelper,
                        null,
                        templateId,
                        fullSlotPath,
                        fullFhirSlotPath,
                        slotArchetype,
                        true,
                        webTemplate,
                        relevantTerminology);
            }
            if (mapping.getSlotArchetype() != null) {
                if (mapping.getSlotArchetype().equals(slotArchetype)) {
                    log.warn("Breaking possible infinite recursion with mapping: {}", mapping.getName());
                    continue;
                }
                final List<OpenFhirFhirConnectModelMapper> nextModelMappers = openFhirTemplateRepo.getMapperForArchetype(
                        templateId, mapping.getSlotArchetype());
                if (nextModelMappers == null) {
                    log.error("No mapping model file found for slot archetype: {}", mapping.getSlotArchetype());
                    continue;
                }
                mappingHelper.setHasSlot(true);
                for (final OpenFhirFhirConnectModelMapper nextModelMapper : nextModelMappers) {
                    createHelpers(nextModelMapper.getOriginalModel(),
                            nextModelMapper.getFhirConfig().getResource(),
                            nextModelMapper.getFhirConfig().getResource(),
                            nextModelMapper.getMappings(),
                            mappingHelper,
                            creatingHelpers,
                            templateId,
                            mappingHelper.getFullOpenEhrPath(),
                            mappingHelper.getFullFhirPath(),
                            mapping.getSlotArchetype(),
                            false,
                            webTemplate,
                            relevantTerminology);
                }
            }
        }
    }

    /**
     * A mapping file can declare unidirectional in its header (spec), in which case every mapping within
     * the file is treated as unidirectional. A unidirectional on the mapping itself takes precedence,
     * so a file-level declaration acts as the default for mappings that don't declare one.
     * https://github.com/openFHIR/openfhir/issues/98
     */
    private String resolveUnidirectional(final Mapping mapping, final FhirConnectModel fhirConnectModel) {
        if (StringUtils.isNotBlank(mapping.getUnidirectional())) {
            return mapping.getUnidirectional();
        }
        if (fhirConnectModel.getSpec() == null) {
            return null;
        }
        return fhirConnectModel.getSpec().getUnidirectional();
    }

    /**
     * Returns specific terminology if it exists (because it takes precedence over the parent one)
     * or null if none exist
     */
    private Terminology getRelevantTerminology(final Mapping specificMapping,
                                               final FhirConnectModel model) {
        final Terminology specificTerminology = specificMapping.getTerminology();
        final Terminology modelTerminology = model.getTerminology();
        return specificTerminology != null ? specificTerminology : modelTerminology;
    }

    private String getHierarchySplitFlatPath(final FhirConnectModel fhirConnectModel,
                                             final WebTemplate webTemplate,
                                             final String fullSlotPath,
                                             final MappingHelper parentHelper) {
        final Preprocessor preprocessor = fhirConnectModel.getPreprocessor();
        if (preprocessor == null || preprocessor.getHierarchy() == null) {
            return null;
        }
        final String openEhrPath = preprocessor.getHierarchy().getWith().getOpenehr();

        final String fallback = amendOpenEhrPath(openEhrPath, openEhrPath, parentHelper);
        final String resolvedOpenEhrPath = resolveFullOpenEhrPath(openEhrPath,
                fhirConnectModel.getSpec().getOpenEhrConfig()
                        .getArchetype(),
                fallback, parentHelper, fullSlotPath);

        final AqlToFlatPathConverter.Result result = aqlToFlatPathConverter.convert(resolvedOpenEhrPath, null,
                webTemplate);
        // An unresolved path is only a problem when it also lost its repeating marker: the split path has then
        // collapsed to a non-repeating node, and repeating entries under it silently share an index. A path that
        // kept its [n] resolved well enough (e.g. a hierarchy that is just $archetype), so it stays quiet.
        if (!result.valid() && result.flatPath() != null
                && !result.flatPath().contains(OpenFhirStringUtils.RECURRING_SYNTAX)) {
            log.warn(
                    "Hierarchy path '{}' of model mapper '{}' could not be fully resolved against the template; "
                            + "resolved to '{}'. Check that the model mapper matches the uploaded operational template.",
                    resolvedOpenEhrPath,
                    fhirConnectModel.getMetadata() == null ? null : fhirConnectModel.getMetadata().getName(),
                    result.flatPath());
        }
        return result.flatPath();
    }

    private Condition handlePreprocessorOpenEhrCondition(final FhirConnectModel fhirConnectModel,
                                                         final WebTemplate webTemplate,
                                                         final String fullSlotPath) {
        final Preprocessor preprocessor = fhirConnectModel.getPreprocessor();
        if (preprocessor == null || preprocessor.getOpenehrCondition() == null) {
            return null;
        }
        return amendCondition(preprocessor.getOpenehrCondition(),
                null,
                fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype(),
                null,
                fullSlotPath,
                false,
                null,
                webTemplate);
    }

    private List<Condition> handlePreprocessorFhirConditions(final FhirConnectModel fhirConnectModel,
                                                             final Mapping mapping,
                                                             final String coreResource,
                                                             final MappingHelper parentHelper,
                                                             final String fullFhirSlotPath) {
        final Preprocessor preprocessor = fhirConnectModel.getPreprocessor();
        if (preprocessor == null) {
            return null;
        }
        if (preprocessor.getFhirCondition() != null) {
            return List.of(amendCondition(preprocessor.getFhirCondition(),
                    coreResource,
                    null,
                    parentHelper,
                    null,
                    true,
                    fullFhirSlotPath,
                    null));
        } else if (preprocessor.getFhirConditions() != null) {
            return preprocessor.getFhirConditions().stream()
                    .map(condition -> amendCondition(condition,
                            coreResource,
                            null,
                            parentHelper,
                            null,
                            true,
                            fullFhirSlotPath,
                            null))
                    .collect(Collectors.toList());
        } else {
            return null;
        }
    }

    private void setFhirPaths(final MappingHelper mappingHelper,
                              final Mapping mapping,
                              final String coreResource,
                              final String referenceResource,
                              final MappingHelper parentHelper,
                              final String fullFhirSlotPath) {
        final String fhirPath = mapping.getWith().getFhir();
        if (StringUtils.isEmpty(fhirPath)) {
            return;
        }

        final String resolveAndCastSuffix = mapping.getReference() == null ? "" : ".as(Reference).resolve()";

        final String relativeFhirPath = getRelativeFhirPath(coreResource, fhirPath, parentHelper, fullFhirSlotPath);
        mappingHelper.setFhir(relativeFhirPath + resolveAndCastSuffix);
        mappingHelper.setUseParentRoot(FHIR_ROOT_FC.equals(fhirPath));

        final String absoluteFhirPath = getAbsoluteFhirPath(fhirPath,
                relativeFhirPath,
                coreResource,
                parentHelper,
                fullFhirSlotPath);
        mappingHelper.setFullFhirPath(absoluteFhirPath + resolveAndCastSuffix);
    }

    private void setOpenEhrPaths(final MappingHelper mappingHelper,
                                 final String openEhrPath,
                                 final String coreArchetype,
                                 final MappingHelper parentHelper,
                                 final String fullSlotPath) {
        // openEhr (local/relative path): $archetype → coreArchetype, $openehrRoot → parent's openEhr
        final String relativeOpenEhrPath;
        if (OPENEHR_ROOT_FC.equals(openEhrPath)) {
            relativeOpenEhrPath = parentHelper != null ? parentHelper.getOpenEhr() : openEhrPath;
        } else if (REFERENCE.equals(openEhrPath)) {
            if (parentHelper == null && StringUtils.isEmpty(fullSlotPath)) {
                // it means $reference really is on the first very level
                relativeOpenEhrPath = String.format("$composition/content[%s]",
                        coreArchetype);
            } else {
                relativeOpenEhrPath = amendOpenEhrPath(coreArchetype, OPENEHR_ARCHETYPE_FC, parentHelper);
            }
        } else {
            relativeOpenEhrPath = amendOpenEhrPath(coreArchetype, openEhrPath, parentHelper);
        }
        mappingHelper.setOpenEhr(relativeOpenEhrPath);


        mappingHelper.setFullOpenEhrPath(
                resolveFullOpenEhrPath(openEhrPath, coreArchetype, relativeOpenEhrPath, parentHelper,
                        fullSlotPath));
    }

    private void setConditions(final MappingHelper mappingHelper,
                               final Mapping mapping,
                               final String coreResource,
                               final String coreArchetype,
                               final MappingHelper parentHelper,
                               final String fullSlotPath,
                               final String fullFhirSlotPath,
                               final WebTemplate webTemplate) {
        // Amend FHIR condition
        if (mapping.getFhirCondition() != null) {

            final Condition amendedFhirCondition = amendCondition(mapping.getFhirCondition(),
                    coreResource, coreArchetype,
                    parentHelper, fullSlotPath, true,
                    fullFhirSlotPath, webTemplate);
            amendedFhirCondition.setMappedPathEndAttributePrefix(
                    mappedPathEndAttributePrefix(mapping.getFhirCondition(), mappingHelper));
            mappingHelper.setFhirConditions(List.of(amendedFhirCondition));
        }

        // Amend OpenEHR condition
        if (mapping.getOpenehrCondition() != null) {
            final Condition amendedOpenEhrCondition = amendCondition(mapping.getOpenehrCondition(),
                    coreResource, coreArchetype,
                    parentHelper, fullSlotPath, false,
                    null, webTemplate);
            mappingHelper.setOpenEhrConditions(List.of(amendedOpenEhrCondition));
        }
    }

    /**
     * Replicates the placement dispatch of the deprecated condition-in-fhirPath string splicing
     * ({@code OpenFhirStringUtils.getFhirPathWithConditions}): a condition whose raw targetRoot
     * did not prefix the raw mapped path had its where() clause appended to the END of the mapped
     * path — its attributes were therefore evaluated on the mapped path's results, not on
     * elements at the condition's targetRoot. When the raw targetRoot started with the resource
     * type, the attributes were additionally prefixed with the targetRoot relativized against the
     * raw path (which for unrelated roots produces a path that never resolves — a degeneracy that
     * effectively disabled such conditions and is deliberately preserved).
     *
     * @return {@code null} when the condition anchors at its (amended) targetRoot; otherwise the
     * attribute prefix to apply on the mapped path's results ("" for none)
     */
    private String mappedPathEndAttributePrefix(final Condition rawCondition,
                                                final MappingHelper mappingHelper) {
        final String resource = mappingHelper.getGeneratingResourceType() == null
                ? ""
                : mappingHelper.getGeneratingResourceType().replace(FhirConnectConst.FHIR_BACKBONE_ELEMENT, "");
        final String rawPath = mappingHelper.getOriginalFhirPath() == null
                ? ""
                : mappingHelper.getOriginalFhirPath()
                .replace(FHIR_RESOURCE_FC, resource)
                .replace(FhirConnectConst.FHIR_BACKBONE_ELEMENT, resource);
        if (rawPath.isEmpty() || rawPath.equals(FHIR_ROOT_FC)) {
            // empty paths anchor the condition at its targetRoot; $fhirRoot paths at the parent root
            return null;
        }
        final String rawTargetRoot = rawCondition.getTargetRoot()
                .replace(FHIR_RESOURCE_FC, resource)
                .replace(FHIR_ROOT_FC, "");
        if (rawPath.startsWith(rawTargetRoot)) {
            // targetRoot is a prefix of the mapped path — anchored at the targetRoot itself
            return null;
        }
        if (rawTargetRoot.startsWith(resource)) {
            // legacy "resource-anchored" splice: attributes prefixed with the targetRoot
            // relativized against the raw path
            if (rawTargetRoot.startsWith(rawPath + ".")) {
                return rawTargetRoot.substring(rawPath.length() + 1);
            }
            return rawTargetRoot;
        }
        return "";
    }

    /**
     * Attributes of the RM data value types (DV_*) that are represented as a pipe attribute in the flat
     * format, keyed by the RM attribute name and mapped to the flat name where the two differ.
     *
     * <p>None of these have a node of their own in an operational template — a DV_IDENTIFIER's
     * {@code type} or a DV_QUANTITY's {@code magnitude} is a leaf of the data value, not a child node —
     * so the AQL converter cannot resolve them and a condition addressing one has to be rewritten here.
     *
     * <p>The exceptions, deliberately absent, are the attributes that <em>are</em> real nodes and
     * therefore resolve normally: the element's own {@code value}, {@code defining_code} (a
     * DV_CODED_TEXT's CODE_PHRASE node, whose {@code defining_code/code_string} spelling is rewritten
     * to {@code |code} further down) and the {@code lower} / {@code upper} bounds of a DV_INTERVAL.
     */
    private static final Map<String, String> LEAF_RM_ATTRIBUTES = Map.ofEntries(
            // DV_IDENTIFIER
            Map.entry("id", "id"),
            Map.entry("type", "type"),
            Map.entry("issuer", "issuer"),
            Map.entry("assigner", "assigner"),
            // DV_TEXT / DV_CODED_TEXT
            Map.entry("formatting", "formatting"),
            Map.entry("language", "language"),
            Map.entry("encoding", "encoding"),
            Map.entry("hyperlink", "hyperlink"),
            // CODE_PHRASE — the flat format names these |code and |terminology
            Map.entry("code_string", "code"),
            Map.entry("terminology_id", "terminology"),
            Map.entry("preferred_term", "preferred_term"),
            // DV_QUANTITY / DV_COUNT / DV_PROPORTION and the other quantified types.
            // "units" is |unit in the flat format.
            Map.entry("magnitude", "magnitude"),
            Map.entry("units", "unit"),
            Map.entry("precision", "precision"),
            Map.entry("numerator", "numerator"),
            Map.entry("denominator", "denominator"),
            Map.entry("magnitude_status", "magnitude_status"),
            Map.entry("accuracy", "accuracy"),
            Map.entry("accuracy_is_percent", "accuracy_is_percent"),
            // DV_ORDINAL / DV_SCALE
            Map.entry("symbol", "symbol"),
            Map.entry("ordinal", "ordinal"),
            // DV_DURATION / DV_PARSABLE / DV_URI / DV_MULTIMEDIA / DV_ENCAPSULATED
            Map.entry("formalism", "formalism"),
            Map.entry("charset", "charset"),
            Map.entry("media_type", "mediatype"),
            Map.entry("size", "size"),
            Map.entry("alternate_text", "alternate_text"),
            Map.entry("uri", "uri"));

    /**
     * Maps a condition's {@code targetAttribute} onto the flat-format pipe attribute of an RM data
     * value, or returns {@code null} when it is an ordinary path that has to go through the AQL
     * converter.
     *
     * <p>Conditions address openEHR with RM paths, so a DV_IDENTIFIER part is written {@code type} (or
     * {@code value/type}, spelling out the ELEMENT's value attribute) rather than {@code |type}, the
     * same way a DV_CODED_TEXT is narrowed on {@code defining_code/code_string}. Those parts have no
     * node in the operational template, so the converter silently drops the segment and returns the
     * parent's flat path — which would leave the evaluator with a full path where it expects a pipe
     * attribute. The flat pipe syntax is accepted as-is too, for mappings that already use it.
     */
    private String toLeafAttributeFlatPath(final String targetAttribute) {
        if (targetAttribute.startsWith("|")) {
            return targetAttribute;
        }
        // "value/type" addresses the RM attribute through the ELEMENT's value; both spellings are common
        final String withoutValuePrefix = targetAttribute.startsWith("value/")
                ? targetAttribute.substring("value/".length())
                : targetAttribute;
        if (withoutValuePrefix.contains("/")) {
            // Multi-segment paths still go through the converter — notably a DV_CODED_TEXT's
            // "defining_code/code_string", which resolves against the template and is rewritten to
            // |code further down.
            return null;
        }
        // Ordinary attributes — including a bare "value", which is the ELEMENT's own value node —
        // still resolve against the template.
        final String flatName = LEAF_RM_ATTRIBUTES.get(withoutValuePrefix);
        return flatName == null ? null : "|" + flatName;
    }

    Condition amendCondition(final Condition originalCondition,
                             final String coreResource,
                             final String coreArchetype,
                             final MappingHelper parentHelper,
                             final String fullSlotPath,
                             final boolean isFhirCondition,
                             final String fullFhirSlotPath,
                             final WebTemplate webTemplate) {
        final Condition amendedCondition = originalCondition.copy();

        final String amendedTargetRoot;
        if (isFhirCondition) {
            final String condTargetRoot = amendedCondition.getTargetRoot();
            final String relativeFhirPath = getRelativeFhirPath(coreResource, condTargetRoot, parentHelper,
                    fullFhirSlotPath);
            amendedTargetRoot = getAbsoluteFhirPath(condTargetRoot, relativeFhirPath,
                    coreResource, parentHelper, fullFhirSlotPath);
        } else {
            final String targetRoot = amendedCondition.getTargetRoot();
            amendedTargetRoot = resolveFullOpenEhrPath(targetRoot, coreArchetype, targetRoot, parentHelper,
                    fullSlotPath);

            // Populate flat path alternatives for openEHR conditions
            if (webTemplate != null && StringUtils.isNotBlank(amendedTargetRoot)) {
                final AqlToFlatPathConverter.Result rootResult =
                        aqlToFlatPathConverter.convert(amendedTargetRoot, null, webTemplate);
                amendedCondition.setTargetRootFlatPath(rootResult.flatPath());

                // may legitimately be null on an attribute-less condition; FhirConditionEvaluator
                // treats that as a no-op condition, so amending must tolerate it too
                final List<String> targetAttributes = amendedCondition.getTargetAttributes() == null
                        ? Collections.<String>emptyList() : amendedCondition.getTargetAttributes();
                for (final String targetAttribute : targetAttributes) {
                    if (!StringUtils.isNotBlank(targetAttribute)) {
                        continue;
                    }
                    final String leafAttributeFlatPath = toLeafAttributeFlatPath(targetAttribute);
                    if (leafAttributeFlatPath != null) {
                        amendedCondition.getTargetAttributesFlatPath().add(leafAttributeFlatPath);
                        continue;
                    }
                    final String combined = amendedTargetRoot + "/" + targetAttribute;
                    final AqlToFlatPathConverter.Result attrResult =
                            aqlToFlatPathConverter.convert(combined, null, webTemplate);
                    final String fullFlatPath = attrResult.flatPath();
                    final String attributeFlatPath = fullFlatPath.replace(rootResult.flatPath() + "/", "");
                    final String replaceTerminologyAqlSyntax =
                            attributeFlatPath.contains("terminology") ? attributeFlatPath : attributeFlatPath
                                                                                            .replace("/defining_code/code_string", "|code")
                                                                                            .replace("/defining_code", "|code")
                                                                                            .replace("defining_code/code_string", "|code")
                                                                                            .replace("defining_code", "|code");
                    amendedCondition.getTargetAttributesFlatPath()
                            .add(replaceTerminologyAqlSyntax);
                }
            }
        }
        amendedCondition.setTargetRoot(amendedTargetRoot);

        return amendedCondition;
    }

    private String resolveFullOpenEhrPath(final String openEhrPath,
                                          final String coreArchetype,
                                          final String fallback,
                                          final MappingHelper parentHelper,
                                          final String fullSlotPath) {
        if (StringUtils.isEmpty(openEhrPath) || openEhrPath.contains(OPENEHR_COMPOSITION_FC)) {
            return fallback;
        }
        if (openEhrPath.contains(OPENEHR_ARCHETYPE_FC)) {
            final String suffix = openEhrPath.substring(OPENEHR_ARCHETYPE_FC.length());
            if (!StringUtils.isEmpty(fullSlotPath)) {
                return fullSlotPath + suffix;
            }
            return coreArchetype + suffix;
        }
        if (openEhrPath.contains(OPENEHR_ROOT_FC)) {
            final String parentFullPath = parentHelper != null ? parentHelper.getFullOpenEhrPath() : null;
            final String fullRootPath = StringUtils.isEmpty(parentFullPath) ? fullSlotPath
                    : parentFullPath;
            if (fullRootPath != null) {
                return openEhrPath.replace(OPENEHR_ROOT_FC, fullRootPath);
            }
            return fallback;
        }
        if (parentHelper != null && parentHelper.getFullOpenEhrPath() != null) {
            final String openEhrPathWithoutReference = openEhrPath.replace(REFERENCE, "");
            if (StringUtils.isEmpty(openEhrPathWithoutReference)) {
                return parentHelper.getFullOpenEhrPath(); // so there's no trailing slash
            }
            return String.format("%s/%s", parentHelper.getFullOpenEhrPath(), openEhrPathWithoutReference);
        }
        return fallback;
    }

    String getRelativeFhirPath(final String coreResource,
                               final String pathToAmend,
                               final MappingHelper parentHelper,
                               final String fullFhirSlotPath) {
        if (StringUtils.isEmpty(pathToAmend)) {
            return pathToAmend;
        }
        final String resolvedResource = coreResource.replace("BackboneElement", "");

        // Handle exact constant matches first
        if (FHIR_RESOURCE_FC.equals(pathToAmend)) {
            return resolvedResource;
        }
        if (FHIR_ROOT_FC.equals(pathToAmend)) {
            return parentHelper != null ? parentHelper.getFullFhirPath() : fullFhirSlotPath;
        }

        // Replace constants in the path
        return pathToAmend
                .replace(String.format("%s.", FHIR_ROOT_FC), "")
                .replace(String.format("%s.", FHIR_RESOURCE_FC), "");
    }

    String getAbsoluteFhirPath(final String originalFhirPath,
                               final String relativeFhirPath,
                               final String generatingResource,
                               final MappingHelper parentHelper,
                               final String fullFhirSlotPath) {
        if (StringUtils.isEmpty(originalFhirPath)) {
            return originalFhirPath;
        }

        final String parentFullFhirPath = parentHelper != null ? parentHelper.getFullFhirPath() : generatingResource;

        // Handle exact constant matches first
        if (FHIR_RESOURCE_FC.equals(originalFhirPath)) {
            if (StringUtils.isNotEmpty(fullFhirSlotPath)) {
                return fullFhirSlotPath;
            } else {
                return parentFullFhirPath;
            }
        }
        if (FHIR_ROOT_FC.equals(originalFhirPath)) {
            if (StringUtils.isNotEmpty(parentFullFhirPath)) {
                return parentFullFhirPath;
            } else {
                return fullFhirSlotPath;
            }
        }

        final String resourceWithDot = String.format("%s.", FHIR_RESOURCE_FC);
        final String rootWithDot = String.format("%s.", FHIR_ROOT_FC);

        if (originalFhirPath.contains(resourceWithDot)) {
            if (StringUtils.isNotEmpty(fullFhirSlotPath)) {
                return String.format("%s.%s", fullFhirSlotPath, relativeFhirPath);
            } else {
                return String.format("%s.%s", parentFullFhirPath, relativeFhirPath);
            }
        }
        if (originalFhirPath.contains(rootWithDot)) {
            if (StringUtils.isNotEmpty(parentFullFhirPath)) {
                return String.format("%s.%s", parentFullFhirPath, relativeFhirPath);
            } else {
                return String.format("%s.%s", fullFhirSlotPath, relativeFhirPath);
            }
        }
        // means there's no constant, just concatenation
        final String relevantPrefix = parentFullFhirPath != null ? parentFullFhirPath : fullFhirSlotPath;
        return String.format("%s.%s", relevantPrefix, relativeFhirPath);
    }

    String amendOpenEhrPath(final String coreOpenEhrArchetype,
                            final String pathToAmend,
                            final MappingHelper parentHelper) {
        if (StringUtils.isEmpty(pathToAmend)) {
            return pathToAmend;
        }

        // $composition paths are left untouched (runtime-resolved)
        if (pathToAmend.contains(OPENEHR_COMPOSITION_FC)) {
            return pathToAmend;
        }

        // Handle exact constant matches first
        if (OPENEHR_ARCHETYPE_FC.equals(pathToAmend)) {
            return coreOpenEhrArchetype;
        }
        if (OPENEHR_ROOT_FC.equals(pathToAmend)) {
            return parentHelper != null ? parentHelper.getFullOpenEhrPath() : pathToAmend;
        }

        // Replace constants in the path
        String result = pathToAmend.replace(OPENEHR_ARCHETYPE_FC, coreOpenEhrArchetype);
        if (parentHelper != null && parentHelper.getFullOpenEhrPath() != null) {
            result = result.replace(OPENEHR_ROOT_FC, parentHelper.getFullOpenEhrPath());
        }
        return result;
    }
}
