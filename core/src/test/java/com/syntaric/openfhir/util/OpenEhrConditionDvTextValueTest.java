package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * A DV_TEXT has no pipe attributes in flat format: its value sits on the node itself
 * ({@code .../category}), so a condition on its {@code value} has to read the node.
 */
public class OpenEhrConditionDvTextValueTest {

    private static final String ROOT_FLAT = "ips/allergies/adverse_reaction_risk[n]/category";

    private OpenEhrConditionEvaluator evaluator;

    @Before
    public void setUp() {
        evaluator = new OpenEhrConditionEvaluator(new OpenFhirStringUtils());
    }

    private JsonObject flat() {
        final JsonObject flat = new JsonObject();
        flat.addProperty("ips/allergies/adverse_reaction_risk:0/category", "Food");
        flat.addProperty("ips/allergies/adverse_reaction_risk:0/substance", "Peanut");
        return flat;
    }

    private Condition conditionFor(final String attributeFlatPath, final String criteria) {
        final Condition condition = new Condition()
                .withTargetRoot("$archetype/data[at0001]/items[at0120]")
                .withTargetAttributes(List.of("value"))
                .withOperator("one of")
                .withCriterias(criteria);
        condition.setTargetRootFlatPath(ROOT_FLAT);
        condition.getTargetAttributesFlatPath().add(attributeFlatPath);
        return condition;
    }

    @Test
    public void matchingValueIsKept() {
        for (final String attributeFlatPath : List.of("value", "|value")) {
            final JsonObject narrowed = evaluator.splitByOpenEhrCondition(flat(),
                                                                          conditionFor(attributeFlatPath, "Food"));
            Assert.assertEquals(2, narrowed.entrySet().size());
        }
    }

    @Test
    public void nonMatchingValueIsFilteredOut() {
        for (final String attributeFlatPath : List.of("value", "|value")) {
            final JsonObject narrowed = evaluator.splitByOpenEhrCondition(flat(),
                                                                          conditionFor(attributeFlatPath, "Medication"));
            Assert.assertTrue(narrowed.entrySet().isEmpty());
        }
    }
}
