package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * https://github.com/openFHIR/openfhir/issues/88
 * <p>
 * CLUSTER.multiple_coding_icd10gm.v1 stores the Mehrfachcodierung kennzeichen as a single
 * DV_CODED_TEXT leaf, so its flat representation is a pipe attribute
 * ({@code .../mehrfachkodierungkennzeichen|code}) rather than a nested object with children.
 * The narrowing must still select the branch whose criteria matches the stored code.
 */
public class Issue88OpenEhrConditionEvaluatorTest {

    private static final String ROOT_FLAT =
            "diagnose/diagnose[n]/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen";

    private OpenEhrConditionEvaluator evaluator;

    @Before
    public void setUp() {
        evaluator = new OpenEhrConditionEvaluator(new OpenFhirStringUtils());
    }

    private JsonObject flat() {
        final JsonObject flat = new JsonObject();
        flat.addProperty("diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|value", "†");
        flat.addProperty("diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code", "at0002");
        flat.addProperty("diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|terminology", "local");
        flat.addProperty("diagnose/diagnose:0/kodierte_diagnose|code", "E10.9");
        return flat;
    }

    private Condition conditionFor(final String criteria) {
        final Condition condition = new Condition()
                .withTargetRoot("$archetype/items[at0001]")
                .withTargetAttributes(List.of("defining_code/code_string"))
                .withOperator("one of")
                .withCriterias(criteria);
        condition.setTargetRootFlatPath(ROOT_FLAT);
        condition.getTargetAttributesFlatPath().add("|code");
        return condition;
    }

    /** The branch whose criteria matches the stored code (at0002) must be kept. */
    @Test
    public void matchingBranchIsKept() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(flat(), conditionFor("at0002"));
        Assert.assertFalse("branch at0002 matches the stored code and must not be filtered out",
                           narrowed.entrySet().isEmpty());
    }

    /** Branches whose criteria do NOT match the stored code must be filtered out entirely. */
    @Test
    public void nonMatchingBranchesAreFilteredOut() {
        for (final String nonMatching : List.of("at0003", "at0004")) {
            final JsonObject narrowed = evaluator.splitByOpenEhrCondition(flat(), conditionFor(nonMatching));
            Assert.assertTrue("branch " + nonMatching
                                      + " does not match the stored code at0002 and must be filtered out",
                              narrowed.entrySet().isEmpty());
        }
    }

    /**
     * A condition that was never amended against a web template has no targetRootFlatPath. That must
     * not blow up the whole conversion with a NullPointerException.
     */
    @Test
    public void unresolvedConditionRootDoesNotThrow() {
        final Condition unresolved = new Condition()
                .withTargetRoot("$archetype/items[at0001]")
                .withTargetAttributes(List.of("defining_code/code_string"))
                .withOperator("one of")
                .withCriterias("at0002");
        // targetRootFlatPath deliberately left unset, as happens when webTemplate is null

        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(flat(), unresolved);

        Assert.assertNotNull(narrowed);
    }
}
