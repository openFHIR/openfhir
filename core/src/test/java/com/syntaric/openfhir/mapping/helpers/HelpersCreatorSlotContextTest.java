package com.syntaric.openfhir.mapping.helpers;

import com.syntaric.openfhir.OpenFhirMappingContext;
import com.syntaric.openfhir.TestOpenFhirMappingContext;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.util.FhirConnectModelMerger;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import com.syntaric.openfhir.util.OpenFhirTestUtility;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A slotContext resolves the slotted model from the referenced sub-context's own archetypes and extensions.
 */
public class HelpersCreatorSlotContextTest {

    private TestOpenFhirMappingContext repo;
    private HelpersCreator helpersCreator;

    @Before
    public void setUp() {
        repo = new TestOpenFhirMappingContext(new FhirConnectModelMerger());
        helpersCreator = new HelpersCreator(repo, new AqlToFlatPathConverter(new OpenFhirStringUtils()));
    }

    @Test
    public void subContextsExtendingSameArchetype_resolveToTheirOwnExtension() throws IOException {
        final List<MappingHelper> helpers = constructHelpers("/slotcontext/");

        final MappingHelper sectionA = byName(helpers, "sectionA");
        final MappingHelper sectionB = byName(helpers, "sectionB");

        Assert.assertTrue(sectionA.isHasSlot());
        Assert.assertTrue(sectionB.isHasSlot());
        Assert.assertEquals(Set.of("common", "onlyInA"), childNames(sectionA));
        Assert.assertEquals(Set.of("common", "onlyInB"), childNames(sectionB));

        // same shape as a slotArchetype: children are entered from the slot and hang off the slot's paths
        final MappingHelper onlyInA = byName(sectionA.getChildren(), "onlyInA");
        Assert.assertTrue(onlyInA.isEnteredFromSlotArchetypeLink());
        Assert.assertEquals("openEHR-EHR-SECTION.adhoc.v1", onlyInA.getArchetype());
        Assert.assertTrue(onlyInA.getFullOpenEhrPath().startsWith(sectionA.getFullOpenEhrPath()));
        Assert.assertEquals(sectionA.getFullFhirPath() + ".code.text", onlyInA.getFullFhirPath());
    }

    @Test
    public void slotContextNotListedUnderContexts_isSkipped() throws IOException {
        final List<MappingHelper> helpers = constructHelpers("/slotcontext/");

        final MappingHelper undeclared = byName(helpers, "sectionUndeclared");
        Assert.assertFalse(undeclared.isHasSlot());
        Assert.assertTrue(undeclared.getChildren() == null || undeclared.getChildren().isEmpty());
        Assert.assertNull(repo.getStartMappersForContext("c.context"));
    }

    @Test
    public void cyclicContexts_areNotLoaded() throws IOException {
        final FhirConnectContext context = context("/slotcontext_cyclic/");
        repo.initRepository(context, null, getClass().getResource("/slotcontext_cyclic/").getFile());

        Assert.assertTrue(repo.getRepository().isEmpty());
    }

    private List<MappingHelper> constructHelpers(final String dir) throws IOException {
        final FhirConnectContext context = context(dir);
        repo.initRepository(context, null, getClass().getResource(dir).getFile());
        final String templateId = OpenFhirMappingContext.normalizeTemplateId(
                context.getContext().getTemplate().getId());
        return helpersCreator.constructHelpers(templateId, context.getContext().getStart(),
                                               context.getContext().getArchetypes(), null)
                .get(context.getContext().getStart());
    }

    private FhirConnectContext context(final String dir) throws IOException {
        return OpenFhirTestUtility.getYaml()
                .readValue(getClass().getResourceAsStream(dir + "root.context.yml"), FhirConnectContext.class);
    }

    private MappingHelper byName(final List<MappingHelper> helpers, final String name) {
        return helpers.stream()
                .filter(helper -> name.equals(helper.getMappingName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No helper for mapping " + name));
    }

    private Set<String> childNames(final MappingHelper helper) {
        return helper.getChildren().stream().map(MappingHelper::getMappingName).collect(Collectors.toSet());
    }
}
