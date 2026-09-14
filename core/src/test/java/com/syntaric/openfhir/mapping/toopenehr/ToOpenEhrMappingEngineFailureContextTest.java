package com.syntaric.openfhir.mapping.toopenehr;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.custommappings.CustomMapping;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import com.syntaric.openfhir.util.FhirConditionEvaluator;
import com.syntaric.openfhir.util.MappingExecutionException;
import com.syntaric.openfhir.util.OpenEhrPopulator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Issue #120: a runtime failure inside one {@code $toopenehr} mapping is reported with the mapping's context, the
 * remaining mappings are still processed, custom-mapping-code failures are no longer swallowed, and a FHIRPath
 * expression that cannot be evaluated is reported as a warning naming the mapping and the expression.
 */
public class ToOpenEhrMappingEngineFailureContextTest {

    private static final String NPE_TEXT = "Cannot invoke \"org.openehr.schemas.v1.OPERATIONALTEMPLATE.getLanguage()\"";
    private static final String BROKEN = "broken";

    private ToOpenEhrMappingEngine engine;
    private OpenEhrPopulator populator;
    private CustomMappingRegistry customMappingRegistry;

    @Before
    public void setUp() {
        final OpenFhirStringUtils stringUtils = new OpenFhirStringUtils();
        final OpenFhirMapperUtils mapperUtils = new OpenFhirMapperUtils();

        populator = Mockito.mock(OpenEhrPopulator.class);
        // the "broken" mapping blows up inside the populator; every other mapping writes its path into the flat
        Mockito.doAnswer(invocation -> {
            final MappingHelper helper = invocation.getArgument(0);
            if (BROKEN.equals(helper.getMappingName())) {
                throw new NullPointerException(NPE_TEXT);
            }
            final String path = invocation.getArgument(1);
            final JsonObject flat = invocation.getArgument(5);
            flat.add(path, new JsonPrimitive("mapped"));
            return null;
        }).when(populator).setOpenEhrValue(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any(), ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(), ArgumentMatchers.any(),
                ArgumentMatchers.any());
        customMappingRegistry = Mockito.mock(CustomMappingRegistry.class);
        Mockito.when(customMappingRegistry.find(ArgumentMatchers.any())).thenReturn(Optional.empty());

        engine = new ToOpenEhrMappingEngine(
                new FhirContextRegistry(),
                stringUtils,
                populator,
                mapperUtils,
                new ToOpenEhrNullFlavour(stringUtils, populator),
                customMappingRegistry,
                (section, context, elapsedMs) -> { /* no-op metrics in tests */ },
                new FhirConditionEvaluator(stringUtils));
    }

    /**
     * A helper whose FHIR path is empty maps the iterated resource itself; with a manual openEHR value it reaches
     * the populator deterministically.
     */
    private static MappingHelper hardcodedHelper(final String mappingName, final Observation resource) {
        final MappingHelper helper = new MappingHelper();
        helper.setModelMetadataName("Body weight");
        helper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper.setMappingName(mappingName);
        helper.setOriginalOpenEhrPath("$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0004]");
        helper.setOriginalFhirPath("$resource");
        helper.setFullOpenEhrFlatPath("body_weight/any_event:0/" + mappingName);
        helper.setFlatPathPipeSuffix("");
        helper.setManualOpenEhrValue("x");
        helper.setPossibleRmTypes(List.of(FhirConnectConst.DV_TEXT));
        helper.setGeneratingFhirResource(resource);
        return helper;
    }

    private static Observation observation() {
        final Observation observation = new Observation();
        observation.setValue(new Quantity().setValue(70));
        return observation;
    }

    private JsonObject run(final List<MappingHelper> helpers, final Observation resource,
                           final MappingIssueCollector collector) {
        final JsonObject flat = new JsonObject();
        engine.mapToOpenEhr(helpers, flat, resource, true, new HashMap<>(), Spec.Version.R4, collector);
        return flat;
    }

