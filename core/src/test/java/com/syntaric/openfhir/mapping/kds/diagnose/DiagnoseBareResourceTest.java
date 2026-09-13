package com.syntaric.openfhir.mapping.kds.diagnose;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

/**
 * Covers issue #118: a bare resource (not wrapped in a Bundle) whose type matches the one the start archetype's
 * model mapper generates used to map to nothing at all.
 * <p>
 * The KDS Diagnose start archetype {@code EVALUATION.problem_diagnosis.v1} declares
 * {@code structureDefinition: .../Condition} and carries a {@code preprocessor.fhirCondition}. That combination
 * took the branch of {@code findStartingResource} which evaluates a hardcoded {@code Bundle.entry.resource...}
 * path — matching nothing when the input is the bare Condition itself.
 */
public class DiagnoseBareResourceTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT_MAPPING = "/kds/core/projects/org.highmed/KDS/diagnose/KDS_diagnose.context.yaml";
    final String OPT = "/kds/diagnose/KDS_Diagnose.opt";
    final String CONDITION_BUNDLE = "/kds/diagnose/toOpenEHR/input/Condition-mii-exa-test-data-patient-1-diagnose-1.json";

    @SneakyThrows
    @Override
    public void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    /**
     * The same Condition, posted bare and posted inside a Bundle, must map to the same flat composition.
     */
    @Test
    public void bareConditionMapsSameAsConditionInABundle() {
        final Bundle bundle = getTestBundle(CONDITION_BUNDLE);
        final Condition bare = (Condition) bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Condition.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixture carries no Condition"));

        final JsonObject fromBundle = toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate);
        final JsonObject fromBare = toOpenEhr.fhirToFlatJsonObject(context, bare, webTemplate);

        Assert.assertFalse("the Bundle form must map something, otherwise this test proves nothing",
                fromBundle.entrySet().isEmpty());
        Assert.assertEquals(fromBundle, fromBare);
    }

    /**
     * The bare form must not report gaps the Bundle form doesn't — this template reports some either way, so
     * what matters is that posting bare adds none of its own.
     */
    @Test
    public void bareConditionReportsTheSameIssuesAsTheBundleForm() {
        final Bundle bundle = getTestBundle(CONDITION_BUNDLE);
        final Condition bare = (Condition) bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Condition.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError("fixture carries no Condition"));

        final MappingIssueCollector bareIssues = new MappingIssueCollector();
        toOpenEhr.fhirToFlatJsonObject(context, bare, webTemplate, bareIssues);
        final MappingIssueCollector bundleIssues = new MappingIssueCollector();
        toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate, bundleIssues);

        Assert.assertEquals(bundleIssues.getIssues(), bareIssues.getIssues());
    }

    /**
     * A bare resource of the wrong type is still nothing to map — reported, not silently mapped as if it were
     * the expected type.
     */
    @Test
    public void bareResourceOfWrongTypeIsReported() {
        final MappingIssueCollector issueCollector = new MappingIssueCollector();
        final JsonObject flat = toOpenEhr.fhirToFlatJsonObject(context, new Observation(), webTemplate,
                issueCollector);

        Assert.assertTrue(flat.entrySet().isEmpty());
        Assert.assertFalse(issueCollector.isEmpty());
    }
}
