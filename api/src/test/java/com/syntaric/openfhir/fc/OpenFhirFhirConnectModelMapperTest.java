package com.syntaric.openfhir.fc;

import com.syntaric.openfhir.fc.schema.Metadata;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.model.FhirConfig;
import com.syntaric.openfhir.fc.schema.model.FhirConnectModel;
import com.syntaric.openfhir.fc.schema.model.FollowedBy;
import com.syntaric.openfhir.fc.schema.model.Manual;
import com.syntaric.openfhir.fc.schema.model.ManualEntry;
import com.syntaric.openfhir.fc.schema.model.Mapping;
import com.syntaric.openfhir.fc.schema.model.OpenEhrConfig;
import com.syntaric.openfhir.fc.schema.model.With;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class OpenFhirFhirConnectModelMapperTest {
    @Test
    public void handleManualMappingsTest() {
        final FhirConnectModel fhirConnectModel = buildFhirConnectModel();
        final OpenFhirFhirConnectModelMapper handled = new OpenFhirFhirConnectModelMapper().fromFhirConnectModelMapper(
                fhirConnectModel);

        // assert first level mappings
        Assert.assertEquals(6, handled.getMappings().size());
        Assert.assertEquals("context", handled.getMappings().get(0).getName());
        Assert.assertEquals("$resource.effective.as(Period)", handled.getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("$composition/context", handled.getMappings().get(0).getWith().getOpenehr());
        Assert.assertEquals(2, handled.getMappings().get(0).getFollowedBy().getMappings().size());
        Assert.assertEquals("contextStart",
                            handled.getMappings().get(0).getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("contextEnd", handled.getMappings().get(0).getFollowedBy().getMappings().get(1).getName());
        Assert.assertEquals("_end_time",
                            handled.getMappings().get(0).getFollowedBy().getMappings().get(1).getWith().getOpenehr());

        final Mapping firstLevelDraftTerminology = handled.getMappings().get(1);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftTerminology.getName());
        Assert.assertEquals("openehr", firstLevelDraftTerminology.getWith().getValue());
        Assert.assertEquals("$archetype/ism_transition/current_state/terminology_id",
                            firstLevelDraftTerminology.getWith().getOpenehr());
        Assert.assertEquals("status", firstLevelDraftTerminology.getFhirCondition().getTargetRoot());
        Assert.assertEquals("[draft]", firstLevelDraftTerminology.getFhirCondition().getCriteria());

        final Mapping firstLevelDraftValue = handled.getMappings().get(2);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftValue.getName());
        Assert.assertEquals("Initial", firstLevelDraftValue.getWith().getValue());
        Assert.assertEquals("$archetype/ism_transition/current_state",
                            firstLevelDraftValue.getWith().getOpenehr());
        Assert.assertEquals("status", firstLevelDraftValue.getFhirCondition().getTargetRoot());
        Assert.assertEquals("[draft]", firstLevelDraftValue.getFhirCondition().getCriteria());

        final Mapping firstLevelDraftCode = handled.getMappings().get(3);
        Assert.assertEquals("firstLevelTest.draft", firstLevelDraftCode.getName());
        Assert.assertEquals("524", firstLevelDraftCode.getWith().getValue());
        Assert.assertEquals("$archetype/ism_transition/current_state/defining_code",
                            firstLevelDraftCode.getWith().getOpenehr());
        Assert.assertEquals("status", firstLevelDraftCode.getFhirCondition().getTargetRoot());
        Assert.assertEquals("[draft]", firstLevelDraftCode.getFhirCondition().getCriteria());

        final Mapping firstLevelDraftFhir = handled.getMappings().get(4);
        final Mapping mapping = firstLevelDraftFhir.getFollowedBy().getMappings().get(0);
        Assert.assertEquals("firstLevelTest.draft", mapping.getName());
        Assert.assertEquals("draft", mapping.getWith().getValue());
        Assert.assertEquals("status", mapping.getWith().getFhir());
        Assert.assertEquals("$archetype/ism_transition/current_state",
                            mapping.getOpenehrCondition().getTargetRoot());
        Assert.assertEquals("defining_code", mapping.getOpenehrCondition().getTargetAttribute());
        Assert.assertEquals("[524]", mapping.getOpenehrCondition().getCriteria());

        // assert second level mappings
        final Mapping innerMapping = handled.getMappings().get(5);
        Assert.assertEquals("$resource", innerMapping.getWith().getFhir());
        Assert.assertEquals("$archetype/ism_transition/current_state", innerMapping.getWith().getOpenehr());
        Assert.assertEquals(2, innerMapping.getFollowedBy().getMappings().size());
        Assert.assertEquals("performer", innerMapping.getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("$resource.performer.as(Reference).display", innerMapping.getFollowedBy().getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("$composition/perfomer", innerMapping.getFollowedBy().getMappings().get(0).getWith().getOpenehr());
        Assert.assertEquals("STRING", innerMapping.getFollowedBy().getMappings().get(0).getWith().getType());
        Assert.assertNull(innerMapping.getFollowedBy().getMappings().get(0).getFollowedBy());

        final Mapping secondLevelMapping = innerMapping.getFollowedBy().getMappings().get(1);
        Assert.assertEquals("secondLevel", secondLevelMapping.getName());
        Assert.assertEquals("status", secondLevelMapping.getWith().getFhir());
        Assert.assertEquals("something/else", secondLevelMapping.getWith().getOpenehr());
        Assert.assertNull(secondLevelMapping.getManual());

        Assert.assertEquals(5, secondLevelMapping.getFollowedBy().getMappings().size());
        final Mapping secondLevelFirst = secondLevelMapping.getFollowedBy().getMappings().get(0);
        Assert.assertEquals("name", secondLevelFirst.getName());
        Assert.assertEquals("$resource.code", secondLevelFirst.getWith().getFhir());
        Assert.assertEquals("$archetype", secondLevelFirst.getWith().getOpenehr());
        Assert.assertEquals("Name", secondLevelFirst.getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("coding", secondLevelFirst.getFollowedBy().getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("description[at0001]/items[at0002]", secondLevelFirst.getFollowedBy().getMappings().get(0).getWith().getOpenehr());
        Assert.assertEquals("CODING", secondLevelFirst.getFollowedBy().getMappings().get(0).getWith().getType());
        Assert.assertEquals("[http://fhir.de/CodeSystem/bfarm/ops, http://snomed.info/sct]", secondLevelFirst.getFollowedBy().getMappings().get(0).getFhirCondition().getCriteria());

        final Mapping secondLevelTheOnesTerminology = secondLevelMapping.getFollowedBy().getMappings().get(1);
        final Mapping secondLevelTheOnesValue = secondLevelMapping.getFollowedBy().getMappings().get(2);
        final Mapping secondLevelTheOnesCode = secondLevelMapping.getFollowedBy().getMappings().get(3);
        final Mapping secondLevelTheOnesFhir = secondLevelMapping.getFollowedBy().getMappings().get(4);
        Assert.assertEquals(5, secondLevelMapping.getFollowedBy().getMappings().size());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesTerminology.getName());
        Assert.assertEquals("third/terminology_id", secondLevelTheOnesTerminology.getWith().getOpenehr());
        Assert.assertEquals("openehr", secondLevelTheOnesTerminology.getWith().getValue());
        Assert.assertEquals("[active]", secondLevelTheOnesTerminology.getFhirCondition().getCriteria());
        Assert.assertEquals("status", secondLevelTheOnesTerminology.getFhirCondition().getTargetRoot());
        Assert.assertEquals("value", secondLevelTheOnesTerminology.getFhirCondition().getTargetAttribute());
        Assert.assertNull(secondLevelTheOnesTerminology.getOpenehrCondition());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesValue.getName());
        Assert.assertEquals("third", secondLevelTheOnesValue.getWith().getOpenehr());
        Assert.assertEquals("InitialX", secondLevelTheOnesValue.getWith().getValue());
        Assert.assertEquals("[active]", secondLevelTheOnesValue.getFhirCondition().getCriteria());
        Assert.assertEquals("status", secondLevelTheOnesValue.getFhirCondition().getTargetRoot());
        Assert.assertEquals("value", secondLevelTheOnesValue.getFhirCondition().getTargetAttribute());
        Assert.assertNull(secondLevelTheOnesValue.getOpenehrCondition());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesCode.getName());
        Assert.assertEquals("third/defining_code", secondLevelTheOnesCode.getWith().getOpenehr());
        Assert.assertEquals("xxx", secondLevelTheOnesCode.getWith().getValue());
        Assert.assertEquals("[active]", secondLevelTheOnesCode.getFhirCondition().getCriteria());
        Assert.assertEquals("status", secondLevelTheOnesCode.getFhirCondition().getTargetRoot());
        Assert.assertEquals("value", secondLevelTheOnesCode.getFhirCondition().getTargetAttribute());
        Assert.assertNull(secondLevelTheOnesCode.getOpenehrCondition());

        Assert.assertEquals("thirdLevel.thirdLevelManualMappings", secondLevelTheOnesFhir.getFollowedBy().getMappings().get(0).getName());
        Assert.assertEquals("status", secondLevelTheOnesFhir.getFollowedBy().getMappings().get(0).getWith().getFhir());
        Assert.assertEquals("yyyy", secondLevelTheOnesFhir.getFollowedBy().getMappings().get(0).getWith().getValue());
        Assert.assertEquals("third", secondLevelTheOnesFhir.getFollowedBy().getMappings().get(0).getOpenehrCondition().getTargetRoot());
        Assert.assertEquals("[999]", secondLevelTheOnesFhir.getFollowedBy().getMappings().get(0).getOpenehrCondition().getCriteria());
        Assert.assertNull(secondLevelTheOnesFhir.getFollowedBy().getMappings().get(0).getFhirCondition());
    }

    // -----------------------------------------------------------------------
    // Inline FhirConnectModel matching manualMappingsTest.yml
    // -----------------------------------------------------------------------

    private static FhirConnectModel buildFhirConnectModel() {
        // --- context mapping (followedBy: contextStart, contextEnd) ---
        final Mapping contextStart = new Mapping()
                .withName("contextStart")
                .withWith(new With().withFhir("start").withOpenehr("start_time"));

        final Mapping contextEnd = new Mapping()
                .withName("contextEnd")
                .withWith(new With().withFhir("end").withOpenehr("_end_time"));

        final Mapping context = new Mapping()
                .withName("context")
                .withWith(new With().withFhir("$resource.effective.as(Period)").withOpenehr("$composition/context"))
                .withFollowedBy(new FollowedBy().withMappings(List.of(contextStart, contextEnd)));

        // --- firstLevelTest mapping (manual: draft) ---
        final Condition draftFhirCondition = new Condition()
                .withTargetRoot("status")
                .withTargetAttribute("value")
                .withOperator("one of")
                .withCriteria("[draft]");

        final Condition draftOpenehrCondition = new Condition()
                .withTargetRoot("$openehrRoot")
                .withTargetAttribute("defining_code")
                .withOperator("one of")
                .withCriteria("[524]");

        final Manual draftManual = new Manual();
        draftManual.setName("draft");
        draftManual.setOpenehr(List.of(
                entry("terminology_id", "openehr"),
                entry("value", "Initial"),
                entry("defining_code", "524")));
        draftManual.setFhirCondition(draftFhirCondition);
        draftManual.setFhir(List.of(entry("status", "draft")));
        draftManual.setOpenehrCondition(draftOpenehrCondition);

        final Mapping firstLevelTest = new Mapping()
                .withName("firstLevelTest")
                .withWith(new With().withFhir("$resource").withOpenehr("$archetype/ism_transition/current_state"))
                .withManual(List.of(draftManual));

        // --- innerTest mapping (followedBy: performer, secondLevel) ---
        final Mapping performer = new Mapping()
                .withName("performer")
                .withWith(new With()
                        .withFhir("$resource.performer.as(Reference).display")
                        .withOpenehr("$composition/perfomer")
                        .withType("STRING"));

        // secondLevel / name / Name
        final Condition nameFhirCondition = new Condition()
                .withTargetRoot("coding")
                .withTargetAttribute("system")
                .withOperator("one of")
                .withCriteria("[http://fhir.de/CodeSystem/bfarm/ops, http://snomed.info/sct]");

        final Mapping nameChild = new Mapping()
                .withName("Name")
                .withWith(new With().withFhir("coding").withOpenehr("description[at0001]/items[at0002]").withType("CODING"))
                .withFhirCondition(nameFhirCondition);

        final Mapping name = new Mapping()
                .withName("name")
                .withWith(new With().withFhir("$resource.code").withOpenehr("$archetype"))
                .withFollowedBy(new FollowedBy().withMappings(List.of(nameChild)));

        // secondLevel / thirdLevel (manual: thirdLevelManualMappings)
        final Condition thirdFhirCondition = new Condition()
                .withTargetRoot("status")
                .withTargetAttribute("value")
                .withOperator("one of")
                .withCriteria("[active]");

        final Condition thirdOpenehrCondition = new Condition()
                .withTargetRoot("$openehrRoot")
                .withTargetAttribute("defining_code")
                .withOperator("one of")
                .withCriteria("[999]");

        final Manual thirdManual = new Manual();
        thirdManual.setName("thirdLevelManualMappings");
        thirdManual.setOpenehr(List.of(
                entry("terminology_id", "openehr"),
                entry("value", "InitialX"),
                entry("defining_code", "xxx")));
        thirdManual.setFhirCondition(thirdFhirCondition);
        thirdManual.setFhir(List.of(entry("status", "yyyy")));
        thirdManual.setOpenehrCondition(thirdOpenehrCondition);

        final Mapping thirdLevel = new Mapping()
                .withName("thirdLevel")
                .withWith(new With().withFhir("code").withOpenehr("third"))
                .withManual(List.of(thirdManual));

        final Mapping secondLevel = new Mapping()
                .withName("secondLevel")
                .withWith(new With().withFhir("status").withOpenehr("something/else"))
                .withFollowedBy(new FollowedBy().withMappings(List.of(name, thirdLevel)));

        final Mapping innerTest = new Mapping()
                .withName("innerTest")
                .withWith(new With().withFhir("$resource").withOpenehr("$archetype/ism_transition/current_state"))
                .withFollowedBy(new FollowedBy().withMappings(List.of(performer, secondLevel)));

        final OpenEhrConfig openEhrConfig = new OpenEhrConfig();
        openEhrConfig.setArchetype("openEHR-EHR-ACTION.informed_consent.v0");

        final FhirConfig fhirConfig = new FhirConfig()
                .withStructureDefinition("http://hl7.org/fhir/StructureDefinition/Consent");

        final Spec spec = new Spec();
        spec.setSystem(Spec.System.FHIR);
        spec.setVersion(Spec.Version.R4);
        spec.setOpenEhrConfig(openEhrConfig);
        spec.setFhirConfig(fhirConfig);

        final Metadata metadata = new Metadata();
        metadata.setName("ACTION.informed_consent.v0");
        metadata.setVersion("0.0.1a");

        final FhirConnectModel model = new FhirConnectModel();
        model.setSpec(spec);
        model.setMetadata(metadata);
        model.setMappings(List.of(context, firstLevelTest, innerTest));
        return model;
    }

    private static ManualEntry entry(final String path, final String value) {
        final ManualEntry e = new ManualEntry();
        e.setPath(path);
        e.setValue(value);
        return e;
    }

    @Test
    public void testIsFhirPathConcatination() {
        final OpenFhirFhirConnectModelMapper mapper = new OpenFhirFhirConnectModelMapper();

        // Test cases with concatenation
        Assert.assertTrue(mapper.isFhirPathConcatination("$resource.context.related.reference &'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'"));
        Assert.assertTrue(mapper.isFhirPathConcatination("$resource.context.related.reference & '^^^^urn:ihe:iti:xds:2016:studyInstanceUID'"));
        Assert.assertTrue(mapper.isFhirPathConcatination("'prefix' &$resource.context.related.reference"));
        Assert.assertTrue(mapper.isFhirPathConcatination("'prefix' & $resource.context.related.reference"));
        Assert.assertTrue(mapper.isFhirPathConcatination("'prefix'&'suffix'"));
        Assert.assertTrue(mapper.isFhirPathConcatination("'prefix' & 'suffix'"));

        // Test cases without concatenation
        Assert.assertFalse(mapper.isFhirPathConcatination("$resource.context.related.reference"));
        Assert.assertFalse(mapper.isFhirPathConcatination("simple.path"));
        Assert.assertFalse(mapper.isFhirPathConcatination("'string without concat'"));
        Assert.assertFalse(mapper.isFhirPathConcatination("path.with.quotes'but'no.concat"));
        Assert.assertFalse(mapper.isFhirPathConcatination(""));
    }

    @Test
    public void testGetPrefixConcat() {
        final OpenFhirFhirConnectModelMapper mapper = new OpenFhirFhirConnectModelMapper();

        // Test cases that should return prefix
        Assert.assertEquals("'prefix'&", mapper.getPrefixConcat("'prefix'&$resource.something"));
        Assert.assertEquals("'prefix' &", mapper.getPrefixConcat("'prefix' &$resource.something"));
        Assert.assertEquals("'prefix'&", mapper.getPrefixConcat("'prefix'& $resource"));
        Assert.assertEquals("'prefix' &", mapper.getPrefixConcat("'prefix' & $resource.something"));
        Assert.assertEquals("'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'&", mapper.getPrefixConcat("'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'&$resource.context.related.reference"));

        // Test cases that should return null (doesn't start with quote)
        Assert.assertNull(mapper.getPrefixConcat("$resource.context.related.reference &'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'"));
        Assert.assertNull(mapper.getPrefixConcat("path.without.quotes&'suffix'"));
        Assert.assertNull(mapper.getPrefixConcat("simple.path"));
        Assert.assertNull(mapper.getPrefixConcat(""));
    }

    @Test
    public void testGetSuffixConcat() {
        final OpenFhirFhirConnectModelMapper mapper = new OpenFhirFhirConnectModelMapper();

        // Test cases that should return suffix
        Assert.assertEquals("&'suffix'", mapper.getSuffixConcat("$resource.something&'suffix'"));
        Assert.assertEquals("& 'suffix'", mapper.getSuffixConcat("$resource.something & 'suffix'"));
        Assert.assertEquals("&'suffix'", mapper.getSuffixConcat("$resource&'suffix'"));
        Assert.assertEquals("&'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'", mapper.getSuffixConcat("$resource.context.related.reference&'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'"));
        Assert.assertEquals("& '^^^^urn:ihe:iti:xds:2016:studyInstanceUID'", mapper.getSuffixConcat("$resource.context.related.reference & '^^^^urn:ihe:iti:xds:2016:studyInstanceUID'"));

        // Test cases that should return null (doesn't end with quote)
        Assert.assertNull(mapper.getSuffixConcat("'prefix'&$resource.context.related.reference"));
        Assert.assertNull(mapper.getSuffixConcat("'prefix'&path.without.quotes"));
        Assert.assertNull(mapper.getSuffixConcat("simple.path"));
        Assert.assertNull(mapper.getSuffixConcat(""));
    }

    @Test
    public void testProcessFhirPathConcatenation() {
        final OpenFhirFhirConnectModelMapper mapper = new OpenFhirFhirConnectModelMapper();


        // Test case 2: Only prefix concatenation
        final Mapping mapping2 = new Mapping();
        mapping2.setWith(new With().withFhir("'^^^^urn:ihe:iti:xds:2016:studyInstanceUID'&$resource.context.related.reference"));
        mapper.processFhirPathConcatenation(mapping2);

        Assert.assertEquals("$resource.context.related.reference", mapping2.getWith().getFhir());
        Assert.assertEquals("^^^^urn:ihe:iti:xds:2016:studyInstanceUID", mapping2.getPrefixConcat());
        Assert.assertNull(mapping2.getSuffixConcat());

        // Test case 3: Only suffix concatenation
        final Mapping mapping3 = new Mapping();
        mapping3.setWith(new With().withFhir("$resource.context.related.reference & '^^^^urn:ihe:iti:xds:2016:studyInstanceUID'"));
        mapper.processFhirPathConcatenation(mapping3);

        Assert.assertEquals("$resource.context.related.reference", mapping3.getWith().getFhir());
        Assert.assertNull(mapping3.getPrefixConcat());
        Assert.assertEquals("^^^^urn:ihe:iti:xds:2016:studyInstanceUID", mapping3.getSuffixConcat());

        // Test case 4: No concatenation
        final Mapping mapping4 = new Mapping();
        mapping4.setWith(new With().withFhir("$resource.context.related.reference"));
        mapper.processFhirPathConcatenation(mapping4);

        Assert.assertEquals("$resource.context.related.reference", mapping4.getWith().getFhir());
        Assert.assertNull(mapping4.getPrefixConcat());
        Assert.assertNull(mapping4.getSuffixConcat());

        // Test case 5: Null with object
        final Mapping mapping5 = new Mapping();
        mapping5.setWith(null);
        mapper.processFhirPathConcatenation(mapping5);

        Assert.assertNull(mapping5.getWith());
        Assert.assertNull(mapping5.getPrefixConcat());
        Assert.assertNull(mapping5.getSuffixConcat());

        // Test case 6: With object with null fhir
        final Mapping mapping6 = new Mapping();
        mapping6.setWith(new With().withFhir(null));
        mapper.processFhirPathConcatenation(mapping6);

        Assert.assertNull(mapping6.getWith().getFhir());
        Assert.assertNull(mapping6.getPrefixConcat());
        Assert.assertNull(mapping6.getSuffixConcat());

        // Test case 7: Complex concatenation with spaces
        final Mapping mapping7 = new Mapping();
        mapping7.setWith(new With().withFhir("'prefix with spaces' & $resource.path"));
        mapper.processFhirPathConcatenation(mapping7);

        Assert.assertEquals("$resource.path", mapping7.getWith().getFhir());
        Assert.assertEquals("prefix with spaces", mapping7.getPrefixConcat());
    }
}