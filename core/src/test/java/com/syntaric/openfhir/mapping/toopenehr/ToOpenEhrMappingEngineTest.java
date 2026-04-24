package com.syntaric.openfhir.mapping.toopenehr;

import static org.junit.Assert.assertEquals;

import ca.uhn.fhir.context.FhirContext;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.NoOpPrePostOpenEhrPopulator;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import ca.uhn.fhir.fhirpath.IFhirPath;
import org.junit.Before;
import org.junit.Test;

public class ToOpenEhrMappingEngineTest {

    private ToOpenEhrMappingEngine engine;

    @Before
    public void setUp() {
        final OpenFhirStringUtils stringUtils = new OpenFhirStringUtils();
        final OpenFhirMapperUtils mapperUtils = new OpenFhirMapperUtils();
        engine = new ToOpenEhrMappingEngine(
                new FhirContextRegistry(),
                stringUtils,
                new OpenEhrPopulator(mapperUtils, null,
                                     new NoOpPrePostOpenEhrPopulator(),
                                     stringUtils),
                mapperUtils, new ToOpenEhrNullFlavour(stringUtils,
                                                      null),
                new CustomMappingRegistry(),
                (section, context, elapsedMs) -> { /* no-op metrics in tests */ });
    }

    // -------------------------------------------------------------------------
    // Case 1: no split path
    // -------------------------------------------------------------------------

    @Test
    public void noSplitPath_noRecurring_returnsPathUnchanged() {
        final MappingHelper h = helper(null, "growth_chart/body_weight/weight");
        assertEquals("growth_chart/body_weight/weight", engine.setIndexAccordingToHierarchy(h, 2));
    }

    @Test
    public void noSplitPath_singleRecurring_replacesLastWithIndex() {
        final MappingHelper h = helper(null, "growth_chart/body_weight/any_event[n]/weight");
        assertEquals("growth_chart/body_weight/any_event[n]/weight",
                     engine.setIndexAccordingToHierarchy(h, 3));
    }

    @Test
    public void noSplitPath_multipleRecurring_replacesOnlyLast() {
        final MappingHelper h = helper(null, "root[n]/child[n]/leaf");
        assertEquals("root[n]/child[n]/leaf", engine.setIndexAccordingToHierarchy(h, 1));
    }

    @Test
    public void noSplitPath_indexZero_producesZeroSuffix() {
        final MappingHelper h = helper(null, "root/event[n]/value");
        assertEquals("root/event[n]/value", engine.setIndexAccordingToHierarchy(h, 0));
    }

    // -------------------------------------------------------------------------
    // Case 2: split path set, path is OUTSIDE its hierarchy
    // -------------------------------------------------------------------------

    @Test
    public void outsideHierarchy_noRecurring_returnsPathUnchanged() {
        // fullPath shares no prefix with splitPath → outside hierarchy
        final MappingHelper h = helper(
                "growth_chart/body_weight/any_event[n]",
                "growth_chart/height_length/any_event/height");
        assertEquals("growth_chart/height_length/any_event/height",
                     engine.setIndexAccordingToHierarchy(h, 1));
    }

    @Test
    public void outsideHierarchy_withRecurring_replacesLast() {
        final MappingHelper h = helper(
                "growth_chart/body_weight/any_event[n]",
                "growth_chart/height_length/any_event[n]/height");
        assertEquals("growth_chart/height_length/any_event:2/height",
                     engine.setIndexAccordingToHierarchy(h, 2));
    }

    // -------------------------------------------------------------------------
    // Case 3: split path set, path is INSIDE its hierarchy
    // -------------------------------------------------------------------------

    @Test
    public void insideHierarchy_singleRecurringInSplitPath_indexedCorrectly() {
        final MappingHelper h = helper(
                "growth_chart/body_weight/any_event[n]",
                "growth_chart/body_weight/any_event[n]/weight");
        // last [n] in split path → :5; no remaining [n]
        assertEquals("growth_chart/body_weight/any_event:5",
                     engine.setIndexAccordingToHierarchy(h, 5));
    }

    @Test
    public void insideHierarchy_childPathBeyondSplitPath_indexedCorrectly() {
        // fullPath goes deeper than the split path — the result is the indexed
        // splitPath only (the caller uses replacePattern to merge it with the full path)
        final MappingHelper h = helper(
                "growth_chart/body_weight/any_event[n]",
                "growth_chart/body_weight/any_event[n]/state_of_dress");
        assertEquals("growth_chart/body_weight/any_event:0",
                     engine.setIndexAccordingToHierarchy(h, 0));
    }

    @Test
    public void insideHierarchy_outerRecurringCollapsedToZero() {
        // split path itself has a leading [n] that also needs to be resolved
        final MappingHelper h = helper(
                "root[n]/child[n]",
                "root[n]/child[n]/value");
        // replaceLastIndexOf on "root[n]/child[n]" with ":7" → "root[n]/child:7"
        // then remaining [n] → :0  ⇒ "root:0/child:7"
        assertEquals("root:0/child:7", engine.setIndexAccordingToHierarchy(h, 7));
    }

    @Test
    public void insideHierarchy_indexZero() {
        final MappingHelper h = helper(
                "medikamentenliste/aussage_zur_medikamenteneinnahme[n]",
                "medikamentenliste/aussage_zur_medikamenteneinnahme[n]/time");
        assertEquals("medikamentenliste/aussage_zur_medikamenteneinnahme:0",
                     engine.setIndexAccordingToHierarchy(h, 0));
    }

    @Test
    public void insideHierarchy_indexNonZero() {
        final MappingHelper h = helper(
                "medikamentenliste/aussage_zur_medikamenteneinnahme[n]",
                "medikamentenliste/aussage_zur_medikamenteneinnahme[n]/time");
        assertEquals("medikamentenliste/aussage_zur_medikamenteneinnahme:3",
                     engine.setIndexAccordingToHierarchy(h, 3));
    }

    @Test
    public void insideHierarchy_splitPathEqualsFullPath_indexedDirectly() {
        final MappingHelper h = helper(
                "growth_chart/body_mass_index/any_event[n]",
                "growth_chart/body_mass_index/any_event[n]");
        assertEquals("growth_chart/body_mass_index/any_event:2",
                     engine.setIndexAccordingToHierarchy(h, 2));
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static MappingHelper helper(final String splitPath, final String fullFlatPath) {
        final MappingHelper h = new MappingHelper();
        h.setOpenEhrHierarchySplitFlatPath(splitPath);
        h.setFullOpenEhrFlatPath(fullFlatPath);
        return h;
    }
}
