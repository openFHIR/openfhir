
package com.syntaric.openfhir.fc;

import static com.syntaric.openfhir.fc.FhirConnectConst.OPENEHR_ROOT_FC;
import static com.syntaric.openfhir.fc.FhirConnectConst.UNIDIRECTIONAL_TOFHIR;
import static com.syntaric.openfhir.fc.FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR;

import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.FollowedBy;
import com.syntaric.openfhir.fc.schema.model.Manual;
import com.syntaric.openfhir.fc.schema.model.ManualEntry;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import com.syntaric.openfhir.fc.schema.model.OpenEhrConfig;
import com.syntaric.openfhir.fc.schema.model.With;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

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
                mapping.getWith().setType("NONE"); // when a manual mapping is present, dynamic shouldn't happen at all https://github.com/openFHIR/openfhir/issues/54
            }
        }
        return toReturn;
    }

    private void expandManualMappings(final Mapping mapping, final List<Mapping> toReturn) {
        for (final Manual manual : mapping.getManual()) {
            if (manual.getOpenehr() != null) {
                toReturn.addAll(buildOpenEhrManualMappings(mapping, manual));
            }
            if (manual.getFhir() != null) {
                appendFhirManualMappings(mapping, manual);
            }
        }
        toReturn.add(mapping);
    }

    private List<Mapping> buildOpenEhrManualMappings(final Mapping mapping, final Manual manual) {
        final List<Mapping> result = new ArrayList<>();
        for (final ManualEntry openEhrManualEntry : manual.getOpenehr()) {
            final String manualOpenehrPath = openEhrManualEntry.getPath().replace(OPENEHR_ROOT_FC, "");
            final String openEhrSuffix = "value".equals(manualOpenehrPath) ? "" : manualOpenehrPath;
            final String manualSuffix = StringUtils.isEmpty(openEhrSuffix) ? "" : "/" + openEhrSuffix;

            final Mapping fromManual = new Mapping();
            fromManual.setUnidirectional(UNIDIRECTIONAL_TOOPENEHR);
            fromManual.setName(mapping.getName() + "." + manual.getName());
            fromManual.setWith(new With()
                                       .withValue(openEhrManualEntry.getValue())
                                       .withOpenehr(mapping.getWith().getOpenehr() + manualSuffix));
            fromManual.setFhirCondition(manual.getFhirCondition() == null
                                                ? mapping.getFhirCondition()
                                                : manual.getFhirCondition().copy());
            result.add(fromManual);
        }
        return result;
    }

    private void appendFhirManualMappings(final Mapping mapping, final Manual manual) {
        final FollowedBy followedBy = mapping.getFollowedBy() == null ? new FollowedBy() : mapping.getFollowedBy();
        if (followedBy.getMappings() == null) {
            followedBy.setMappings(new ArrayList<>());
        }
        for (final ManualEntry fhirManualEntry : manual.getFhir()) {
            final Mapping fromManual = new Mapping();
            fromManual.setUnidirectional(UNIDIRECTIONAL_TOFHIR);
            fromManual.setName(mapping.getName() + "." + manual.getName());
            fromManual.setWith(new With()
                                       .withValue(fhirManualEntry.getValue())
                                       .withOpenehr(mapping.getWith().getOpenehr())
                                       .withFhir(fhirManualEntry.getPath()));
            fromManual.setOpenehrCondition(resolveOpenEhrCondition(mapping, manual));
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
        if(fhirPath.startsWith("'")) {
            return fhirPath.substring(0, fhirPath.lastIndexOf("&")+1);
        }
        return null;
    }

    String getSuffixConcat(final String fhirPath) {
        if(fhirPath.endsWith("'")) {
            return fhirPath.substring(fhirPath.indexOf("&"));
        }
        return null;
    }

    private String parseResourceType(final FhirConnectModel fhirConnectModel) {
        final String structureDefinition = fhirConnectModel.getSpec().getFhirConfig().getStructureDefinition();
        return structureDefinition.replace("http://hl7.org/fhir/StructureDefinition/", "");
    }


}
