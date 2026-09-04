package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.schema.model.Condition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Pins how {@code operator: "not empty"} behaves when its {@code targetRoot} names a node that
 * repeats ({@code 0..*}) versus one that does not.
 * <p>
 * Motivation: in the eHealth Africa mappings, a sub-extension guarded on
 * {@code targetRoot: "$openehrRoot"} + {@code targetAttribute: "items[at0178]"} (Safety override,
 * {@code 0..*}) was suppressed entirely, while the same shape on {@code items[at0150]} (Total daily
 * effective dose, {@code 0..1}) worked. The matcher itself is not the cause —
 * {@link OpenFhirStringUtils#getAllEntriesThatMatchIgnoringPipe} uses {@code startsWith} and happily
 * matches an index-free prefix against an indexed entry (see
 * {@code OpenFhirStringUtilsTest#getAllEntriesThatMatchIgnoringPipe_repeatingNodes}).
 * <p>
 * These tests exercise {@link OpenEhrConditionEvaluator#splitByOpenEhrCondition} directly to locate
 * where the two cases actually diverge.
 */
public class NotEmptyOnRepeatingNodeTest {

    private OpenEhrConditionEvaluator evaluator;

    @Before
    public void setUp() {
        evaluator = new OpenEhrConditionEvaluator(new OpenFhirStringUtils());
    }

    /** The medication-safety block: one repeating child (safety_override) and one non-repeating. */
    private JsonObject flat() {
        final JsonObject flat = new JsonObject();
        flat.addProperty("prescription/medication_order:0/order:0/medication_safety/safety_override:0/override_reason:0",
                         "Lorem ipsum");
        flat.addProperty("prescription/medication_order:0/order:0/medication_safety/safety_override:0/overriden_safety_advice",
                         "Lorem ipsum");
        flat.addProperty("prescription/medication_order:0/order:0/medication_safety/total_daily_effective_dose/purpose",
                         "Lorem ipsum");
        flat.addProperty("prescription/medication_order:0/order:0/medication_safety/exceptional_safety_override",
                         "true");
        return flat;
    }

    private Condition notEmptyOn(final String rootFlatPath, final String attributeFlatPath) {
        final Condition condition = new Condition()
                .withTargetRoot("$openehrRoot")
                .withOperator("not empty");
        condition.setTargetRootFlatPath(rootFlatPath);
        condition.getTargetAttributesFlatPath().add(attributeFlatPath);
        return condition;
    }

    /**
     * Baseline: guarding on a NON-repeating child keeps the block.
     */
    @Test
    public void notEmptyOnNonRepeatingChildKeepsTheBlock() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(
                flat(),
                notEmptyOn("prescription/medication_order:0/order:0/medication_safety",
                           "total_daily_effective_dose"));

        Assert.assertFalse("total_daily_effective_dose is present, so the condition must hold",
                           narrowed.entrySet().isEmpty());
    }

    /**
     * The case under investigation: guarding on a REPEATING child, whose flat entries carry an
     * occurrence index the resolved targetAttribute does not.
     */
    @Test
    public void notEmptyOnRepeatingChildKeepsTheBlock() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(
                flat(),
                notEmptyOn("prescription/medication_order:0/order:0/medication_safety",
                           "safety_override"));

        Assert.assertFalse("safety_override:0 is present, so a 'not empty' condition on"
                           + " safety_override must hold — the occurrence index must not hide it",
                           narrowed.entrySet().isEmpty());
    }

    /**
     * A node that genuinely is not there must still filter the block out, so the assertion above
     * cannot be satisfied by the condition simply never filtering anything.
     */
    @Test
    public void notEmptyOnAbsentChildFiltersTheBlockOut() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(
                flat(),
                notEmptyOn("prescription/medication_order:0/order:0/medication_safety",
                           "no_such_node"));

        Assert.assertTrue("no_such_node is absent, so the condition must filter the block out",
                          narrowed.entrySet().isEmpty());
    }
}
