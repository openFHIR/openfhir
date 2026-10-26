package com.syntaric.openfhir.mapping.tofhir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.OpenEhrFlatPathDataExtractor;
import com.syntaric.openfhir.mapping.helpers.parser.CodedParser;
import com.syntaric.openfhir.mapping.helpers.parser.FhirValueReaders;
import com.syntaric.openfhir.mapping.helpers.parser.IdentifierParser;
import com.syntaric.openfhir.mapping.helpers.parser.MediaParser;
import com.syntaric.openfhir.mapping.helpers.parser.QuantityParser;
import com.syntaric.openfhir.mapping.helpers.parser.ReferenceParser;
import com.syntaric.openfhir.mapping.helpers.parser.TemporalParser;
import com.syntaric.openfhir.mapping.helpers.parser.TextParser;
import com.syntaric.openfhir.mapping.helpers.parser.ValueToFHIRParser;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.FhirInstancePopulator;
import com.syntaric.openfhir.util.MappingExecutionException;
import com.syntaric.openfhir.util.OpenEhrConditionEvaluator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

/**
 * Issue #120: a runtime failure inside one {@code $tofhir} mapping is reported with the mapping's context instead
 * of escaping as an anonymous exception, and the remaining mappings are still processed.
 */
public class ToFhirMappingEngineFailureContextTest {

    private static final String NPE_TEXT = "Cannot invoke \"org.openehr.schemas.v1.OPERATIONALTEMPLATE.getLanguage()\"";
    private static final String BROKEN = "broken";

    private ToFhirMappingEngine engine;
    private ToFhirInstantiator instantiator;
    private FhirInstancePopulator populator;

    @Before
    public void setUp() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(openFhirStringUtils);
        final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
        final FhirValueReaders readers = new FhirValueReaders(openFhirMapperUtils);

        instantiator = Mockito.mock(ToFhirInstantiator.class);
        // the "broken" mapping blows up inside the engine; every other mapping instantiates normally
        Mockito.when(instantiator.instantiateElement(ArgumentMatchers.any(MappingHelper.class), ArgumentMatchers.any(),
                        ArgumentMatchers.anyInt(), ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    final MappingHelper helper = invocation.getArgument(0);
                    if (BROKEN.equals(helper.getMappingName())) {
                        throw new NullPointerException(NPE_TEXT);
                    }
                    return new StringType();
                });
        populator = Mockito.mock(FhirInstancePopulator.class);

