package com.syntaric.openfhir.mapping.helpers;

import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

public class GrowthChartHelpersTest extends GenericHelpersTest {

    final String MODEL_MAPPINGS = "/growth_chart/";
    final String CONTEXT_MAPPING = "/growth_chart/growth-chart.context.yml";
    final String HELPER_LOCATION = "/growth_chart/";
    final String OPT = "Growth chart.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
    }

    @Test
    public void assertGrowthChartHelpers() {
        final String templateId = context.getContext().getTemplate().getId();
        final String start = context.getContext().getStart();

        final Map<String, List<MappingHelper>> allHelpersPerArchetype = helpersCreator.constructHelpers(templateId, start,
                                                                                                        context.getContext().getArchetypes(),
                                                                                                        webTemplate);

        final List<MappingHelper> compositionHelpers = allHelpersPerArchetype.get("openEHR-EHR-COMPOSITION.growth_chart.v0");
        Assert.assertEquals(4, compositionHelpers.size());

        // =====================================================================
        // heightParent
        // =====================================================================
        final MappingHelper heightParent = compositionHelpers.get(0);
        Assert.assertEquals("heightParent", heightParent.getMappingName());
        Assert.assertEquals("Bundle", heightParent.getGeneratingResourceType());
        Assert.assertEquals("$resource.entry", heightParent.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0002]/events[at0003]", heightParent.getOriginalOpenEhrPath());
        Assert.assertEquals("entry", heightParent.getFhir());
        Assert.assertEquals("Bundle.entry", heightParent.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0002]/events[at0003]", heightParent.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0002]/events[at0003]", heightParent.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length/any_event[n]", heightParent.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/height_length(:\\d+)?/any_event(:\\d+)?(\\|.*)?", heightParent.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("", heightParent.getFlatPathPipeSuffix());
        Assert.assertEquals("EVENT", heightParent.getDetectedType());
        Assert.assertFalse(heightParent.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(heightParent.isUseParentRoot());
        Assert.assertFalse(heightParent.isHasSlot());
        Assert.assertEquals(1, heightParent.getChildren().size());

        // iterateHeight
        final MappingHelper iterateHeight = heightParent.getChildren().get(0);
        Assert.assertEquals("iterateHeight", iterateHeight.getMappingName());
        Assert.assertEquals("Bundle", iterateHeight.getGeneratingResourceType());
        Assert.assertEquals("Observation", iterateHeight.getResolveResourceType());
        Assert.assertEquals("resource", iterateHeight.getOriginalFhirPath());
        Assert.assertEquals("$reference", iterateHeight.getOriginalOpenEhrPath());
        Assert.assertEquals("resource.as(Reference).resolve()", iterateHeight.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", iterateHeight.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.growth_chart.v0", iterateHeight.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0002]/events[at0003]", iterateHeight.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length/any_event[n]", iterateHeight.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/height_length(:\\d+)?/any_event(:\\d+)?(\\|.*)?", iterateHeight.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("", iterateHeight.getFlatPathPipeSuffix());
        Assert.assertEquals("EVENT", iterateHeight.getDetectedType());
        Assert.assertFalse(iterateHeight.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(iterateHeight.isUseParentRoot());
        Assert.assertFalse(iterateHeight.isHasSlot());
        Assert.assertEquals(1, iterateHeight.getChildren().size());

        // heightSlot
        final MappingHelper heightSlot = iterateHeight.getChildren().get(0);
        Assert.assertEquals("heightSlot", heightSlot.getMappingName());
        Assert.assertEquals("Observation", heightSlot.getGeneratingResourceType());
        Assert.assertEquals("$fhirRoot", heightSlot.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]", heightSlot.getOriginalOpenEhrPath());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", heightSlot.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", heightSlot.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]", heightSlot.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]", heightSlot.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length", heightSlot.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/height_length(:\\d+)?(\\|.*)?", heightSlot.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("", heightSlot.getFlatPathPipeSuffix());
        Assert.assertEquals("OBSERVATION", heightSlot.getDetectedType());
        Assert.assertFalse(heightSlot.isEnteredFromSlotArchetypeLink());
        Assert.assertTrue(heightSlot.isUseParentRoot());
        Assert.assertTrue(heightSlot.isHasSlot());
        Assert.assertEquals(2, heightSlot.getChildren().size());

        // height
        final MappingHelper height = heightSlot.getChildren().get(0);
        Assert.assertEquals("height", height.getMappingName());
        Assert.assertEquals("Observation", height.getGeneratingResourceType());
        Assert.assertEquals("$resource.value", height.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0001]/events[at0002]/data[at0003]/items[at0004]", height.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length/any_event[n]", height.getOpenEhrHierarchySplitFlatPath());
        Assert.assertNotNull(height.getPreprocessorFhirConditions());
        Assert.assertEquals(1, height.getPreprocessorFhirConditions().size());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", height.getPreprocessorFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("category.coding.code", height.getPreprocessorFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("one of", height.getPreprocessorFhirConditions().get(0).getOperator());
        Assert.assertEquals("height", height.getPreprocessorFhirConditions().get(0).getCriteria());
        Assert.assertEquals("value", height.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", height.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.height.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]", height.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]", height.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length/any_event[n]/height_length", height.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/height_length(:\\d+)?/any_event(:\\d+)?/height_length(:\\d+)?(\\|.*)?", height.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("", height.getFlatPathPipeSuffix());
        Assert.assertEquals("DV_QUANTITY", height.getDetectedType());
        Assert.assertTrue(height.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(height.isUseParentRoot());
        Assert.assertFalse(height.isHasSlot());
        Assert.assertEquals(0, height.getChildren().size());

        // code (under heightSlot)
        final MappingHelper heightCode = heightSlot.getChildren().get(1);
        Assert.assertEquals("code", heightCode.getMappingName());
        Assert.assertEquals("Observation", heightCode.getGeneratingResourceType());
        Assert.assertEquals("$resource.code.coding", heightCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", heightCode.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length/any_event[n]", heightCode.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("code.coding", heightCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding", heightCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.height.v2", heightCode.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.height.v2]", heightCode.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/height_length", heightCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/height_length(:\\d+)?(\\|.*)?", heightCode.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("OBSERVATION", heightCode.getDetectedType());
        Assert.assertEquals("openehr->fhir", heightCode.getUnidirectional());
        Assert.assertTrue(heightCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(heightCode.isUseParentRoot());
        Assert.assertFalse(heightCode.isHasSlot());
        Assert.assertEquals(1, heightCode.getChildren().size());

        // code.code (under heightCode)
        final MappingHelper heightCodeCode = heightCode.getChildren().get(0);
        Assert.assertEquals("code.code", heightCodeCode.getMappingName());
        Assert.assertEquals("Observation", heightCodeCode.getGeneratingResourceType());
        Assert.assertEquals("code", heightCodeCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", heightCodeCode.getOriginalOpenEhrPath());
        Assert.assertEquals("code", heightCodeCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding.code", heightCodeCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.height.v2", heightCodeCode.getOpenEhr());
        Assert.assertEquals("growth_chart/height_length", heightCodeCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", heightCodeCode.getDetectedType());
        Assert.assertEquals("8302-2", heightCodeCode.getManualFhirValue());
        Assert.assertEquals("openehr->fhir", heightCodeCode.getUnidirectional());
        Assert.assertTrue(heightCodeCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(heightCodeCode.isUseParentRoot());
        Assert.assertFalse(heightCodeCode.isHasSlot());
        Assert.assertEquals(0, heightCodeCode.getChildren().size());

        // =====================================================================
        // weightParent
        // =====================================================================
        final MappingHelper weightParent = compositionHelpers.get(1);
        Assert.assertEquals("weightParent", weightParent.getMappingName());
        Assert.assertEquals("Bundle", weightParent.getGeneratingResourceType());
        Assert.assertEquals("$resource.entry", weightParent.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]", weightParent.getOriginalOpenEhrPath());
        Assert.assertEquals("entry", weightParent.getFhir());
        Assert.assertEquals("Bundle.entry", weightParent.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]", weightParent.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]", weightParent.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", weightParent.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?/any_event(:\\d+)?(\\|.*)?", weightParent.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("", weightParent.getFlatPathPipeSuffix());
        Assert.assertEquals("EVENT", weightParent.getDetectedType());
        Assert.assertFalse(weightParent.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(weightParent.isUseParentRoot());
        Assert.assertFalse(weightParent.isHasSlot());
        Assert.assertEquals(1, weightParent.getChildren().size());

        // iterateWeight
        final MappingHelper iterateWeight = weightParent.getChildren().get(0);
        Assert.assertEquals("iterateWeight", iterateWeight.getMappingName());
        Assert.assertEquals("Bundle", iterateWeight.getGeneratingResourceType());
        Assert.assertEquals("Observation", iterateWeight.getResolveResourceType());
        Assert.assertEquals("resource", iterateWeight.getOriginalFhirPath());
        Assert.assertEquals("$reference", iterateWeight.getOriginalOpenEhrPath());
        Assert.assertEquals("resource.as(Reference).resolve()", iterateWeight.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", iterateWeight.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.growth_chart.v0", iterateWeight.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]", iterateWeight.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", iterateWeight.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?/any_event(:\\d+)?(\\|.*)?", iterateWeight.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("EVENT", iterateWeight.getDetectedType());
        Assert.assertFalse(iterateWeight.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(iterateWeight.isUseParentRoot());
        Assert.assertFalse(iterateWeight.isHasSlot());
        Assert.assertEquals(1, iterateWeight.getChildren().size());

        // weightSlot
        final MappingHelper weightSlot = iterateWeight.getChildren().get(0);
        Assert.assertEquals("weightSlot", weightSlot.getMappingName());
        Assert.assertEquals("Observation", weightSlot.getGeneratingResourceType());
        Assert.assertEquals("$fhirRoot", weightSlot.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]", weightSlot.getOriginalOpenEhrPath());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", weightSlot.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", weightSlot.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]", weightSlot.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]", weightSlot.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight", weightSlot.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?(\\|.*)?", weightSlot.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("OBSERVATION", weightSlot.getDetectedType());
        Assert.assertFalse(weightSlot.isEnteredFromSlotArchetypeLink());
        Assert.assertTrue(weightSlot.isUseParentRoot());
        Assert.assertTrue(weightSlot.isHasSlot());
        Assert.assertEquals(5, weightSlot.getChildren().size());

        // weight
        final MappingHelper weight = weightSlot.getChildren().get(0);
        Assert.assertEquals("weight", weight.getMappingName());
        Assert.assertEquals("Observation", weight.getGeneratingResourceType());
        Assert.assertEquals("$resource.value", weight.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0004]", weight.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", weight.getOpenEhrHierarchySplitFlatPath());
        Assert.assertNotNull(weight.getPreprocessorFhirConditions());
        Assert.assertEquals(1, weight.getPreprocessorFhirConditions().size());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", weight.getPreprocessorFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("category.coding.code", weight.getPreprocessorFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("one of", weight.getPreprocessorFhirConditions().get(0).getOperator());
        Assert.assertEquals("weight", weight.getPreprocessorFhirConditions().get(0).getCriteria());
        Assert.assertEquals("value", weight.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", weight.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0004]", weight.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0004]", weight.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]/weight", weight.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?/any_event(:\\d+)?/weight(:\\d+)?(\\|.*)?", weight.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_QUANTITY", weight.getDetectedType());
        Assert.assertTrue(weight.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(weight.isUseParentRoot());
        Assert.assertFalse(weight.isHasSlot());
        Assert.assertEquals(0, weight.getChildren().size());

        // time (weight)
        final MappingHelper weightTime = weightSlot.getChildren().get(1);
        Assert.assertEquals("time", weightTime.getMappingName());
        Assert.assertEquals("Observation", weightTime.getGeneratingResourceType());
        Assert.assertEquals("$resource.effective", weightTime.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0002]/events[at0003]/time", weightTime.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", weightTime.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("effective", weightTime.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().effective", weightTime.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/time", weightTime.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/time", weightTime.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]/time", weightTime.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?/any_event(:\\d+)?/time(:\\d+)?(\\|.*)?", weightTime.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_DATE_TIME", weightTime.getDetectedType());
        Assert.assertTrue(weightTime.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(weightTime.isUseParentRoot());
        Assert.assertFalse(weightTime.isHasSlot());
        Assert.assertEquals(0, weightTime.getChildren().size());

        // comment
        final MappingHelper comment = weightSlot.getChildren().get(2);
        Assert.assertEquals("comment", comment.getMappingName());
        Assert.assertEquals("Observation", comment.getGeneratingResourceType());
        Assert.assertEquals("$resource.note.text", comment.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0024]", comment.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", comment.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("note.text", comment.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().note.text", comment.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/data[at0001]/items[at0024]", comment.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/data[at0001]/items[at0024]", comment.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]/comment", comment.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?/any_event(:\\d+)?/comment(:\\d+)?(\\|.*)?", comment.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_TEXT", comment.getDetectedType());
        Assert.assertNull(comment.getFhirConditions());
        Assert.assertTrue(comment.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(comment.isUseParentRoot());
        Assert.assertFalse(comment.isHasSlot());
        Assert.assertEquals(0, comment.getChildren().size());

        // stateOfDress
        final MappingHelper stateOfDress = weightSlot.getChildren().get(3);
        Assert.assertEquals("stateOfDress", stateOfDress.getMappingName());
        Assert.assertEquals("Observation", stateOfDress.getGeneratingResourceType());
        Assert.assertEquals("$resource.component.value", stateOfDress.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0002]/events[at0003]/state[at0008]/items[at0009]", stateOfDress.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", stateOfDress.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("component.value", stateOfDress.getFhir());
        Assert.assertEquals("Observation.component.where(code.coding.code.toString().contains('9999-9')).value", stateOfDress.getFhirWithCondition());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component.value", stateOfDress.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_weight.v2/data[at0002]/events[at0003]/state[at0008]/items[at0009]", stateOfDress.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]/data[at0002]/events[at0003]/state[at0008]/items[at0009]", stateOfDress.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]/state_of_dress", stateOfDress.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?/any_event(:\\d+)?/state_of_dress(:\\d+)?(\\|.*)?", stateOfDress.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_CODED_TEXT", stateOfDress.getDetectedType());
        Assert.assertNotNull(stateOfDress.getFhirConditions());
        Assert.assertEquals(1, stateOfDress.getFhirConditions().size());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().component", stateOfDress.getFhirConditions().get(0).getTargetRoot());
        Assert.assertEquals("code.coding.code", stateOfDress.getFhirConditions().get(0).getTargetAttribute());
        Assert.assertEquals("one of", stateOfDress.getFhirConditions().get(0).getOperator());
        Assert.assertEquals("9999-9", stateOfDress.getFhirConditions().get(0).getCriteria());
        Assert.assertTrue(stateOfDress.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(stateOfDress.isUseParentRoot());
        Assert.assertFalse(stateOfDress.isHasSlot());
        Assert.assertEquals(0, stateOfDress.getChildren().size());

        // code (under weightSlot)
        final MappingHelper weightCode = weightSlot.getChildren().get(4);
        Assert.assertEquals("code", weightCode.getMappingName());
        Assert.assertEquals("Observation", weightCode.getGeneratingResourceType());
        Assert.assertEquals("$resource.code.coding", weightCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", weightCode.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight/any_event[n]", weightCode.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("code.coding", weightCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding", weightCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_weight.v2", weightCode.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_weight.v2]", weightCode.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_weight", weightCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_weight(:\\d+)?(\\|.*)?", weightCode.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("OBSERVATION", weightCode.getDetectedType());
        Assert.assertEquals("openehr->fhir", weightCode.getUnidirectional());
        Assert.assertTrue(weightCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(weightCode.isUseParentRoot());
        Assert.assertFalse(weightCode.isHasSlot());
        Assert.assertEquals(1, weightCode.getChildren().size());

        // code.code (under weightCode)
        final MappingHelper weightCodeCode = weightCode.getChildren().get(0);
        Assert.assertEquals("code.code", weightCodeCode.getMappingName());
        Assert.assertEquals("Observation", weightCodeCode.getGeneratingResourceType());
        Assert.assertEquals("code", weightCodeCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", weightCodeCode.getOriginalOpenEhrPath());
        Assert.assertEquals("code", weightCodeCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding.code", weightCodeCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_weight.v2", weightCodeCode.getOpenEhr());
        Assert.assertEquals("growth_chart/body_weight", weightCodeCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", weightCodeCode.getDetectedType());
        Assert.assertEquals("29463-7", weightCodeCode.getManualFhirValue());
        Assert.assertEquals("openehr->fhir", weightCodeCode.getUnidirectional());
        Assert.assertTrue(weightCodeCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(weightCodeCode.isUseParentRoot());
        Assert.assertFalse(weightCodeCode.isHasSlot());
        Assert.assertEquals(0, weightCodeCode.getChildren().size());

        // =====================================================================
        // bmiParent
        // =====================================================================
        final MappingHelper bmiParent = compositionHelpers.get(2);
        Assert.assertEquals("bmiParent", bmiParent.getMappingName());
        Assert.assertEquals("Bundle", bmiParent.getGeneratingResourceType());
        Assert.assertEquals("$resource.entry", bmiParent.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0002]/events[at0003]", bmiParent.getOriginalOpenEhrPath());
        Assert.assertEquals("entry", bmiParent.getFhir());
        Assert.assertEquals("Bundle.entry", bmiParent.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0002]/events[at0003]", bmiParent.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0002]/events[at0003]", bmiParent.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]", bmiParent.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_mass_index(:\\d+)?/any_event(:\\d+)?(\\|.*)?", bmiParent.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("EVENT", bmiParent.getDetectedType());
        Assert.assertFalse(bmiParent.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(bmiParent.isUseParentRoot());
        Assert.assertFalse(bmiParent.isHasSlot());
        Assert.assertEquals(1, bmiParent.getChildren().size());

        // iterateBmi
        final MappingHelper iterateBmi = bmiParent.getChildren().get(0);
        Assert.assertEquals("iterateBmi", iterateBmi.getMappingName());
        Assert.assertEquals("Bundle", iterateBmi.getGeneratingResourceType());
        Assert.assertEquals("Observation", iterateBmi.getResolveResourceType());
        Assert.assertEquals("resource", iterateBmi.getOriginalFhirPath());
        Assert.assertEquals("$reference", iterateBmi.getOriginalOpenEhrPath());
        Assert.assertEquals("resource.as(Reference).resolve()", iterateBmi.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", iterateBmi.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.growth_chart.v0", iterateBmi.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0002]/events[at0003]", iterateBmi.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]", iterateBmi.getFullOpenEhrFlatPath());
        Assert.assertEquals("EVENT", iterateBmi.getDetectedType());
        Assert.assertFalse(iterateBmi.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(iterateBmi.isUseParentRoot());
        Assert.assertFalse(iterateBmi.isHasSlot());
        Assert.assertEquals(1, iterateBmi.getChildren().size());

        // bmiSlot
        final MappingHelper bmiSlot = iterateBmi.getChildren().get(0);
        Assert.assertEquals("bmiSlot", bmiSlot.getMappingName());
        Assert.assertEquals("Observation", bmiSlot.getGeneratingResourceType());
        Assert.assertEquals("$fhirRoot", bmiSlot.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]", bmiSlot.getOriginalOpenEhrPath());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", bmiSlot.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", bmiSlot.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]", bmiSlot.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]", bmiSlot.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index", bmiSlot.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_mass_index(:\\d+)?(\\|.*)?", bmiSlot.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("OBSERVATION", bmiSlot.getDetectedType());
        Assert.assertFalse(bmiSlot.isEnteredFromSlotArchetypeLink());
        Assert.assertTrue(bmiSlot.isUseParentRoot());
        Assert.assertTrue(bmiSlot.isHasSlot());
        Assert.assertEquals(3, bmiSlot.getChildren().size());

        // bmi
        final MappingHelper bmi = bmiSlot.getChildren().get(0);
        Assert.assertEquals("bmi", bmi.getMappingName());
        Assert.assertEquals("Observation", bmi.getGeneratingResourceType());
        Assert.assertEquals("$resource.value", bmi.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0001]/events[at0002]/data[at0003]/items[at0004]", bmi.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]", bmi.getOpenEhrHierarchySplitFlatPath());
        Assert.assertNotNull(bmi.getPreprocessorFhirConditions());
        Assert.assertEquals(1, bmi.getPreprocessorFhirConditions().size());
        Assert.assertEquals("bmi", bmi.getPreprocessorFhirConditions().get(0).getCriteria());
        Assert.assertEquals("value", bmi.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", bmi.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_mass_index.v2/data[at0001]/events[at0002]/data[at0003]/items[at0004]", bmi.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0001]/events[at0002]/data[at0003]/items[at0004]", bmi.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]/body_mass_index", bmi.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_mass_index(:\\d+)?/any_event(:\\d+)?/body_mass_index(:\\d+)?(\\|.*)?", bmi.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_QUANTITY", bmi.getDetectedType());
        Assert.assertTrue(bmi.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(bmi.isUseParentRoot());
        Assert.assertFalse(bmi.isHasSlot());
        Assert.assertEquals(0, bmi.getChildren().size());

        // time (bmi)
        final MappingHelper bmiTime = bmiSlot.getChildren().get(1);
        Assert.assertEquals("time", bmiTime.getMappingName());
        Assert.assertEquals("Observation", bmiTime.getGeneratingResourceType());
        Assert.assertEquals("$resource.effective", bmiTime.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0001]/events[at0002]/time", bmiTime.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]", bmiTime.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("effective", bmiTime.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().effective", bmiTime.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_mass_index.v2/data[at0001]/events[at0002]/time", bmiTime.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]/data[at0001]/events[at0002]/time", bmiTime.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]/time", bmiTime.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/body_mass_index(:\\d+)?/any_event(:\\d+)?/time(:\\d+)?(\\|.*)?", bmiTime.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_DATE_TIME", bmiTime.getDetectedType());
        Assert.assertTrue(bmiTime.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(bmiTime.isUseParentRoot());
        Assert.assertFalse(bmiTime.isHasSlot());
        Assert.assertEquals(0, bmiTime.getChildren().size());

        // code (under bmiSlot)
        final MappingHelper bmiCode = bmiSlot.getChildren().get(2);
        Assert.assertEquals("code", bmiCode.getMappingName());
        Assert.assertEquals("Observation", bmiCode.getGeneratingResourceType());
        Assert.assertEquals("$resource.code.coding", bmiCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", bmiCode.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index/any_event[n]", bmiCode.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("code.coding", bmiCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding", bmiCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_mass_index.v2", bmiCode.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.body_mass_index.v2]", bmiCode.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/body_mass_index", bmiCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", bmiCode.getDetectedType());
        Assert.assertEquals("openehr->fhir", bmiCode.getUnidirectional());
        Assert.assertTrue(bmiCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(bmiCode.isUseParentRoot());
        Assert.assertFalse(bmiCode.isHasSlot());
        Assert.assertEquals(1, bmiCode.getChildren().size());

        // code.code (under bmiCode)
        final MappingHelper bmiCodeCode = bmiCode.getChildren().get(0);
        Assert.assertEquals("code.code", bmiCodeCode.getMappingName());
        Assert.assertEquals("Observation", bmiCodeCode.getGeneratingResourceType());
        Assert.assertEquals("code", bmiCodeCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", bmiCodeCode.getOriginalOpenEhrPath());
        Assert.assertEquals("code", bmiCodeCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding.code", bmiCodeCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.body_mass_index.v2", bmiCodeCode.getOpenEhr());
        Assert.assertEquals("growth_chart/body_mass_index", bmiCodeCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", bmiCodeCode.getDetectedType());
        Assert.assertEquals("39156-5", bmiCodeCode.getManualFhirValue());
        Assert.assertEquals("openehr->fhir", bmiCodeCode.getUnidirectional());
        Assert.assertTrue(bmiCodeCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(bmiCodeCode.isUseParentRoot());
        Assert.assertFalse(bmiCodeCode.isHasSlot());
        Assert.assertEquals(0, bmiCodeCode.getChildren().size());

        // =====================================================================
        // headCircumferenceParent
        // =====================================================================
        final MappingHelper headCircumferenceParent = compositionHelpers.get(3);
        Assert.assertEquals("headCircumferenceParent", headCircumferenceParent.getMappingName());
        Assert.assertEquals("Bundle", headCircumferenceParent.getGeneratingResourceType());
        Assert.assertEquals("$resource.entry", headCircumferenceParent.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0002]/events[at0003]", headCircumferenceParent.getOriginalOpenEhrPath());
        Assert.assertEquals("entry", headCircumferenceParent.getFhir());
        Assert.assertEquals("Bundle.entry", headCircumferenceParent.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0002]/events[at0003]", headCircumferenceParent.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0002]/events[at0003]", headCircumferenceParent.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]", headCircumferenceParent.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/head_circumference(:\\d+)?/any_event(:\\d+)?(\\|.*)?", headCircumferenceParent.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("EVENT", headCircumferenceParent.getDetectedType());
        Assert.assertFalse(headCircumferenceParent.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headCircumferenceParent.isUseParentRoot());
        Assert.assertFalse(headCircumferenceParent.isHasSlot());
        Assert.assertEquals(1, headCircumferenceParent.getChildren().size());

        // iterateHead
        final MappingHelper iterateHead = headCircumferenceParent.getChildren().get(0);
        Assert.assertEquals("iterateHead", iterateHead.getMappingName());
        Assert.assertEquals("Bundle", iterateHead.getGeneratingResourceType());
        Assert.assertEquals("Observation", iterateHead.getResolveResourceType());
        Assert.assertEquals("resource", iterateHead.getOriginalFhirPath());
        Assert.assertEquals("$reference", iterateHead.getOriginalOpenEhrPath());
        Assert.assertEquals("resource.as(Reference).resolve()", iterateHead.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", iterateHead.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-COMPOSITION.growth_chart.v0", iterateHead.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0002]/events[at0003]", iterateHead.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]", iterateHead.getFullOpenEhrFlatPath());
        Assert.assertEquals("EVENT", iterateHead.getDetectedType());
        Assert.assertFalse(iterateHead.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(iterateHead.isUseParentRoot());
        Assert.assertFalse(iterateHead.isHasSlot());
        Assert.assertEquals(1, iterateHead.getChildren().size());

        // headCircumferenceSlot
        final MappingHelper headCircumferenceSlot = iterateHead.getChildren().get(0);
        Assert.assertEquals("headCircumferenceSlot", headCircumferenceSlot.getMappingName());
        Assert.assertEquals("Observation", headCircumferenceSlot.getGeneratingResourceType());
        Assert.assertEquals("$fhirRoot", headCircumferenceSlot.getOriginalFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]", headCircumferenceSlot.getOriginalOpenEhrPath());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", headCircumferenceSlot.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", headCircumferenceSlot.getFullFhirPath());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]", headCircumferenceSlot.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]", headCircumferenceSlot.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference", headCircumferenceSlot.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/head_circumference(:\\d+)?(\\|.*)?", headCircumferenceSlot.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("OBSERVATION", headCircumferenceSlot.getDetectedType());
        Assert.assertFalse(headCircumferenceSlot.isEnteredFromSlotArchetypeLink());
        Assert.assertTrue(headCircumferenceSlot.isUseParentRoot());
        Assert.assertTrue(headCircumferenceSlot.isHasSlot());
        Assert.assertEquals(4, headCircumferenceSlot.getChildren().size());

        // headCircumference
        final MappingHelper headCircumference = headCircumferenceSlot.getChildren().get(0);
        Assert.assertEquals("headCircumference", headCircumference.getMappingName());
        Assert.assertEquals("Observation", headCircumference.getGeneratingResourceType());
        Assert.assertEquals("$resource.value", headCircumference.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0001]/events[at0010]/data[at0003]/items[at0004]", headCircumference.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]", headCircumference.getOpenEhrHierarchySplitFlatPath());
        Assert.assertNotNull(headCircumference.getPreprocessorFhirConditions());
        Assert.assertEquals(2, headCircumference.getPreprocessorFhirConditions().size());
        Assert.assertEquals("head_circumference", headCircumference.getPreprocessorFhirConditions().get(0).getCriteria());
        Assert.assertEquals("final", headCircumference.getPreprocessorFhirConditions().get(1).getCriteria());
        Assert.assertEquals("value", headCircumference.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().value", headCircumference.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.head_circumference.v1/data[at0001]/events[at0010]/data[at0003]/items[at0004]", headCircumference.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0001]/events[at0010]/data[at0003]/items[at0004]", headCircumference.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]/head_circumference", headCircumference.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/head_circumference(:\\d+)?/any_event(:\\d+)?/head_circumference(:\\d+)?(\\|.*)?", headCircumference.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_QUANTITY", headCircumference.getDetectedType());
        Assert.assertTrue(headCircumference.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headCircumference.isUseParentRoot());
        Assert.assertFalse(headCircumference.isHasSlot());
        Assert.assertEquals(0, headCircumference.getChildren().size());

        // time (head circumference)
        final MappingHelper headCircumferenceTime = headCircumferenceSlot.getChildren().get(1);
        Assert.assertEquals("time", headCircumferenceTime.getMappingName());
        Assert.assertEquals("Observation", headCircumferenceTime.getGeneratingResourceType());
        Assert.assertEquals("$resource.effective", headCircumferenceTime.getOriginalFhirPath());
        Assert.assertEquals("$archetype/data[at0001]/events[at0010]/time", headCircumferenceTime.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]", headCircumferenceTime.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("effective", headCircumferenceTime.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().effective", headCircumferenceTime.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.head_circumference.v1/data[at0001]/events[at0010]/time", headCircumferenceTime.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]/data[at0001]/events[at0010]/time", headCircumferenceTime.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]/time", headCircumferenceTime.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/head_circumference(:\\d+)?/any_event(:\\d+)?/time(:\\d+)?(\\|.*)?", headCircumferenceTime.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("DV_DATE_TIME", headCircumferenceTime.getDetectedType());
        Assert.assertTrue(headCircumferenceTime.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headCircumferenceTime.isUseParentRoot());
        Assert.assertFalse(headCircumferenceTime.isHasSlot());
        Assert.assertEquals(0, headCircumferenceTime.getChildren().size());

        // status (head circumference)
        final MappingHelper headStatus = headCircumferenceSlot.getChildren().get(2);
        Assert.assertEquals("status", headStatus.getMappingName());
        Assert.assertEquals("Observation", headStatus.getGeneratingResourceType());
        Assert.assertEquals("$resource", headStatus.getOriginalFhirPath());
        Assert.assertEquals("$archetype", headStatus.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]", headStatus.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("Observation", headStatus.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve()", headStatus.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.head_circumference.v1", headStatus.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]", headStatus.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference", headStatus.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", headStatus.getDetectedType());
        Assert.assertEquals("openehr->fhir", headStatus.getUnidirectional());
        Assert.assertTrue(headStatus.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headStatus.isUseParentRoot());
        Assert.assertFalse(headStatus.isHasSlot());
        Assert.assertEquals(1, headStatus.getChildren().size());

        // status.status
        final MappingHelper headStatusStatus = headStatus.getChildren().get(0);
        Assert.assertEquals("status.status", headStatusStatus.getMappingName());
        Assert.assertEquals("Observation", headStatusStatus.getGeneratingResourceType());
        Assert.assertEquals("status", headStatusStatus.getOriginalFhirPath());
        Assert.assertEquals("$archetype", headStatusStatus.getOriginalOpenEhrPath());
        Assert.assertEquals("status", headStatusStatus.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().status", headStatusStatus.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.head_circumference.v1", headStatusStatus.getOpenEhr());
        Assert.assertEquals("growth_chart/head_circumference", headStatusStatus.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", headStatusStatus.getDetectedType());
        Assert.assertEquals("final", headStatusStatus.getManualFhirValue());
        Assert.assertEquals("openehr->fhir", headStatusStatus.getUnidirectional());
        Assert.assertTrue(headStatusStatus.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headStatusStatus.isUseParentRoot());
        Assert.assertFalse(headStatusStatus.isHasSlot());
        Assert.assertEquals(0, headStatusStatus.getChildren().size());

        // code (under headCircumferenceSlot)
        final MappingHelper headCode = headCircumferenceSlot.getChildren().get(3);
        Assert.assertEquals("code", headCode.getMappingName());
        Assert.assertEquals("Observation", headCode.getGeneratingResourceType());
        Assert.assertEquals("$resource.code.coding", headCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", headCode.getOriginalOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference/any_event[n]", headCode.getOpenEhrHierarchySplitFlatPath());
        Assert.assertEquals("code.coding", headCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding", headCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.head_circumference.v1", headCode.getOpenEhr());
        Assert.assertEquals("$composition/content[openEHR-EHR-OBSERVATION.head_circumference.v1]", headCode.getFullOpenEhrPath());
        Assert.assertEquals("growth_chart/head_circumference", headCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("growth_chart(:\\d+)?/head_circumference(:\\d+)?(\\|.*)?", headCode.getFullOpenEhrFlatPathWithMatchingRegex());
        Assert.assertEquals("OBSERVATION", headCode.getDetectedType());
        Assert.assertEquals("openehr->fhir", headCode.getUnidirectional());
        Assert.assertTrue(headCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headCode.isUseParentRoot());
        Assert.assertFalse(headCode.isHasSlot());
        Assert.assertEquals(1, headCode.getChildren().size());

        // code.code (under headCode)
        final MappingHelper headCodeCode = headCode.getChildren().get(0);
        Assert.assertEquals("code.code", headCodeCode.getMappingName());
        Assert.assertEquals("Observation", headCodeCode.getGeneratingResourceType());
        Assert.assertEquals("code", headCodeCode.getOriginalFhirPath());
        Assert.assertEquals("$archetype", headCodeCode.getOriginalOpenEhrPath());
        Assert.assertEquals("code", headCodeCode.getFhir());
        Assert.assertEquals("Bundle.entry.resource.as(Reference).resolve().code.coding.code", headCodeCode.getFullFhirPath());
        Assert.assertEquals("openEHR-EHR-OBSERVATION.head_circumference.v1", headCodeCode.getOpenEhr());
        Assert.assertEquals("growth_chart/head_circumference", headCodeCode.getFullOpenEhrFlatPath());
        Assert.assertEquals("OBSERVATION", headCodeCode.getDetectedType());
        Assert.assertEquals("8287-5", headCodeCode.getManualFhirValue());
        Assert.assertEquals("openehr->fhir", headCodeCode.getUnidirectional());
        Assert.assertTrue(headCodeCode.isEnteredFromSlotArchetypeLink());
        Assert.assertFalse(headCodeCode.isUseParentRoot());
        Assert.assertFalse(headCodeCode.isHasSlot());
        Assert.assertEquals(0, headCodeCode.getChildren().size());
    }
}