    private static List<MappingIssueCollector.MappingIssue> errors(final MappingIssueCollector collector) {
        return collector.getIssues().stream()
                .filter(i -> MappingIssueCollector.SEVERITY_ERROR.equals(i.severity()))
                .toList();
    }

    private static List<MappingIssueCollector.MappingIssue> warnings(final MappingIssueCollector collector) {
        return collector.getIssues().stream()
                .filter(i -> MappingIssueCollector.SEVERITY_WARNING.equals(i.severity()))
                .toList();
    }

    @Test
    public void lenientCollectorRecordsTheFailureWithContextAndContinues() {
        final Observation resource = observation();
        final MappingIssueCollector collector = new MappingIssueCollector();

        final JsonObject flat = run(List.of(hardcodedHelper(BROKEN, resource), hardcodedHelper("healthy", resource)),
                resource, collector);

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
        Assert.assertTrue(diagnostics, diagnostics.contains("FHIR '$resource'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("while mapping to openEHR"));
        Assert.assertTrue(diagnostics, diagnostics.contains("NullPointerException (reference "));
        Assert.assertFalse("internal exception text leaked: " + diagnostics, diagnostics.contains("OPERATIONALTEMPLATE"));

        // the next mapping in the list was still executed and its value landed in the flat composition
        Assert.assertTrue(flat.toString(), flat.has("body_weight/any_event:0/healthy"));
        // something was mapped, so no "nothing mapped" warning
        Assert.assertTrue(warnings(collector).toString(), warnings(collector).isEmpty());
    }

    @Test
    public void failFastCollectorThrowsWithTheSameContext() {
        final Observation resource = observation();
        try {
            run(List.of(hardcodedHelper(BROKEN, resource), hardcodedHelper("healthy", resource)), resource,
                    MappingIssueCollector.failFast());
            Assert.fail("expected MappingExecutionException");
        } catch (final MappingExecutionException e) {
            Assert.assertEquals(BROKEN, e.getContext().mappingName());
            Assert.assertEquals("Body weight", e.getContext().modelMapper());
            Assert.assertEquals(FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR, e.getContext().direction());
            Assert.assertTrue(e.getCause() instanceof NullPointerException);
            Assert.assertFalse(e.getMessage(), e.getMessage().contains("OPERATIONALTEMPLATE"));
        }
        Mockito.verify(populator, Mockito.never()).setOpenEhrValue(
                ArgumentMatchers.argThat(h -> "healthy".equals(h.getMappingName())),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean(),
                ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    /**
     * Custom mapping code that throws used to be swallowed by a catch-all that only logged.
     */
    @Test
    public void customMappingCodeFailureIsReportedWithContext() {
        final Observation resource = observation();
        final MappingHelper helper = hardcodedHelper("programmed", resource);
        helper.setManualOpenEhrValue(null);
        helper.setProgrammedMapping("explode");
        Mockito.when(customMappingRegistry.find("explode")).thenReturn(Optional.of(new CustomMapping() {
            @Override
            public Set<String> mappingCodes() {
                return Set.of("explode");
            }

            @Override
            public boolean applyFhirToOpenEhrMapping(final MappingHelper mappingHelper, final IBase fhirValue,
                                                     final List<String> possibleRmTypes, final JsonObject flat,
                                                     final OpenEhrPopulator populator,
                                                     final OpenFhirMapperUtils mapperUtils,
                                                     final OpenFhirStringUtils stringUtils) {
                throw new IllegalStateException("custom mapping exploded");
            }
        }));

        final MappingIssueCollector collector = new MappingIssueCollector();
        run(List.of(helper, hardcodedHelper("healthy", resource)), resource, collector);

        final List<MappingIssueCollector.MappingIssue> errors = errors(collector);
        Assert.assertEquals(1, errors.size());
        Assert.assertEquals(MappingIssueCollector.CODE_EXCEPTION, errors.get(0).code());
        Assert.assertTrue(errors.get(0).diagnostics(), errors.get(0).diagnostics().contains("mapping 'programmed'"));
        Assert.assertTrue(errors.get(0).diagnostics(), errors.get(0).diagnostics().contains("IllegalStateException"));
        Assert.assertFalse(errors.get(0).diagnostics(), errors.get(0).diagnostics().contains("custom mapping exploded"));

        try {
            run(List.of(helper), resource, MappingIssueCollector.failFast());
            Assert.fail("expected MappingExecutionException");
        } catch (final MappingExecutionException e) {
            Assert.assertEquals("programmed", e.getContext().mappingName());
        }
    }

    /**
     * A FHIRPath expression the engine cannot evaluate is a mapper-authoring problem: it must not abort the
     * request, but the caller has to learn which mapping and which expression are at fault.
     */
    @Test
    public void unevaluableFhirPathIsReportedAsWarningNamingTheExpression() {
        final Observation resource = observation();
        final MappingHelper badPath = hardcodedHelper("bad-path", resource);
        badPath.setManualOpenEhrValue(null);
        badPath.setOriginalFhirPath("$resource.value.unknownFn(");
        badPath.setFhir("value.unknownFn(");

        final MappingIssueCollector collector = new MappingIssueCollector();
        final JsonObject flat = run(List.of(badPath, hardcodedHelper("healthy", resource)), resource, collector);

        Assert.assertTrue(errors(collector).isEmpty());
        final List<MappingIssueCollector.MappingIssue> warnings = warnings(collector);
        Assert.assertEquals(warnings.toString(), 1, warnings.size());
        Assert.assertEquals(MappingIssueCollector.CODE_INCOMPLETE, warnings.get(0).code());
        final String diagnostics = warnings.get(0).diagnostics();
        Assert.assertTrue(diagnostics, diagnostics.contains("mapping 'bad-path' of model mapper 'Body weight'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("FHIRPath 'value.unknownFn('"));
        Assert.assertTrue("should carry the FHIRPath engine's message: " + diagnostics,
                diagnostics.contains("could not be evaluated: ") && diagnostics.length() > diagnostics.indexOf("could not be evaluated: ") + 24);

        // the next helper was still mapped
        Assert.assertTrue(flat.toString(), flat.has("body_weight/any_event:0/healthy"));
    }

    /**
     * Same for the conditions branch: a path-filtering fhirCondition whose attribute path is malformed.
     */
    @Test
    public void unevaluableConditionPathIsReportedAsWarningNamingTheExpression() {
        final Observation resource = new Observation();
        resource.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding("http://loinc.org", "9999-9", "x")))
                .setValue(new Quantity().setValue(1));

        final MappingHelper badCondition = hardcodedHelper("bad-condition", resource);
        badCondition.setManualOpenEhrValue(null);
        badCondition.setOriginalFhirPath("$resource.component.value");
        badCondition.setFullFhirPath("Observation.component.value");
        badCondition.setFhir("component.value");
        final Condition condition = new Condition();
        condition.setOperator(FhirConnectConst.CONDITION_OPERATOR_ONE_OF);
        condition.setTargetRoot("Observation.component");
        condition.setTargetAttributes(List.of("code..coding"));
        condition.setCriterias(List.of("9999-9"));
        badCondition.setFhirConditions(List.of(condition));

        final MappingIssueCollector collector = new MappingIssueCollector();
        final JsonObject flat = run(List.of(badCondition, hardcodedHelper("healthy", resource)), resource, collector);

        Assert.assertTrue(errors(collector).isEmpty());
        final List<MappingIssueCollector.MappingIssue> warnings = warnings(collector);
        Assert.assertEquals(warnings.toString(), 1, warnings.size());
        final String diagnostics = warnings.get(0).diagnostics();
        Assert.assertTrue(diagnostics, diagnostics.contains("mapping 'bad-condition'"));
        Assert.assertTrue(diagnostics, diagnostics.contains("FHIRPath 'component.value'"));
        Assert.assertTrue(flat.toString(), flat.has("body_weight/any_event:0/healthy"));
    }
}