        engine = new ToFhirMappingEngine(
                new OpenEhrConditionEvaluator(openFhirStringUtils),
                fhirInstanceCreatorUtility,
                new FhirContextRegistry(),
                new OpenEhrFlatPathDataExtractor(openFhirStringUtils,
                        new ValueToFHIRParser(
                                new TemporalParser(readers),
                                new QuantityParser(readers),
                                new CodedParser(readers),
                                new MediaParser(readers),
                                new TextParser(readers),
                                new IdentifierParser(readers),
                                new ReferenceParser(readers))),
                openFhirStringUtils,
                populator,
                instantiator,
                new CustomMappingRegistry(),
                openFhirMapperUtils,
                (section, context, elapsedMs) -> { /* no-op metrics in tests */ },
                new ToFhirNullFlavour(openFhirStringUtils, fhirInstanceCreatorUtility));
    }

    /**
     * A hardcoded mapping (manual FHIR value, no flat-path regex) reaches the instantiator deterministically.
     */
    private static MappingHelper hardcodedHelper(final String mappingName) {
        final MappingHelper helper = new MappingHelper();
        helper.setModelMetadataName("Body weight");
        helper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper.setMappingName(mappingName);
        helper.setOriginalOpenEhrPath("$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0004]");
        helper.setOriginalFhirPath("$resource.value");
        helper.setFhir("Observation.value");
        helper.setManualFhirValue("kg");
        helper.setGeneratingFhirResource(new Observation());
        return helper;
    }

    private static JsonObject flat() {
        return JsonParser.parseString("{\"body_weight/any_event:0/weight|magnitude\": 70}").getAsJsonObject();
    }

    private static List<MappingIssueCollector.MappingIssue> errors(final MappingIssueCollector collector) {
        return collector.getIssues().stream()
                .filter(i -> MappingIssueCollector.SEVERITY_ERROR.equals(i.severity()))
                .toList();
    }

    @Test
    public void lenientCollectorRecordsTheFailureWithContextAndContinues() {
        final MappingIssueCollector collector = new MappingIssueCollector();

        engine.handleMappingIterations(List.of(hardcodedHelper(BROKEN), hardcodedHelper("healthy")), flat(),
                Spec.Version.R4, collector);

        final List<MappingIssueCollector.MappingIssue> errors = errors(collector);
        Assert.assertEquals(1, errors.size());
        final MappingIssueCollector.MappingIssue error = errors.get(0);
        Assert.assertEquals(MappingIssueCollector.CODE_EXCEPTION, error.code());
        final String diagnostics = error.diagnostics();
        Assert.assertTrue(diagnostics, diagnostics.contains("mapping 'broken'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("model mapper 'Body weight'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("archetype 'openEHR-EHR-OBSERVATION.body_weight.v2'"));
        Assert.assertTrue(diagnostics, diagnostics.contains(
                "openEHR '$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0004]'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("FHIR '$resource.value'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("while mapping to FHIR"));
        Assert.assertTrue(diagnostics, diagnostics.contains("NullPointerException (reference "));
        Assert.assertFalse("internal exception text leaked: " + diagnostics, diagnostics.contains("OPERATIONALTEMPLATE"));

        // the next mapping in the list was still executed
        Mockito.verify(instantiator).instantiateElement(
                ArgumentMatchers.argThat(h -> "healthy".equals(h.getMappingName())),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.any());
        Mockito.verify(populator).populateElement(
                ArgumentMatchers.argThat(h -> "healthy".equals(h.getMappingName())),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.any());
    }

    @Test
    public void failFastCollectorThrowsWithTheSameContext() {
        try {
            engine.handleMappingIterations(List.of(hardcodedHelper(BROKEN), hardcodedHelper("healthy")), flat(),
                    Spec.Version.R4, MappingIssueCollector.failFast());
            Assert.fail("expected MappingExecutionException");
        } catch (final MappingExecutionException e) {
            Assert.assertEquals(BROKEN, e.getContext().mappingName());
            Assert.assertEquals("Body weight", e.getContext().modelMapper());
            Assert.assertTrue(e.getCause() instanceof NullPointerException);
            Assert.assertFalse(e.getMessage(), e.getMessage().contains("OPERATIONALTEMPLATE"));
        }
        // fail fast: the mapping after the broken one was not reached
        Mockito.verify(instantiator, Mockito.never()).instantiateElement(
                ArgumentMatchers.argThat(h -> "healthy".equals(h.getMappingName())),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.any());
    }

    /**
     * When a child mapping fails, the reported context is the child's (the innermost one), not the parent's.
     */
    @Test
    public void nestedFailureReportsTheChildContext() {
        final MappingHelper parent = hardcodedHelper("parent");
        parent.setManualFhirValue(null);
        parent.setChildren(new ArrayList<>(List.of(hardcodedHelper(BROKEN), hardcodedHelper("sibling"))));

        final MappingIssueCollector lenient = new MappingIssueCollector();
        engine.handleMappingIterations(List.of(parent), flat(), Spec.Version.R4, lenient);

        final List<MappingIssueCollector.MappingIssue> errors = errors(lenient);
        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0).diagnostics(), errors.get(0).diagnostics().contains("mapping 'broken'"));
        Mockito.verify(instantiator).instantiateElement(
                ArgumentMatchers.argThat(h -> "sibling".equals(h.getMappingName())),
                ArgumentMatchers.any(), ArgumentMatchers.anyInt(), ArgumentMatchers.any());

        try {
            engine.handleMappingIterations(List.of(parent), flat(), Spec.Version.R4, MappingIssueCollector.failFast());
            Assert.fail("expected MappingExecutionException");
        } catch (final MappingExecutionException e) {
            Assert.assertEquals(BROKEN, e.getContext().mappingName());
        }
    }

    /**
     * Child mappings used to run against a throwaway collector, so their warnings never reached the caller.
     */
    @Test
    public void childWarningsReachTheCallersCollector() {
        final MappingHelper parent = hardcodedHelper("parent");
        parent.setManualFhirValue(null);
        final MappingHelper child = hardcodedHelper("child");
        child.setManualFhirValue(null);
        child.setProgrammedMapping("not-registered-anywhere");
        parent.setChildren(new ArrayList<>(List.of(child)));

        final MappingIssueCollector collector = new MappingIssueCollector();
        engine.handleMappingIterations(List.of(parent), flat(), Spec.Version.R4, collector);

        Assert.assertTrue(errors(collector).isEmpty());
        Assert.assertTrue(collector.getIssues().stream().anyMatch(i ->
                MappingIssueCollector.SEVERITY_WARNING.equals(i.severity())
                        && i.diagnostics().contains("not-registered-anywhere")));
    }

    @Test
    public void skippedMappingWarningNamesTheMappingContext() {
        final MappingIssueCollector collector = new MappingIssueCollector();

        engine.handleMappingIterations(List.of(hardcodedHelper("healthy")), new JsonObject(), Spec.Version.R4,
                collector);

        Assert.assertEquals(1, collector.getIssues().size());
        final String diagnostics = collector.getIssues().get(0).diagnostics();
        Assert.assertTrue(diagnostics, diagnostics.contains("mapping 'healthy' of model mapper 'Body weight'"));
    }
}
