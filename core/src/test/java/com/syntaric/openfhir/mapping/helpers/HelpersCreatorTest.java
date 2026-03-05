package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.util.OpenFhirStringUtils;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Metadata;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.FollowedBy;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import com.syntaric.openfhir.fc.schema.model.With;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HelpersCreatorTest {

    private HelpersCreator helpersCreator;
    private MappingHelper parentHelper;

    @Before
    public void setUp() {
        helpersCreator = new HelpersCreator(null, null, new OpenFhirStringUtils());
        parentHelper = new MappingHelper();
    }

    private FhirConnectModel createFcModel(String name, String archetype) {
        FhirConnectModel model = new FhirConnectModel();
        model.setId("test-id");
        model.setMetadata(new Metadata());
        model.getMetadata().setName(name);

        com.syntaric.openfhir.fc.schema.Spec spec = new com.syntaric.openfhir.fc.schema.Spec();
        com.syntaric.openfhir.fc.schema.model.OpenEhrConfig openEhrConfig = new com.syntaric.openfhir.fc.schema.model.OpenEhrConfig();
        openEhrConfig.setArchetype(archetype);
        spec.setOpenEhrConfig(openEhrConfig);
        model.setSpec(spec);

        return model;
    }

    // ==================== getAbsoluteFhirPath Tests ====================


    // ==================== amendOpenEhrPath Tests ====================

    @Test
    public void amendOpenEhrPath_whenPathIsNull_returnsNull() {
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2", null,
                                                        parentHelper);
        Assert.assertNull(result);
    }

    @Test
    public void amendOpenEhrPath_whenPathIsEmpty_returnsEmpty() {
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2", "", parentHelper);
        Assert.assertEquals("", result);
    }

    @Test
    public void amendOpenEhrPath_whenPathContainsComposition_returnsUnchanged() {
        String path = "$composition/context/start_time";
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2", path,
                                                        parentHelper);
        Assert.assertEquals("$composition/context/start_time", result);
    }

    @Test
    public void amendOpenEhrPath_whenPathIsArchetypeConstant_returnsCoreArchetype() {
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        FhirConnectConst.OPENEHR_ARCHETYPE_FC, parentHelper);
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2", result);
    }

    @Test
    public void amendOpenEhrPath_whenPathIsRootConstant_returnsParentFullOpenEhrPath() {
        parentHelper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]");
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        FhirConnectConst.OPENEHR_ROOT_FC, parentHelper);
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]", result);
    }

    @Test
    public void amendOpenEhrPath_whenPathContainsArchetypePrefix_replacesWithCoreArchetype() {
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        "$archetype/protocol[at0011]", parentHelper);
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/protocol[at0011]", result);
    }

    @Test
    public void amendOpenEhrPath_whenPathContainsOpenEhrRootPrefix_replacesWithParentPath() {
        parentHelper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]");
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        "$openehrRoot/state[at0007]", parentHelper);
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]/state[at0007]",
                            result);
    }

    @Test
    public void amendOpenEhrPath_whenPathIsRegularOpenEhrPath_returnsUnchanged() {
        String path = "openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]/data[at0003]/items[at0004]";
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2", path,
                                                        parentHelper);
        Assert.assertEquals(path, result);
    }

    @Test
    public void amendOpenEhrPath_basedOnYamlExample_compositionStartTime() {
        // Based on problem_list.yml: openehr: "$composition/context/start_time"
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-COMPOSITION.problem_list.v2",
                                                        "$composition/context/start_time", parentHelper);
        Assert.assertEquals("$composition/context/start_time", result);
    }

    @Test
    public void amendOpenEhrPath_basedOnYamlExample_archetypeOnly() {
        // Based on problem_list.yml: openehr: "$archetype"
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-COMPOSITION.problem_list.v2",
                                                        "$archetype", parentHelper);
        Assert.assertEquals("openEHR-EHR-COMPOSITION.problem_list.v2", result);
    }

    @Test
    public void amendOpenEhrPath_basedOnYamlExample_archetypeWithPath() {
        // Based on blood_pressure_nhealth.yml: openehr: "$archetype/protocol[at0011]/items[at1035]"
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        "$archetype/protocol[at0011]/items[at1035]", parentHelper);
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/protocol[at0011]/items[at1035]", result);
    }

    @Test
    public void amendOpenEhrPath_basedOnYamlExample_nestedOpenEhrRoot() {
        // Based on blood_pressure_nhealth.yml: openehr: "$openehrRoot" within component mapping
        parentHelper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]");
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        "$openehrRoot", parentHelper);
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]", result);
    }

    @Test
    public void amendOpenEhrPath_basedOnYamlExample_problemDiagnosisEntry() {
        // Based on problem_list.yml: openehr: "$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]"
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-COMPOSITION.problem_list.v2",
                                                        "$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]",
                                                        parentHelper);
        Assert.assertEquals("$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]", result);
    }

    @Test
    public void amendOpenEhrPath_withComplexNestedPath_handlesCorrectly() {
        parentHelper.setFullOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]");
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.blood_pressure.v2",
                                                        "$openehrRoot/data[at0003]/items[at0004]", parentHelper);
        Assert.assertEquals(
                "openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]/data[at0003]/items[at0004]",
                result);
    }

    @Test
    public void amendOpenEhrPath_withMultipleLevelsOfNesting_replacesCorrectly() {
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-OBSERVATION.body_temperature.v2",
                                                        "$archetype/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                                                        parentHelper);
        Assert.assertEquals(
                "openEHR-EHR-OBSERVATION.body_temperature.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                result);
    }

    @Test
    public void amendOpenEhrPath_compositionPathWithMultipleSegments_leavesUnchanged() {
        String path = "$composition/context/other_context[at0001]/items[at0002]";
        String result = helpersCreator.amendOpenEhrPath("openEHR-EHR-COMPOSITION.encounter.v1", path, parentHelper);
        Assert.assertEquals("$composition/context/other_context[at0001]/items[at0002]", result);
    }

    // ==================== createHelpers Tests ====================

    /**
     * Test with null mappings list - should return empty list.
     *
     * <pre>
     * mappings: null
     * </pre>
     */
    @Test
    public void createHelpers_withNullMappings_returnsEmptyList() {
        FhirConnectModel model = new FhirConnectModel();
        model.setId("test-id");
        final Metadata metadata = new Metadata();
        model.setMetadata(metadata);
        model.getMetadata().setName("test-context");

        List<MappingHelper> result = helpersCreator.createHelpers(model, "Patient", null, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(0, result.size());
    }

    /**
     * Test single simple mapping with $resource and $composition constants.
     * Based on problem_list.yml date mapping.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-COMPOSITION.problem_list.v2
     * mappings:
     *   - name: "date"
     *     with:
     *       fhir: "$resource.date"
     *       openehr: "$composition/context/start_time"
     * </pre>
     * <p>
     * Expected result:
     * - fhir: "Composition.date" ($resource replaced with ResourceType name)
     * - openehr: "$composition/context/start_time" ($composition preserved)
     */
    @Test
    public void createHelpers_singleSimpleMapping_createsOneHelper() {
        FhirConnectModel context = createFcModel("problem_list", "openEHR-EHR-COMPOSITION.problem_list.v2");

        Mapping mapping = new Mapping();
        mapping.setName("date");

        With with = new With();
        with.setFhir("$resource.date");
        with.setOpenehr("$composition/context/start_time");
        mapping.setWith(with);

        List<Mapping> mappings = java.util.Arrays.asList(mapping);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", mappings, null, null);

        Assert.assertNotNull(result);
        Assert.assertEquals(1, result.size());

        MappingHelper helper = result.get(0);
        Assert.assertEquals("date", helper.getFhir());
        Assert.assertEquals("Composition.date", helper.getFullFhirPath());
        Assert.assertEquals("$composition/context/start_time", helper.getOpenEhr());
    }

    /**
     * Test multiple mappings with $resource and $archetype constants.
     * Based on blood_pressure_nhealth.yml.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-OBSERVATION.blood_pressure.v2
     * mappings:
     *   - name: method
     *     with:
     *       fhir: "$resource.method"
     *       openehr: "$archetype/protocol[at0011]/items[at1035]"
     *
     *   - name: bodySite
     *     with:
     *       fhir: "$resource.bodySite"
     *       openehr: "$archetype/protocol[at0011]/items[at0014]"
     * </pre>
     * <p>
     * Expected result:
     * - 2 helpers created
     * - $resource replaced with "Observation"
     * - $archetype replaced with "openEHR-EHR-OBSERVATION.blood_pressure.v2"
     */
    @Test
    public void createHelpers_multipleMappings_createsMultipleHelpers() {
        FhirConnectModel context = createFcModel("blood_pressure", "openEHR-EHR-OBSERVATION.blood_pressure.v2");

        Mapping mapping1 = new Mapping();
        mapping1.setName("method");
        With with1 = new With();
        with1.setFhir("$resource.method");
        with1.setOpenehr("$archetype/protocol[at0011]/items[at1035]");
        mapping1.setWith(with1);

        Mapping mapping2 = new Mapping();
        mapping2.setName("bodySite");
        With with2 = new With();
        with2.setFhir("$resource.bodySite");
        with2.setOpenehr("$archetype/protocol[at0011]/items[at0014]");
        mapping2.setWith(with2);

        List<Mapping> mappings = java.util.Arrays.asList(mapping1, mapping2);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Observation", mappings, null, null);

        Assert.assertEquals(2, result.size());
        Assert.assertEquals("method", result.get(0).getFhir());
        Assert.assertEquals("Observation.method", result.get(0).getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/protocol[at0011]/items[at1035]",
                            result.get(0).getOpenEhr());
        Assert.assertEquals("bodySite", result.get(1).getFhir());
        Assert.assertEquals("Observation.bodySite", result.get(1).getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/protocol[at0011]/items[at0014]",
                            result.get(1).getOpenEhr());
    }

    /**
     * Test nested mapping with followedBy using $fhirRoot and $archetype.
     * Based on problem_list.yml sectionActiveProblems mapping.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-COMPOSITION.problem_list.v2
     * mappings:
     *   - name: "sectionActiveProblems"
     *     with:
     *       fhir: "$resource.section"
     *       openehr: "$archetype"
     *     followedBy:
     *       mappings:
     *         - name: "title"
     *           with:
     *             fhir: "$fhirRoot"
     *             openehr: "$archetype"
     * </pre>
     * <p>
     * Expected result:
     * - Parent: fhir="Composition.section", openehr="openEHR-EHR-COMPOSITION.problem_list.v2"
     * - Child: fhir="Composition.section" ($fhirRoot resolves to parent's fullFhirPath)
     * openehr="openEHR-EHR-COMPOSITION.problem_list.v2" ($archetype resolves to spec.openEhrConfig.archetype)
     */
    @Test
    public void createHelpers_withFollowedBy_createsNestedHelpers() {
        FhirConnectModel context = createFcModel("problem_list", "openEHR-EHR-COMPOSITION.problem_list.v2");

        Mapping parentMapping = new Mapping();
        parentMapping.setName("sectionActiveProblems");

        With parentWith = new With();
        parentWith.setFhir("$resource.section");
        parentWith.setOpenehr("$archetype");
        parentMapping.setWith(parentWith);

        // Child mapping with $fhirRoot
        Mapping childMapping = new Mapping();
        childMapping.setName("title");

        With childWith = new With();
        childWith.setFhir("$fhirRoot");
        childWith.setOpenehr("$archetype");
        childMapping.setWith(childWith);

        FollowedBy followedBy = new FollowedBy();
        followedBy.setMappings(java.util.Arrays.asList(childMapping));
        parentMapping.setFollowedBy(followedBy);

        List<Mapping> mappings = java.util.Arrays.asList(parentMapping);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", mappings, null, null);

        Assert.assertEquals(1, result.size());

        MappingHelper parent = result.get(0);
        Assert.assertEquals("section", parent.getFhir());
        Assert.assertEquals("Composition.section", parent.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.problem_list.v2", parent.getOpenEhr());

        // Check children
        Assert.assertNotNull(parent.getChildren());
        Assert.assertEquals(1, parent.getChildren().size());

        MappingHelper child = parent.getChildren().get(0);
        Assert.assertEquals("Composition.section", child.getFhir());
        Assert.assertEquals("Composition.section", child.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.problem_list.v2", child.getOpenEhr());
    }

    /**
     * Test 3-level deep nested followedBy with $fhirRoot and $openehrRoot.
     * Based on blood_pressure_nhealth.yml componentParent → systolicParent → systolic.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-OBSERVATION.blood_pressure.v2
     * mappings:
     *   - name: componentParent
     *     with:
     *       fhir: "$resource.component"
     *       openehr: "$archetype/data[at0001]/events[at0006]"
     *     followedBy:
     *       mappings:
     *         - name: systolicParent
     *           with:
     *             fhir: "$fhirRoot"
     *             openehr: "$openehrRoot"
     *           followedBy:
     *             mappings:
     *               - name: systolic
     *                 with:
     *                   fhir: "value"
     *                   openehr: "data[at0003]/items[at0004]"
     * </pre>
     * <p>
     * Expected result:
     * - Level 1: fhir="Observation.component",
     * openehr="openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]"
     * - Level 2: fhir="Observation.component" ($fhirRoot → parent's fullFhirPath)
     * openehr="openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]" ($openehrRoot → parent's
     * fullOpenEhrPath)
     * - Level 3: fullFhirPath="Observation.component.value" (plain path concatenated)
     * fullOpenEhrPath="openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006].data[at0003]/items[at0004]"
     */
    @Test
    public void createHelpers_deeplyNestedFollowedBy_createsHierarchy() {
        FhirConnectModel context = createFcModel("blood_pressure", "openEHR-EHR-OBSERVATION.blood_pressure.v2");

        // Level 1: componentParent
        Mapping componentParent = new Mapping();
        componentParent.setName("componentParent");
        With componentWith = new With();
        componentWith.setFhir("$resource.component");
        componentWith.setOpenehr("$archetype/data[at0001]/events[at0006]");
        componentParent.setWith(componentWith);

        // Level 2: systolicParent
        Mapping systolicParent = new Mapping();
        systolicParent.setName("systolicParent");
        With systolicParentWith = new With();
        systolicParentWith.setFhir("$fhirRoot");
        systolicParentWith.setOpenehr("$openehrRoot");
        systolicParent.setWith(systolicParentWith);

        // Level 3: systolic value
        Mapping systolicValue = new Mapping();
        systolicValue.setName("systolic");
        With systolicValueWith = new With();
        systolicValueWith.setFhir("value");
        systolicValueWith.setOpenehr("data[at0003]/items[at0004]");
        systolicValue.setWith(systolicValueWith);

        // Wire up the hierarchy
        FollowedBy systolicFollowedBy = new FollowedBy();
        systolicFollowedBy.setMappings(java.util.Arrays.asList(systolicValue));
        systolicParent.setFollowedBy(systolicFollowedBy);

        FollowedBy componentFollowedBy = new FollowedBy();
        componentFollowedBy.setMappings(java.util.Arrays.asList(systolicParent));
        componentParent.setFollowedBy(componentFollowedBy);

        List<Mapping> mappings = java.util.Arrays.asList(componentParent);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Observation", mappings, null, null);

        Assert.assertEquals(1, result.size());

        // Level 1 validation
        MappingHelper level1 = result.get(0);
        Assert.assertEquals("component", level1.getFhir());
        Assert.assertEquals("Observation.component", level1.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]",
                            level1.getOpenEhr());
        Assert.assertEquals(1, level1.getChildren().size());

        // Level 2 validation
        MappingHelper level2 = level1.getChildren().get(0);
        Assert.assertEquals("Observation.component", level2.getFhir()); // $fhirRoot resolves to parent's fullFhirPath
        Assert.assertEquals("Observation.component",
                            level2.getFullFhirPath()); // $fhirRoot resolves to parent's fullFhirPath
        Assert.assertEquals("openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]",
                            level2.getOpenEhr()); // $openehrRoot resolves to parent's fullOpenEhrPath
        Assert.assertEquals(1, level2.getChildren().size());

        // Level 3 validation
        MappingHelper level3 = level2.getChildren().get(0);
        Assert.assertEquals("Observation.component.value", level3.getFullFhirPath()); // Plain path gets concatenated
        Assert.assertEquals("value", level3.getFhir()); // Plain path gets concatenated
        Assert.assertEquals(
                "openEHR-EHR-OBSERVATION.blood_pressure.v2/data[at0001]/events[at0006]/data[at0003]/items[at0004]",
                level3.getFullOpenEhrPath());
    }

    /**
     * Test parent mapping with multiple children in followedBy.
     * Based on problem_list.yml sectionActiveProblems with multiple children.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-COMPOSITION.problem_list.v2
     * mappings:
     *   - name: "sectionActiveProblems"
     *     with:
     *       fhir: "$resource.section"
     *       openehr: "$archetype"
     *     followedBy:
     *       mappings:
     *         - name: "entry"
     *           with:
     *             fhir: "entry.as(Condition)"
     *             openehr: "$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]"
     *
     *         - name: "title"
     *           with:
     *             fhir: "$fhirRoot"
     *             openehr: "$archetype"
     *
     *         - name: "code"
     *           with:
     *             fhir: "code.coding"
     *             openehr: "$archetype"
     * </pre>
     * <p>
     * Expected result:
     * - 1 parent with 3 children
     * - Child 1: fullFhirPath="Composition.section.entry.as(Condition)" (plain path concatenated)
     * - Child 2: fhir="Composition.section" ($fhirRoot → parent's fullFhirPath)
     * - Child 3: fullFhirPath="Composition.section.code.coding" (plain path concatenated)
     */
    @Test
    public void createHelpers_multipleChildrenInFollowedBy_createsAllChildren() {
        FhirConnectModel context = createFcModel("problem_list", "openEHR-EHR-COMPOSITION.problem_list.v2");

        Mapping parentMapping = new Mapping();
        parentMapping.setName("sectionActiveProblems");
        With parentWith = new With();
        parentWith.setFhir("$resource.section");
        parentWith.setOpenehr("$archetype");
        parentMapping.setWith(parentWith);

        // Child 1: entry
        Mapping child1 = new Mapping();
        child1.setName("entry");
        With with1 = new With();
        with1.setFhir("entry.as(Condition)");
        with1.setOpenehr("$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]");
        child1.setWith(with1);

        // Child 2: title
        Mapping child2 = new Mapping();
        child2.setName("title");
        With with2 = new With();
        with2.setFhir("$fhirRoot");
        with2.setOpenehr("$archetype");
        child2.setWith(with2);

        // Child 3: code
        Mapping child3 = new Mapping();
        child3.setName("code");
        With with3 = new With();
        with3.setFhir("code.coding");
        with3.setOpenehr("$archetype");
        child3.setWith(with3);

        FollowedBy followedBy = new FollowedBy();
        followedBy.setMappings(java.util.Arrays.asList(child1, child2, child3));
        parentMapping.setFollowedBy(followedBy);

        List<Mapping> mappings = java.util.Arrays.asList(parentMapping);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", mappings, null, null);

        Assert.assertEquals(1, result.size());

        MappingHelper parent = result.get(0);
        Assert.assertEquals(3, parent.getChildren().size());

        Assert.assertEquals("Composition.section.entry.as(Condition)", parent.getChildren().get(0).getFullFhirPath());
        Assert.assertEquals("Composition.section", parent.getChildren().get(1).getFhir());
        Assert.assertEquals("Composition.section.code.coding", parent.getChildren().get(2).getFullFhirPath());
    }

    /**
     * Test that $composition constant is preserved and not replaced.
     * Based on problem_list.yml entry mapping.
     *
     * <pre>
     * mappings:
     *   - name: "entry"
     *     with:
     *       fhir: "entry.as(Condition)"
     *       openehr: "$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]"
     * </pre>
     * <p>
     * Expected result:
     * - openehr path should keep $composition constant unchanged
     * - $composition is never replaced (it refers to the composition root at runtime)
     */
    @Test
    public void createHelpers_withCompositionConstant_preservesIt() {
        FhirConnectModel context = createFcModel("problem_list", "openEHR-EHR-COMPOSITION.problem_list.v2");

        Mapping mapping = new Mapping();
        mapping.setName("entry");
        With with = new With();
        with.setFhir("$resource.entry.as(Condition)");
        with.setOpenehr("$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]");
        mapping.setWith(with);

        List<Mapping> mappings = java.util.Arrays.asList(mapping);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", mappings, null, null);

        Assert.assertEquals(1, result.size());
        // $composition should be left as-is
        Assert.assertEquals("$composition/content[openEHR-EHR-EVALUATION.problem_diagnosis.v1]",
                            result.get(0).getOpenEhr());
    }

    /**
     * Complex real-world example with 2 top-level mappings and deep nesting.
     * Based on blood_pressure_nhealth.yml structure.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-OBSERVATION.blood_pressure.v2
     * mappings:
     *   - name: dateTime
     *     with:
     *       fhir: "$resource.effective"
     *       openehr: "$archetype/data[at0001]/events[at0006]"
     *
     *   - name: componentParent
     *     with:
     *       fhir: "$resource.component"
     *       openehr: "$archetype/data[at0001]/events[at0006]"
     *     followedBy:
     *       mappings:
     *         - name: systolicParent
     *           with:
     *             fhir: "$fhirRoot"
     *             openehr: "$openehrRoot"
     *           followedBy:
     *             mappings:
     *               - name: systolic
     *                 with:
     *                   fhir: "value"
     *                   openehr: "data[at0003]/items[at0004]"
     * </pre>
     * <p>
     * Expected result:
     * - 2 top-level helpers (dateTime and componentParent)
     * - componentParent has nested structure with 3 levels total
     * - Proper path resolution at each level using $resource, $fhirRoot, $openehrRoot
     */
    @Test
    public void createHelpers_complexRealWorldExample_bloodPressure() {
        FhirConnectModel context = createFcModel("blood_pressure", "openEHR-EHR-OBSERVATION.blood_pressure.v2");

        List<Mapping> topLevelMappings = new ArrayList<>();

        // Mapping 1: dateTime
        Mapping dateTime = new Mapping();
        dateTime.setName("dateTime");
        With dateTimeWith = new With();
        dateTimeWith.setFhir("$resource.effective");
        dateTimeWith.setOpenehr("$archetype/data[at0001]/events[at0006]");
        dateTime.setWith(dateTimeWith);
        topLevelMappings.add(dateTime);

        // Mapping 2: componentParent with nested structure
        Mapping componentParent = new Mapping();
        componentParent.setName("componentParent");
        With componentWith = new With();
        componentWith.setFhir("$resource.component");
        componentWith.setOpenehr("$archetype/data[at0001]/events[at0006]");
        componentParent.setWith(componentWith);

        // Nested: systolic and diastolic
        Mapping systolicParent = new Mapping();
        systolicParent.setName("systolicParent");
        With systolicParentWith = new With();
        systolicParentWith.setFhir("$fhirRoot");
        systolicParentWith.setOpenehr("$openehrRoot");
        systolicParent.setWith(systolicParentWith);

        Mapping systolic = new Mapping();
        systolic.setName("systolic");
        With systolicWith = new With();
        systolicWith.setFhir("value");
        systolicWith.setOpenehr("data[at0003]/items[at0004]");
        systolic.setWith(systolicWith);

        FollowedBy systolicFollowedBy = new FollowedBy();
        systolicFollowedBy.setMappings(java.util.Arrays.asList(systolic));
        systolicParent.setFollowedBy(systolicFollowedBy);

        FollowedBy componentFollowedBy = new FollowedBy();
        componentFollowedBy.setMappings(java.util.Arrays.asList(systolicParent));
        componentParent.setFollowedBy(componentFollowedBy);

        topLevelMappings.add(componentParent);

        List<MappingHelper> result = helpersCreator.createHelpers(context, "Observation", topLevelMappings, null, null);

        // Should have 2 top-level helpers
        Assert.assertEquals(2, result.size());

        // First is dateTime
        Assert.assertEquals("effective", result.get(0).getFhir());
        Assert.assertEquals("Observation.effective", result.get(0).getFullFhirPath());

        // Second is componentParent with nested structure
        Assert.assertEquals("component", result.get(1).getFhir());
        Assert.assertEquals("Observation.component", result.get(1).getFullFhirPath());
        Assert.assertEquals(1, result.get(1).getChildren().size());

        MappingHelper systolicParentHelper = result.get(1).getChildren().get(0);
        Assert.assertEquals(1, systolicParentHelper.getChildren().size());

        MappingHelper systolicHelper = systolicParentHelper.getChildren().get(0);
        Assert.assertEquals("Observation.component.value", systolicHelper.getFullFhirPath());
    }

    /**
     * Extensive test with deeply nested mappings using all constants at various levels.
     * Tests $resource, $fhirRoot, $composition, $archetype, and $openehrRoot throughout a complex hierarchy.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-COMPOSITION.encounter.v1
     *
     * mappings:
     *   # Level 1: Top-level with $resource and $composition
     *   - name: "contextTime"
     *     with:
     *       fhir: "$resource.period.start"
     *       openehr: "$composition/context/start_time"
     *
     *   # Level 1: Section with $archetype
     *   - name: "observationSection"
     *     with:
     *       fhir: "$resource.section"
     *       openehr: "$archetype"
     *     followedBy:
     *       mappings:
     *         # Level 2: Entry using $fhirRoot and $composition
     *         - name: "observationEntry"
     *           with:
     *             fhir: "$fhirRoot.entry.as(Observation)"
     *             openehr: "$composition/content[openEHR-EHR-OBSERVATION.vitals.v1]"
     *           followedBy:
     *             mappings:
     *               # Level 3: Component using $fhirRoot and $archetype
     *               - name: "componentContainer"
     *                 with:
     *                   fhir: "$fhirRoot.component"
     *                   openehr: "$archetype/data[at0001]/events[at0002]"
     *                 followedBy:
     *                   mappings:
     *                     # Level 4: Specific component using $fhirRoot and $openehrRoot
     *                     - name: "specificComponent"
     *                       with:
     *                         fhir: "$fhirRoot"
     *                         openehr: "$openehrRoot"
     *                       followedBy:
     *                         mappings:
     *                           # Level 5: Value using plain paths
     *                           - name: "componentValue"
     *                             with:
     *                               fhir: "value.quantity"
     *                               openehr: "data[at0003]/items[at0004]"
     *
     *                           # Level 5: Code using $fhirRoot and plain openEHR
     *                           - name: "componentCode"
     *                             with:
     *                               fhir: "$fhirRoot.code.coding.code"
     *                               openehr: "data[at0003]/items[at0005]|code"
     *
     *         # Level 2: Title using $fhirRoot and $archetype
     *         - name: "sectionTitle"
     *           with:
     *             fhir: "$fhirRoot.title"
     *             openehr: "$archetype/name|value"
     * </pre>
     * <p>
     * Expected path resolutions:
     * - Level 1 contextTime: fhir="Composition.period.start", openehr="$composition/context/start_time"
     * - Level 1 observationSection: fhir="Composition.section", openehr="openEHR-EHR-COMPOSITION.encounter.v1"
     * - Level 2 observationEntry: fhir="Composition.section.entry.as(Observation)",
     * openehr="$composition/content[...]"
     * - Level 3 componentContainer: fhir="Composition.section.entry.as(Observation).component",
     * openehr="openEHR-EHR-COMPOSITION.encounter.v1/data[at0001]/events[at0002]"
     * - Level 4 specificComponent: fhir="Composition.section.entry.as(Observation).component",
     * openehr="openEHR-EHR-COMPOSITION.encounter.v1/data[at0001]/events[at0002]"
     * - Level 5 componentValue: fullFhirPath="Composition.section.entry.as(Observation).component.value.quantity"
     * - Level 5 componentCode: fhir="Composition.section.entry.as(Observation).component.code.coding.code"
     * - Level 2 sectionTitle: fhir="Composition.section.title"
     */
    @Test
    public void createHelpers_deeplyNestedWithAllConstants_resolvesPathsCorrectly() {
        FhirConnectModel context = createFcModel("encounter", "openEHR-EHR-COMPOSITION.encounter.v1");
        List<Mapping> topLevelMappings = new ArrayList<>();

        // === Level 1: contextTime ===
        Mapping contextTime = new Mapping();
        contextTime.setName("contextTime");
        With contextTimeWith = new With();
        contextTimeWith.setFhir("$resource.period.start");
        contextTimeWith.setOpenehr("$composition/context/start_time");
        contextTime.setWith(contextTimeWith);
        topLevelMappings.add(contextTime);

        // === Level 1: observationSection ===
        Mapping observationSection = new Mapping();
        observationSection.setName("observationSection");
        With observationSectionWith = new With();
        observationSectionWith.setFhir("$resource.section");
        observationSectionWith.setOpenehr("$archetype");
        observationSection.setWith(observationSectionWith);

        // === Level 2: observationEntry ===
        Mapping observationEntry = new Mapping();
        observationEntry.setName("observationEntry");
        With observationEntryWith = new With();
        observationEntryWith.setFhir("$fhirRoot.entry.as(Observation)");
        observationEntryWith.setOpenehr("$composition/content[openEHR-EHR-OBSERVATION.vitals.v1]");
        observationEntry.setWith(observationEntryWith);

        // === Level 3: componentContainer ===
        Mapping componentContainer = new Mapping();
        componentContainer.setName("componentContainer");
        With componentContainerWith = new With();
        componentContainerWith.setFhir("$fhirRoot.component");
        componentContainerWith.setOpenehr("$archetype/data[at0001]/events[at0002]");
        componentContainer.setWith(componentContainerWith);

        // === Level 4: specificComponent ===
        Mapping specificComponent = new Mapping();
        specificComponent.setName("specificComponent");
        With specificComponentWith = new With();
        specificComponentWith.setFhir("$fhirRoot");
        specificComponentWith.setOpenehr("$openehrRoot");
        specificComponent.setWith(specificComponentWith);

        // === Level 5: componentValue ===
        Mapping componentValue = new Mapping();
        componentValue.setName("componentValue");
        With componentValueWith = new With();
        componentValueWith.setFhir("value.quantity");
        componentValueWith.setOpenehr("data[at0003]/items[at0004]");
        componentValue.setWith(componentValueWith);

        // === Level 5: componentCode ===
        Mapping componentCode = new Mapping();
        componentCode.setName("componentCode");
        With componentCodeWith = new With();
        componentCodeWith.setFhir("$fhirRoot.code.coding.code");
        componentCodeWith.setOpenehr("data[at0003]/items[at0005]|code");
        componentCode.setWith(componentCodeWith);

        // === Level 2: sectionTitle ===
        Mapping sectionTitle = new Mapping();
        sectionTitle.setName("sectionTitle");
        With sectionTitleWith = new With();
        sectionTitleWith.setFhir("$fhirRoot.title");
        sectionTitleWith.setOpenehr("$archetype/name|value");
        sectionTitle.setWith(sectionTitleWith);

        // Wire up the hierarchy
        FollowedBy level5FollowedBy = new FollowedBy();
        level5FollowedBy.setMappings(java.util.Arrays.asList(componentValue, componentCode));
        specificComponent.setFollowedBy(level5FollowedBy);

        FollowedBy level4FollowedBy = new FollowedBy();
        level4FollowedBy.setMappings(java.util.Arrays.asList(specificComponent));
        componentContainer.setFollowedBy(level4FollowedBy);

        FollowedBy level3FollowedBy = new FollowedBy();
        level3FollowedBy.setMappings(java.util.Arrays.asList(componentContainer));
        observationEntry.setFollowedBy(level3FollowedBy);

        FollowedBy level2FollowedBy = new FollowedBy();
        level2FollowedBy.setMappings(java.util.Arrays.asList(observationEntry, sectionTitle));
        observationSection.setFollowedBy(level2FollowedBy);

        topLevelMappings.add(observationSection);

        // Execute
        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", topLevelMappings, null, null);

        // === Assertions ===

        // Level 1: Should have 2 top-level helpers
        Assert.assertEquals(2, result.size());

        // Level 1.1: contextTime
        MappingHelper contextTimeHelper = result.get(0);
        Assert.assertEquals("period.start", contextTimeHelper.getFhir());
        Assert.assertEquals("Composition.period.start", contextTimeHelper.getFullFhirPath());
        Assert.assertEquals("$composition/context/start_time", contextTimeHelper.getOpenEhr());

        // Level 1.2: observationSection
        MappingHelper observationSectionHelper = result.get(1);
        Assert.assertEquals("section", observationSectionHelper.getFhir());
        Assert.assertEquals("Composition.section", observationSectionHelper.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.encounter.v1", observationSectionHelper.getOpenEhr());
        Assert.assertEquals(2, observationSectionHelper.getChildren().size());

        // Level 2.1: observationEntry (first child)
        MappingHelper observationEntryHelper = observationSectionHelper.getChildren().get(0);
        Assert.assertEquals("entry.as(Observation)", observationEntryHelper.getFhir());
        Assert.assertEquals("Composition.section.entry.as(Observation)", observationEntryHelper.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.vitals.v1]",
                            observationEntryHelper.getOpenEhr());
        Assert.assertEquals(1, observationEntryHelper.getChildren().size());

        // Level 3: componentContainer
        MappingHelper componentContainerHelper = observationEntryHelper.getChildren().get(0);
        Assert.assertEquals("component", componentContainerHelper.getFhir());
        Assert.assertEquals("Composition.section.entry.as(Observation).component",
                            componentContainerHelper.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.encounter.v1/data[at0001]/events[at0002]",
                            componentContainerHelper.getOpenEhr());
        Assert.assertEquals(1, componentContainerHelper.getChildren().size());

        // Level 4: specificComponent
        MappingHelper specificComponentHelper = componentContainerHelper.getChildren().get(0);
        Assert.assertEquals("Composition.section.entry.as(Observation).component", specificComponentHelper.getFhir());
        Assert.assertEquals("Composition.section.entry.as(Observation).component",
                            specificComponentHelper.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.encounter.v1/data[at0001]/events[at0002]",
                            specificComponentHelper.getOpenEhr());
        Assert.assertEquals(2, specificComponentHelper.getChildren().size());

        // Level 5.1: componentValue
        MappingHelper componentValueHelper = specificComponentHelper.getChildren().get(0);
        Assert.assertEquals("value.quantity", componentValueHelper.getFhir());
        Assert.assertEquals("Composition.section.entry.as(Observation).component.value.quantity",
                            componentValueHelper.getFullFhirPath());
        Assert.assertEquals(
                "openEHR-EHR-COMPOSITION.encounter.v1/data[at0001]/events[at0002]/data[at0003]/items[at0004]",
                componentValueHelper.getFullOpenEhrPath());

        // Level 5.2: componentCode
        MappingHelper componentCodeHelper = specificComponentHelper.getChildren().get(1);
        Assert.assertEquals("code.coding.code", componentCodeHelper.getFhir());
        Assert.assertEquals(
                "openEHR-EHR-COMPOSITION.encounter.v1/data[at0001]/events[at0002]/data[at0003]/items[at0005]|code",
                componentCodeHelper.getFullOpenEhrPath());

        // Level 2.2: sectionTitle (second child of observationSection)
        MappingHelper sectionTitleHelper = observationSectionHelper.getChildren().get(1);
        Assert.assertEquals("title", sectionTitleHelper.getFhir());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.encounter.v1/name|value", sectionTitleHelper.getOpenEhr());
    }

    /**
     * Test that conditions have their targetRoot paths amended correctly.
     * Based on blood_pressure_nhealth.yml position and systolicParent mappings.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-OBSERVATION.blood_pressure.v2
     *
     * mappings:
     *   # Mapping with $resource in fhirCondition.targetRoot
     *   - name: position
     *     with:
     *       fhir: "$resource.extension.value"
     *       openehr: "$archetype/data[at0001]/events[at0006]/state[at0007]/items[at0008]"
     *     fhirCondition:
     *       targetRoot: "$resource.extension"
     *       targetAttribute: "url"
     *       operator: one of
     *       criteria: "http://hl7.org/fhir/StructureDefinition/observation-bodyPosition"
     *
     *   - name: componentParent
     *     with:
     *       fhir: "$resource.component"
     *       openehr: "$archetype/data[at0001]/events[at0006]"
     *     followedBy:
     *       mappings:
     *         # Nested mapping with $fhirRoot in fhirCondition.targetRoot
     *         - name: systolicParent
     *           with:
     *             fhir: "$fhirRoot"
     *             openehr: "$openehrRoot"
     *           fhirCondition:
     *             targetRoot: "$fhirRoot"
     *             targetAttribute: "code.coding.code"
     *             operator: "one of"
     *             criteria: "8480-6"
     * </pre>
     * <p>
     * Expected result:
     * - position: fhirCondition.targetRoot = "Observation.extension" ($resource replaced)
     * - systolicParent: fhirCondition.targetRoot = "Observation.component" ($fhirRoot replaced with parent's
     * fullFhirPath)
     */
    @Test
    public void createHelpers_withConditions_amendsTargetRootPaths() {
        FhirConnectModel context = createFcModel("blood_pressure", "openEHR-EHR-OBSERVATION.blood_pressure.v2");
        List<Mapping> topLevelMappings = new ArrayList<>();

        // === Mapping 1: position with $resource in targetRoot ===
        Mapping position = new Mapping();
        position.setName("position");
        With positionWith = new With();
        positionWith.setFhir("$resource.extension.value");
        positionWith.setOpenehr("$archetype/data[at0001]/events[at0006]/state[at0007]/items[at0008]");
        position.setWith(positionWith);

        com.syntaric.openfhir.fc.schema.model.Condition positionCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        positionCondition.setTargetRoot("$resource.extension");
        positionCondition.setTargetAttribute("url");
        positionCondition.setOperator("one of");
        positionCondition.setCriteria("http://hl7.org/fhir/StructureDefinition/observation-bodyPosition");
        position.setFhirCondition(positionCondition);

        topLevelMappings.add(position);

        // === Mapping 2: componentParent ===
        Mapping componentParent = new Mapping();
        componentParent.setName("componentParent");
        With componentWith = new With();
        componentWith.setFhir("$resource.component");
        componentWith.setOpenehr("$archetype/data[at0001]/events[at0006]");
        componentParent.setWith(componentWith);

        // === Nested: systolicParent with $fhirRoot in targetRoot ===
        Mapping systolicParent = new Mapping();
        systolicParent.setName("systolicParent");
        With systolicWith = new With();
        systolicWith.setFhir("$fhirRoot");
        systolicWith.setOpenehr("$openehrRoot");
        systolicParent.setWith(systolicWith);

        com.syntaric.openfhir.fc.schema.model.Condition systolicCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        systolicCondition.setTargetRoot("$fhirRoot");
        systolicCondition.setTargetAttribute("code.coding.code");
        systolicCondition.setOperator("one of");
        systolicCondition.setCriteria("8480-6");
        systolicParent.setFhirCondition(systolicCondition);

        FollowedBy followedBy = new FollowedBy();
        followedBy.setMappings(java.util.Arrays.asList(systolicParent));
        componentParent.setFollowedBy(followedBy);

        topLevelMappings.add(componentParent);

        // Execute
        List<MappingHelper> result = helpersCreator.createHelpers(context, "Observation", topLevelMappings, null, null);

        // === Assertions ===

        Assert.assertEquals(2, result.size());

        // Mapping 1: position - verify $resource is replaced in condition targetRoot
        MappingHelper positionHelper = result.get(0);
        Assert.assertNotNull(positionHelper.getFhirConditions());
        Assert.assertEquals(1, positionHelper.getFhirConditions().size());
        Assert.assertEquals("Observation.extension", positionHelper.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("url", positionHelper.getFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("one of", positionHelper.getFhirConditions().get(0).getOperator());

        // Mapping 2: componentParent with nested systolicParent
        MappingHelper componentParentHelper = result.get(1);
        Assert.assertEquals(1, componentParentHelper.getChildren().size());

        // Nested: systolicParent - verify $fhirRoot is replaced in condition targetRoot
        MappingHelper systolicParentHelper = componentParentHelper.getChildren().get(0);
        Assert.assertNotNull(systolicParentHelper.getFhirConditions());
        Assert.assertEquals(1, systolicParentHelper.getFhirConditions().size());
        Assert.assertEquals("Observation.component", systolicParentHelper.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("code.coding.code", systolicParentHelper.getFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("8480-6", systolicParentHelper.getFhirConditions().get(0).getCriteria());
    }

    /**
     * Test OpenEHR conditions with $archetype and $openehrRoot in targetRoot.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-COMPOSITION.encounter.v1
     *
     * mappings:
     *   - name: topLevelWithArchetype
     *     with:
     *       fhir: "$resource.category"
     *       openehr: "$archetype/category"
     *     openehrCondition:
     *       targetRoot: "$archetype/category"
     *       targetAttribute: "value"
     *       operator: "one of"
     *       criteria: "event"
     *
     *   - name: sectionParent
     *     with:
     *       fhir: "$resource.section"
     *       openehr: "$archetype/content"
     *     followedBy:
     *       mappings:
     *         - name: nestedWithOpenEhrRoot
     *           with:
     *             fhir: "entry"
     *             openehr: "$openehrRoot/items[at0001]"
     *           openehrCondition:
     *             targetRoot: "$openehrRoot/items"
     *             targetAttribute: "name|value"
     *             operator: "one of"
     *             criteria: "vital-signs"
     * </pre>
     * <p>
     * Expected result:
     * - topLevelWithArchetype: openehrCondition.targetRoot = "openEHR-EHR-COMPOSITION.encounter.v1/category"
     * - nestedWithOpenEhrRoot: openehrCondition.targetRoot = "openEHR-EHR-COMPOSITION.encounter.v1/content/items"
     */
    @Test
    public void createHelpers_withOpenEhrConditions_amendsArchetypeAndRoot() {
        FhirConnectModel context = createFcModel("encounter", "openEHR-EHR-COMPOSITION.encounter.v1");
        List<Mapping> topLevelMappings = new ArrayList<>();

        // === Top-level mapping with $archetype in openehrCondition ===
        Mapping topLevel = new Mapping();
        topLevel.setName("topLevelWithArchetype");
        With topLevelWith = new With();
        topLevelWith.setFhir("$resource.category");
        topLevelWith.setOpenehr("$archetype/category");
        topLevel.setWith(topLevelWith);

        com.syntaric.openfhir.fc.schema.model.Condition topLevelCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        topLevelCondition.setTargetRoot("$archetype/category");
        topLevelCondition.setTargetAttribute("value");
        topLevelCondition.setOperator("one of");
        topLevelCondition.setCriteria("event");
        topLevel.setOpenehrCondition(topLevelCondition);

        topLevelMappings.add(topLevel);

        // === Parent mapping ===
        Mapping sectionParent = new Mapping();
        sectionParent.setName("sectionParent");
        With sectionWith = new With();
        sectionWith.setFhir("$resource.section");
        sectionWith.setOpenehr("$archetype/content");
        sectionParent.setWith(sectionWith);

        // === Nested mapping with $openehrRoot in openehrCondition ===
        Mapping nested = new Mapping();
        nested.setName("nestedWithOpenEhrRoot");
        With nestedWith = new With();
        nestedWith.setFhir("entry");
        nestedWith.setOpenehr("$openehrRoot/items[at0001]");
        nested.setWith(nestedWith);

        com.syntaric.openfhir.fc.schema.model.Condition nestedCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        nestedCondition.setTargetRoot("$openehrRoot/items");
        nestedCondition.setTargetAttribute("name|value");
        nestedCondition.setOperator("one of");
        nestedCondition.setCriteria("vital-signs");
        nested.setOpenehrCondition(nestedCondition);

        FollowedBy followedBy = new FollowedBy();
        followedBy.setMappings(java.util.Arrays.asList(nested));
        sectionParent.setFollowedBy(followedBy);

        topLevelMappings.add(sectionParent);

        // Execute
        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", topLevelMappings, null, null);

        // === Assertions ===

        Assert.assertEquals(2, result.size());

        // Top-level: verify $archetype is replaced in openehrCondition
        MappingHelper topLevelHelper = result.get(0);
        Assert.assertNotNull(topLevelHelper.getOpenEhrConditions());
        Assert.assertEquals(1, topLevelHelper.getOpenEhrConditions().size());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.encounter.v1/category",
                            topLevelHelper.getOpenEhrConditions().get(0).getTargetRoot());
        Assert.assertEquals("value", topLevelHelper.getOpenEhrConditions().get(0).getTargetAttribute());

        // Nested: verify $openehrRoot is replaced in openehrCondition
        MappingHelper sectionParentHelper = result.get(1);
        MappingHelper nestedHelper = sectionParentHelper.getChildren().get(0);
        Assert.assertNotNull(nestedHelper.getOpenEhrConditions());
        Assert.assertEquals(1, nestedHelper.getOpenEhrConditions().size());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.encounter.v1/content/items",
                            nestedHelper.getOpenEhrConditions().get(0).getTargetRoot());
        Assert.assertEquals("name|value", nestedHelper.getOpenEhrConditions().get(0).getTargetAttribute());
    }

    /**
     * Test deeply nested conditions with mixed FHIR and OpenEHR conditions at different levels.
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-OBSERVATION.vital_signs.v1
     *
     * mappings:
     *   - name: level1
     *     with:
     *       fhir: "$resource.component"
     *       openehr: "$archetype/data[at0001]"
     *     fhirCondition:
     *       targetRoot: "$resource"
     *       targetAttribute: "status"
     *       operator: "one of"
     *       criteria: "final"
     *     followedBy:
     *       mappings:
     *         - name: level2
     *           with:
     *             fhir: "$fhirRoot"
     *             openehr: "$openehrRoot/events[at0002]"
     *           openehrCondition:
     *             targetRoot: "$openehrRoot/events"
     *             targetAttribute: "time"
     *             operator: "not empty"
     *           followedBy:
     *             mappings:
     *               - name: level3
     *                 with:
     *                   fhir: "value"
     *                   openehr: "data[at0003]/items[at0004]"
     *                 fhirCondition:
     *                   targetRoot: "$fhirRoot.code"
     *                   targetAttribute: "coding.code"
     *                   operator: "one of"
     *                   criteria: "8310-5"
     *                 openehrCondition:
     *                   targetRoot: "$openehrRoot"
     *                   targetAttribute: "name|value"
     *                   operator: "one of"
     *                   criteria: "Body temperature"
     * </pre>
     * <p>
     * Expected result:
     * - Level 1 fhirCondition.targetRoot: "Observation" ($resource → ResourceType)
     * - Level 2 openehrCondition.targetRoot: "openEHR-EHR-OBSERVATION.vital_signs.v1/data[at0001]/events"
     * - Level 3 fhirCondition.targetRoot: "Observation.component.code"
     * - Level 3 openehrCondition.targetRoot: "openEHR-EHR-OBSERVATION.vital_signs.v1/data[at0001]/events[at0002]"
     */
    @Test
    public void createHelpers_deeplyNestedConditions_amendsAllLevels() {
        FhirConnectModel context = createFcModel("vital_signs", "openEHR-EHR-OBSERVATION.vital_signs.v1");
        List<Mapping> topLevelMappings = new ArrayList<>();

        // === Level 1 ===
        Mapping level1 = new Mapping();
        level1.setName("level1");
        With level1With = new With();
        level1With.setFhir("$resource.component");
        level1With.setOpenehr("$archetype/data[at0001]");
        level1.setWith(level1With);

        com.syntaric.openfhir.fc.schema.model.Condition level1FhirCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        level1FhirCondition.setTargetRoot("$resource");
        level1FhirCondition.setTargetAttribute("status");
        level1FhirCondition.setOperator("one of");
        level1FhirCondition.setCriteria("final");
        level1.setFhirCondition(level1FhirCondition);

        // === Level 2 ===
        Mapping level2 = new Mapping();
        level2.setName("level2");
        With level2With = new With();
        level2With.setFhir("$fhirRoot");
        level2With.setOpenehr("$openehrRoot/events[at0002]");
        level2.setWith(level2With);

        com.syntaric.openfhir.fc.schema.model.Condition level2OpenEhrCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        level2OpenEhrCondition.setTargetRoot("$openehrRoot/events");
        level2OpenEhrCondition.setTargetAttribute("time");
        level2OpenEhrCondition.setOperator("not empty");
        level2.setOpenehrCondition(level2OpenEhrCondition);

        // === Level 3 ===
        Mapping level3 = new Mapping();
        level3.setName("level3");
        With level3With = new With();
        level3With.setFhir("value");
        level3With.setOpenehr("data[at0003]/items[at0004]");
        level3.setWith(level3With);

        com.syntaric.openfhir.fc.schema.model.Condition level3FhirCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        level3FhirCondition.setTargetRoot("$fhirRoot.code");
        level3FhirCondition.setTargetAttribute("coding.code");
        level3FhirCondition.setOperator("one of");
        level3FhirCondition.setCriteria("8310-5");
        level3.setFhirCondition(level3FhirCondition);

        com.syntaric.openfhir.fc.schema.model.Condition level3OpenEhrCondition = new com.syntaric.openfhir.fc.schema.model.Condition();
        level3OpenEhrCondition.setTargetRoot("$openehrRoot");
        level3OpenEhrCondition.setTargetAttribute("name|value");
        level3OpenEhrCondition.setOperator("one of");
        level3OpenEhrCondition.setCriteria("Body temperature");
        level3.setOpenehrCondition(level3OpenEhrCondition);

        // Wire up hierarchy
        FollowedBy level3FollowedBy = new FollowedBy();
        level3FollowedBy.setMappings(java.util.Arrays.asList(level3));
        level2.setFollowedBy(level3FollowedBy);

        FollowedBy level2FollowedBy = new FollowedBy();
        level2FollowedBy.setMappings(java.util.Arrays.asList(level2));
        level1.setFollowedBy(level2FollowedBy);

        topLevelMappings.add(level1);

        // Execute
        List<MappingHelper> result = helpersCreator.createHelpers(context, "Observation", topLevelMappings, null, null);

        // === Assertions ===

        Assert.assertEquals(1, result.size());

        // Level 1: $resource in fhirCondition
        MappingHelper level1Helper = result.get(0);
        Assert.assertNotNull(level1Helper.getFhirConditions());
        Assert.assertEquals("Observation", level1Helper.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("status", level1Helper.getFhirConditions().get(0).getTargetAttribute());

        // Level 2: $openehrRoot in openehrCondition
        MappingHelper level2Helper = level1Helper.getChildren().get(0);
        Assert.assertNotNull(level2Helper.getOpenEhrConditions());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.vital_signs.v1/data[at0001]/events",
                            level2Helper.getOpenEhrConditions().get(0).getTargetRoot());
        Assert.assertEquals("time", level2Helper.getOpenEhrConditions().get(0).getTargetAttribute());

        // Level 3: both fhirCondition and openehrCondition
        MappingHelper level3Helper = level2Helper.getChildren().get(0);

        // Level 3 FHIR condition with $fhirRoot.code
        Assert.assertNotNull(level3Helper.getFhirConditions());
        Assert.assertEquals("Observation.component.code", level3Helper.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("coding.code", level3Helper.getFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("8310-5", level3Helper.getFhirConditions().get(0).getCriteria());

        // Level 3 OpenEHR condition with $openehrRoot
        Assert.assertNotNull(level3Helper.getOpenEhrConditions());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.vital_signs.v1/data[at0001]/events[at0002]",
                            level3Helper.getOpenEhrConditions().get(0).getTargetRoot());
        Assert.assertEquals("name|value", level3Helper.getOpenEhrConditions().get(0).getTargetAttribute());
    }

    /**
     * Test conditions with $composition constant (should be preserved).
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-COMPOSITION.report.v1
     *
     * mappings:
     *   - name: sectionEntry
     *     with:
     *       fhir: "$resource.section.entry"
     *       openehr: "$composition/content[openEHR-EHR-OBSERVATION.lab_test.v1]"
     *     openehrCondition:
     *       targetRoot: "$composition/content"
     *       targetAttribute: "archetype_node_id"
     *       operator: "one of"
     *       criteria: "openEHR-EHR-OBSERVATION.lab_test.v1"
     * </pre>
     * <p>
     * Expected result:
     * - openehrCondition.targetRoot should remain "$composition/content" (not replaced)
     */
    @Test
    public void createHelpers_withCompositionInCondition_preservesConstant() {
        FhirConnectModel context = createFcModel("report", "openEHR-EHR-COMPOSITION.report.v1");

        Mapping mapping = new Mapping();
        mapping.setName("sectionEntry");
        With with = new With();
        with.setFhir("$resource.section.entry");
        with.setOpenehr("$composition/content[openEHR-EHR-OBSERVATION.lab_test.v1]");
        mapping.setWith(with);

        com.syntaric.openfhir.fc.schema.model.Condition condition = new com.syntaric.openfhir.fc.schema.model.Condition();
        condition.setTargetRoot("$composition/content");
        condition.setTargetAttribute("archetype_node_id");
        condition.setOperator("one of");
        condition.setCriteria("openEHR-EHR-OBSERVATION.lab_test.v1");
        mapping.setOpenehrCondition(condition);

        List<Mapping> mappings = java.util.Arrays.asList(mapping);

        // Execute
        List<MappingHelper> result = helpersCreator.createHelpers(context, "Composition", mappings, null, null);

        // Assert: $composition should be preserved in condition targetRoot
        Assert.assertEquals(1, result.size());
        MappingHelper helper = result.get(0);
        Assert.assertNotNull(helper.getOpenEhrConditions());
        Assert.assertEquals("$composition/content", helper.getOpenEhrConditions().get(0).getTargetRoot());
        Assert.assertEquals("archetype_node_id", helper.getOpenEhrConditions().get(0).getTargetAttribute());
    }

    /**
     * Test multiple conditions at same level (sibling mappings with different conditions).
     *
     * <pre>
     * spec:
     *   openEhrConfig:
     *     archetype: openEHR-EHR-OBSERVATION.lab_test.v1
     *
     * mappings:
     *   - name: sibling1
     *     with:
     *       fhir: "$resource.component"
     *       openehr: "$archetype/data[at0001]/events[at0002]"
     *     fhirCondition:
     *       targetRoot: "$resource.component"
     *       targetAttribute: "code.coding.code"
     *       operator: "one of"
     *       criteria: "789-8"
     *
     *   - name: sibling2
     *     with:
     *       fhir: "$resource.component"
     *       openehr: "$archetype/data[at0001]/events[at0003]"
     *     fhirCondition:
     *       targetRoot: "$resource.component"
     *       targetAttribute: "code.coding.code"
     *       operator: "one of"
     *       criteria: "718-7"
     * </pre>
     * <p>
     * Expected result:
     * - Both siblings should have properly amended conditions
     * - Both should have targetRoot = "Observation.component"
     */
    @Test
    public void createHelpers_siblingMappingsWithConditions_amendsAllCorrectly() {
        FhirConnectModel context = createFcModel("lab_test", "openEHR-EHR-OBSERVATION.lab_test.v1");
        List<Mapping> mappings = new ArrayList<>();

        // Sibling 1
        Mapping sibling1 = new Mapping();
        sibling1.setName("sibling1");
        With with1 = new With();
        with1.setFhir("$resource.component");
        with1.setOpenehr("$archetype/data[at0001]/events[at0002]");
        sibling1.setWith(with1);

        com.syntaric.openfhir.fc.schema.model.Condition condition1 = new com.syntaric.openfhir.fc.schema.model.Condition();
        condition1.setTargetRoot("$resource.component");
        condition1.setTargetAttribute("code.coding.code");
        condition1.setOperator("one of");
        condition1.setCriteria("789-8");
        sibling1.setFhirCondition(condition1);

        // Sibling 2
        Mapping sibling2 = new Mapping();
        sibling2.setName("sibling2");
        With with2 = new With();
        with2.setFhir("$resource.component");
        with2.setOpenehr("$archetype/data[at0001]/events[at0003]");
        sibling2.setWith(with2);

        com.syntaric.openfhir.fc.schema.model.Condition condition2 = new com.syntaric.openfhir.fc.schema.model.Condition();
        condition2.setTargetRoot("$resource.component");
        condition2.setTargetAttribute("code.coding.code");
        condition2.setOperator("one of");
        condition2.setCriteria("718-7");
        sibling2.setFhirCondition(condition2);

        mappings.add(sibling1);
        mappings.add(sibling2);

        // Execute
        List<MappingHelper> result = helpersCreator.createHelpers(context, "Observation", mappings, null, null);

        // Assert both siblings have properly amended conditions
        Assert.assertEquals(2, result.size());

        MappingHelper helper1 = result.get(0);
        Assert.assertEquals("Observation.component", helper1.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("789-8", helper1.getFhirConditions().get(0).getCriteria());

        MappingHelper helper2 = result.get(1);
        Assert.assertEquals("Observation.component", helper2.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("718-7", helper2.getFhirConditions().get(0).getCriteria());
    }
}