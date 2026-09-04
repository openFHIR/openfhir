
package com.syntaric.openfhir.fc;

import com.syntaric.openfhir.fc.schema.model.*;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.syntaric.openfhir.fc.FhirConnectConst.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenFhirFhirConnectModelMapper {

    private String name;
    private OpenFhirFhirConfig fhirConfig;
    private OpenEhrConfig openEhrConfig;
    private List<Mapping> mappings;
    private Terminology terminology;
    private FhirConnectModel originalModel;

    public OpenFhirFhirConnectModelMapper copy() {
        final OpenFhirFhirConnectModelMapper fhirConnectMapper = new OpenFhirFhirConnectModelMapper();
        fhirConnectMapper.setFhirConfig(fhirConfig == null ? null : fhirConfig.copy());
        fhirConnectMapper.setName(name);
        fhirConnectMapper.setOpenEhrConfig(openEhrConfig == null ? null : openEhrConfig.copy());
        fhirConnectMapper.setTerminology(terminology);
        fhirConnectMapper.setOriginalModel(originalModel);
        if (mappings != null) {
            final List<Mapping> copiedMappings = new ArrayList<>();
            for (Mapping mapping : mappings) {
                copiedMappings.add(mapping.copy());
            }
            fhirConnectMapper.setMappings(copiedMappings);
        }
        return fhirConnectMapper;
    }

    public OpenFhirFhirConnectModelMapper fromFhirConnectModelMapper(final FhirConnectModel fhirConnectModel) {
        final OpenFhirFhirConnectModelMapper openFhirFhirConnectModelMapper = new OpenFhirFhirConnectModelMapper();
        openFhirFhirConnectModelMapper.setMappings(handleMappings(fhirConnectModel.getMappings()));
        doManualMappings(fhirConnectModel.getMappings());
        openFhirFhirConnectModelMapper.setOpenEhrConfig(
                new OpenEhrConfig().withArchetype(fhirConnectModel.getSpec().getOpenEhrConfig().getArchetype()));

        openFhirFhirConnectModelMapper.setFhirConfig(new OpenFhirFhirConfig()
                .withCondition(
                        getPreprocessingFhirConditions(fhirConnectModel))
                .withResource(parseResourceType(fhirConnectModel)));
        openFhirFhirConnectModelMapper.setName(fhirConnectModel.getMetadata().getName());
        openFhirFhirConnectModelMapper.setTerminology(fhirConnectModel.getTerminology());
        openFhirFhirConnectModelMapper.setOriginalModel(fhirConnectModel);
        return openFhirFhirConnectModelMapper;
    }

    private List<Condition> getPreprocessingFhirConditions(final FhirConnectModel fhirConnectModel) {
        if (fhirConnectModel.getPreprocessor() == null) {
            return null;
        }
        if (fhirConnectModel.getPreprocessor().getFhirCondition() != null) {
            return List.of(fhirConnectModel.getPreprocessor().getFhirCondition());
        }
        return fhirConnectModel.getPreprocessor().getFhirConditions();
    }

    private void doManualMappings(final List<Mapping> mappings) {
        if (mappings == null) {
            return;
        }
        for (final Mapping mapping : mappings) {
            if (mapping.getFollowedBy() == null
                    || mapping.getFollowedBy().getMappings() == null
                    || mapping.getFollowedBy().getMappings().isEmpty()) {
                continue;
            }
            mapping.getFollowedBy().setMappings(handleMappings(mapping.getFollowedBy().getMappings()));
            doManualMappings(mapping.getFollowedBy().getMappings());
        }
    }

    private List<Mapping> handleMappings(final List<Mapping> mappingsFromFile) {
        if (mappingsFromFile == null) {
            return null;
        }
        final List<Mapping> toReturn = new ArrayList<>();
        for (final Mapping mapping : mappingsFromFile) {
            if (mapping.getManual() == null || mapping.getManual().isEmpty()) {
                processFhirPathConcatenation(mapping);
                toReturn.add(mapping);
            } else {
                expandManualMappings(mapping, toReturn);
                mapping.getWith().setType(FhirConnectConst.OPENEHR_TYPE_NONE); // when a manual mapping is present, dynamic shouldn't happen at all https://github.com/openFHIR/openfhir/issues/54
            }
        }
        return toReturn;
    }

    private void expandManualMappings(final Mapping mapping, final List<Mapping> toReturn) {
        for (final Manual manual : mapping.getManual()) {
            if (manual.getOpenehr() != null) {
                appendOpenEhrManualMappings(mapping, manual);
            }
            if (manual.getFhir() != null) {
                appendFhirManualMappings(mapping, manual);
            }
        }
        toReturn.add(mapping);
    }

    private void appendFhirManualMappings(final Mapping mapping, final Manual manual) {
        final FollowedBy followedBy = mapping.getFollowedBy() == null ? new FollowedBy() : mapping.getFollowedBy();
        if (followedBy.getMappings() == null) {
            followedBy.setMappings(new ArrayList<>());
        }
        followedBy.getMappings().addAll(groupedFhirManualMappings(mapping, manual, manual.getFhir()));
        mapping.setFollowedBy(followedBy);
    }

    /**
     * Expands a manual block's fhir entries into followedBy mappings. Entries whose paths share a
     * dotted prefix (e.g. "code.coding.code" and "code.coding.system") are nested under a single
     * synthetic mapping for that prefix, so the shared intermediate FHIR elements are instantiated
     * once and every value lands on the same instance. A flat one-mapping-per-entry expansion would
     * re-instantiate the prefix per entry, overwriting previously set values on single-valued
     * elements (only the last entry would survive).
     */
    private List<Mapping> groupedFhirManualMappings(final Mapping mapping, final Manual manual,
                                                    final List<ManualEntry> entries) {
        final Map<String, List<ManualEntry>> groups = new LinkedHashMap<>();
        for (final ManualEntry entry : entries) {
            groups.computeIfAbsent(splitPathSegments(entry.getPath()).get(0), k -> new ArrayList<>()).add(entry);
        }
        final List<Mapping> generated = new ArrayList<>();
        for (final List<ManualEntry> group : groups.values()) {
            final List<String> commonPrefix = longestCommonPathPrefix(group);
            final boolean allIdenticalPaths = group.stream()
                    .allMatch(entry -> splitPathSegments(entry.getPath()).size() == commonPrefix.size());
            if (group.size() == 1 || allIdenticalPaths) {
                // nothing shared to preserve — or identical paths, where each entry is meant to
                // create its own element (e.g. several codings) — keep the flat expansion
                for (final ManualEntry entry : group) {
                    generated.add(fhirManualLeaf(mapping, manual, entry.getPath(), entry.getValue()));
                }
            } else {
                generated.add(fhirManualGroup(mapping, manual, group, commonPrefix));
            }
        }
        return generated;
    }

    private Mapping fhirManualGroup(final Mapping mapping, final Manual manual,
                                    final List<ManualEntry> group, final List<String> commonPrefix) {
        final Mapping intermediate = new Mapping();
        intermediate.setUnidirectional(UNIDIRECTIONAL_TOFHIR);
        intermediate.setName(mapping.getName() + "." + manual.getName());
        intermediate.setWith(new With()
                .withType(OPENEHR_TYPE_NONE)
                .withOpenehr(mapping.getWith().getOpenehr())
                .withFhir(String.join(".", commonPrefix)));
        intermediate.setOpenehrCondition(resolveOpenEhrCondition(mapping, manual));

        final List<ManualEntry> remainders = new ArrayList<>();
        for (final ManualEntry entry : group) {
            final List<String> segments = splitPathSegments(entry.getPath());
            final String remainder = String.join(".", segments.subList(commonPrefix.size(), segments.size()));
            final ManualEntry remainderEntry = entry.copy();
            // an entry whose path IS the shared prefix sets its value on the shared element itself
            remainderEntry.setPath(remainder.isEmpty() ? FHIR_ROOT_FC : remainder);
            remainders.add(remainderEntry);
        }
        final FollowedBy inner = new FollowedBy();
        inner.setMappings(groupedFhirManualMappings(mapping, manual, remainders));
        intermediate.setFollowedBy(inner);
        return intermediate;
    }

    private Mapping fhirManualLeaf(final Mapping mapping, final Manual manual,
                                   final String path, final String value) {
        final Mapping fromManual = new Mapping();
        fromManual.setUnidirectional(UNIDIRECTIONAL_TOFHIR);
        fromManual.setName(mapping.getName() + "." + manual.getName());
        fromManual.setWith(new With()
                .withValue(value)
                .withOpenehr(mapping.getWith().getOpenehr())
                .withFhir(path));
        fromManual.setOpenehrCondition(resolveOpenEhrCondition(mapping, manual));
        return fromManual;
    }

    private List<String> longestCommonPathPrefix(final List<ManualEntry> group) {
        List<String> prefix = null;
        for (final ManualEntry entry : group) {
            final List<String> segments = splitPathSegments(entry.getPath());
            if (prefix == null) {
                prefix = new ArrayList<>(segments);
                continue;
            }
            int i = 0;
            while (i < prefix.size() && i < segments.size() && prefix.get(i).equals(segments.get(i))) {
                i++;
            }
            prefix = prefix.subList(0, i);
        }
        return prefix == null ? List.of() : prefix;
    }

    /**
     * Splits a fhir path on dots, ignoring dots inside parentheses so segments like
     * "as(Quantity)" or "where(system.value = 'x')" stay whole.
     */
    static List<String> splitPathSegments(final String path) {
        final List<String> segments = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < path.length(); i++) {
            final char c = path.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '.' && depth == 0) {
                segments.add(path.substring(start, i));
                start = i + 1;
            }
        }
        segments.add(path.substring(start));
        return segments;
    }

    private void appendOpenEhrManualMappings(final Mapping mapping, final Manual manual) {
        final FollowedBy followedBy = mapping.getFollowedBy() == null ? new FollowedBy() : mapping.getFollowedBy();
        if (followedBy.getMappings() == null) {
            followedBy.setMappings(new ArrayList<>());
        }
        boolean isCodedText = manual.getOpenehr().stream().anyMatch(oe -> oe.getPath().contains("defining_code"));
        for (final ManualEntry openEhrManualEntry : manual.getOpenehr()) {
            final String manualOpenehrPath = openEhrManualEntry.getPath().replace(OPENEHR_ROOT_FC, "");
            final String openEhrSuffix = "value".equals(manualOpenehrPath) ? "" : manualOpenehrPath;
            final String manualSuffix = StringUtils.isEmpty(openEhrSuffix) ? "" : "/" + openEhrSuffix;

            final Mapping fromManual = new Mapping();
            fromManual.setManualCodedText(isCodedText);
            fromManual.setUnidirectional(UNIDIRECTIONAL_TOOPENEHR);
            fromManual.setName(mapping.getName() + "." + manual.getName());
            fromManual.setWith(new With()
                    .withValue(openEhrManualEntry.getValue())
                    .withFhir(mapping.getWith().getFhir())
                    .withOpenehr(OPENEHR_ROOT_FC + manualSuffix));
            final Condition manualCondition = manual.getFhirCondition();
            if (manualCondition != null && manualCondition.getTargetRoot().startsWith(FhirConnectConst.FHIR_ROOT_FC)) {
                manualCondition.setTargetRoot(manualCondition.getTargetRoot().replace(FhirConnectConst.FHIR_ROOT_FC,
                        mapping.getWith().getFhir()));
            }
            fromManual.setFhirCondition(manualCondition == null
                    ? mapping.getFhirCondition()
                    : manualCondition.copy());
            followedBy.getMappings().add(fromManual);
        }
        mapping.setFollowedBy(followedBy);
    }

    private Condition resolveOpenEhrCondition(final Mapping mapping, final Manual manual) {
        if (manual.getOpenehrCondition() == null) {
            return mapping.getOpenehrCondition();
        }
        final Condition openEhrCondition = manual.getOpenehrCondition().copy();
        if (openEhrCondition.getTargetRoot().equals(OPENEHR_ROOT_FC)) {
            openEhrCondition.setTargetRoot(mapping.getWith().getOpenehr());
        }
        return openEhrCondition;
    }

    void processFhirPathConcatenation(final Mapping mapping) {
        if (mapping.getWith() == null || mapping.getWith().getFhir() == null) {
            return;
        }

        final String fhirPath = mapping.getWith().getFhir();
        if (!isFhirPathConcatination(fhirPath)) {
            return;
        }

        String modifiedFhirPath = fhirPath;

        // Process prefix concatenation
        final String prefixConcat = getPrefixConcat(fhirPath);
        if (prefixConcat != null) {
            modifiedFhirPath = modifiedFhirPath.replace(prefixConcat, "");
            mapping.setPrefixConcat(prefixConcat.trim()
                    .replace("'", "")
                    .replace("&", "").trim());
        }

        // Process suffix concatenation
        final String suffixConcat = getSuffixConcat(modifiedFhirPath);
        if (suffixConcat != null) {
            modifiedFhirPath = modifiedFhirPath.replace(suffixConcat, "");
            mapping.setSuffixConcat(suffixConcat.trim()
                    .replace("'", "")
                    .replace("&", "").trim());
        }

        // Update the mapping with the cleaned fhir path
        mapping.getWith().setFhir(modifiedFhirPath.trim());
    }

    boolean isFhirPathConcatination(final String fhirPath) {
        return fhirPath.contains("&'")
                || fhirPath.contains("& '")
                || fhirPath.contains("'&")
                || fhirPath.contains("' &");
    }

    String getPrefixConcat(final String fhirPath) {
        if (fhirPath.startsWith("'")) {
            return fhirPath.substring(0, fhirPath.lastIndexOf("&") + 1);
        }
        return null;
    }

    String getSuffixConcat(final String fhirPath) {
        if (fhirPath.endsWith("'")) {
            return fhirPath.substring(fhirPath.indexOf("&"));
        }
        return null;
    }

    private String parseResourceType(final FhirConnectModel fhirConnectModel) {
        final String structureDefinition = fhirConnectModel.getSpec().getFhirConfig().getStructureDefinition();
        return structureDefinition.replace("http://hl7.org/fhir/StructureDefinition/", "");
    }


}
