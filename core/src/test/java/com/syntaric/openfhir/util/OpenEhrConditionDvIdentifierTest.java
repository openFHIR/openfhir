package com.syntaric.openfhir.util;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * An openehrCondition must be able to address a part of a DV_IDENTIFIER ({@code |type}, {@code |issuer},
 * {@code |id}).
 * <p>
 * A care-unit cluster holds several identifiers (e.g. {@code identifierare:0|type = urn:oid:2.5.4.97} and
 * {@code identifierare:1|type = urn:oid:1.2.752.29.4.19}); only the HSA id may become the requester /
 * administrating unit. Narrowing on the {@code |type} part therefore has to keep exactly the matching
 * occurrence and drop the others, instead of excluding every occurrence.
 */
public class OpenEhrConditionDvIdentifierTest {

    private static final String ROOT_FLAT = "vardenhet/vardenhet/identifierare";

    private static final String OID_ORG = "urn:oid:2.5.4.97";
    private static final String OID_HSA = "urn:oid:1.2.752.29.4.19";

    private OpenEhrConditionEvaluator evaluator;

    @Before
    public void setUp() {
        evaluator = new OpenEhrConditionEvaluator(new OpenFhirStringUtils());
    }

    /** A care unit with two identifiers: an organisation number (:0) and an HSA id (:1). */
    private JsonObject twoIdentifierCluster() {
        final JsonObject flat = new JsonObject();
        flat.addProperty("vardenhet/vardenhet/identifierare:0|id", "5561234567");
        flat.addProperty("vardenhet/vardenhet/identifierare:0|type", OID_ORG);
        flat.addProperty("vardenhet/vardenhet/identifierare:0|issuer", "Bolagsverket");
        flat.addProperty("vardenhet/vardenhet/identifierare:1|id", "SE2321000016-3KM3");
        flat.addProperty("vardenhet/vardenhet/identifierare:1|type", OID_HSA);
        flat.addProperty("vardenhet/vardenhet/identifierare:1|issuer", "Inera");
        flat.addProperty("vardenhet/context/start_time", "2026-09-18T10:00:00Z");
        return flat;
    }

    private Condition conditionOn(final String targetAttributeFlatPath, final String criteria) {
        final Condition condition = new Condition()
                .withTargetRoot("$archetype/items[at0003]")
                .withTargetAttributes(List.of(targetAttributeFlatPath))
                .withOperator("one of")
                .withCriterias(criteria);
        condition.setTargetRootFlatPath(ROOT_FLAT);
        condition.getTargetAttributesFlatPath().add(targetAttributeFlatPath);
        return condition;
    }

    /**
     * The core of the bug: narrowing on {@code |type} used to exclude every occurrence. The occurrence
     * carrying the HSA id must survive.
     */
    @Test
    public void narrowingOnTypeKeepsOnlyTheMatchingIdentifier() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(twoIdentifierCluster(),
                                                                     conditionOn("|type", OID_HSA));

        Assert.assertEquals("the HSA identifier must be kept", OID_HSA,
                            narrowed.get("vardenhet/vardenhet/identifierare:1|type").getAsString());
        Assert.assertEquals("the whole matching occurrence must be kept, not just the |type part",
                            "SE2321000016-3KM3",
                            narrowed.get("vardenhet/vardenhet/identifierare:1|id").getAsString());
        Assert.assertFalse("the non-matching organisation-number identifier must be dropped",
                           narrowed.has("vardenhet/vardenhet/identifierare:0|type"));
        Assert.assertFalse("the non-matching occurrence must be dropped as a whole",
                           narrowed.has("vardenhet/vardenhet/identifierare:0|id"));
    }

    /** Narrowing on the other identifier type selects the other occurrence — and only that one. */
    @Test
    public void narrowingOnTypeSelectsTheOtherIdentifierWhenItsCriteriaIsGiven() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(twoIdentifierCluster(),
                                                                     conditionOn("|type", OID_ORG));

        Assert.assertEquals(OID_ORG, narrowed.get("vardenhet/vardenhet/identifierare:0|type").getAsString());
        Assert.assertFalse(narrowed.has("vardenhet/vardenhet/identifierare:1|type"));
    }

    /** Other DV_IDENTIFIER parts must work the same way. */
    @Test
    public void narrowingOnIssuerWorksTheSameWay() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(twoIdentifierCluster(),
                                                                     conditionOn("|issuer", "Inera"));

        Assert.assertEquals("Inera", narrowed.get("vardenhet/vardenhet/identifierare:1|issuer").getAsString());
        Assert.assertFalse(narrowed.has("vardenhet/vardenhet/identifierare:0|issuer"));
    }

    /** Entries outside the narrowed subtree are context and must pass through untouched. */
    @Test
    public void contextOutsideTheNarrowedSubtreeIsPreserved() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(twoIdentifierCluster(),
                                                                     conditionOn("|type", OID_HSA));

        Assert.assertTrue("context entries outside the identifier subtree must be preserved",
                          narrowed.has("vardenhet/context/start_time"));
    }

    /** When no identifier carries the requested type, nothing from the cluster may be emitted. */
    @Test
    public void noIdentifierMatchingTheCriteriaYieldsNothing() {
        final JsonObject narrowed = evaluator.splitByOpenEhrCondition(twoIdentifierCluster(),
                                                                     conditionOn("|type", "urn:oid:9.9.9.9"));

        Assert.assertFalse(narrowed.has("vardenhet/vardenhet/identifierare:0|type"));
        Assert.assertFalse(narrowed.has("vardenhet/vardenhet/identifierare:1|type"));
    }
}
