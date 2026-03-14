package com.syntaric.openfhir.mapping.tofhir;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.syntaric.openfhir.mapping.custommappings.CustomMappingRegistry;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.mapping.helpers.OpenEhrFlatPathDataExtractor;
import com.syntaric.openfhir.mapping.helpers.parser.CodedParser;
import com.syntaric.openfhir.mapping.helpers.parser.FhirValueReaders;
import com.syntaric.openfhir.mapping.helpers.parser.IdentifierParser;
import com.syntaric.openfhir.mapping.helpers.parser.MediaParser;
import com.syntaric.openfhir.mapping.helpers.parser.QuantityParser;
import com.syntaric.openfhir.mapping.helpers.parser.TemporalParser;
import com.syntaric.openfhir.mapping.helpers.parser.TextParser;
import com.syntaric.openfhir.mapping.helpers.parser.ValueToFHIRParser;
import com.syntaric.openfhir.terminology.NoOpTerminologyTranslator;
import com.syntaric.openfhir.util.FhirInstanceCreator;
import com.syntaric.openfhir.util.FhirInstanceCreatorUtility;
import com.syntaric.openfhir.util.FhirInstancePopulator;
import com.syntaric.openfhir.util.NoOpPrePostFhirInstancePopulator;
import com.syntaric.openfhir.util.OpenEhrConditionEvaluator;
import com.syntaric.openfhir.util.OpenFhirMapperUtils;
import com.syntaric.openfhir.util.OpenFhirStringUtils;
import com.syntaric.openfhir.fc.schema.model.Condition;
import java.util.List;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ToFhirMappingEngine#getRelevantJsonObject}.
 * <p>
 * The method takes a flat openEHR JsonObject and filters it based on:
 * - an optional Preprocessor openEhrCondition (applied first)
 * - the MappingHelper's openEhrConditions (applied second)
 * <p>
 * Two operator semantics are tested:
 * - "empty"  → a specific path must NOT exist in the JsonObject for a group to be kept
 * - "one of" → split by openEhrCondition.targetRoot so only groups whose targetAttribute
 * equals the criteria are kept in the returned JsonObject
 */
public class ToFhirMappingEngineTest {

    private ToFhirMappingEngine engine;

    @Before
    public void setUp() {
        final OpenFhirStringUtils openFhirStringUtils = new OpenFhirStringUtils();
        final FhirInstanceCreatorUtility fhirInstanceCreatorUtility = new FhirInstanceCreatorUtility(
                openFhirStringUtils);
        final FhirInstanceCreator fhirInstanceCreator = new FhirInstanceCreator(openFhirStringUtils,
                                                                                fhirInstanceCreatorUtility);
        final OpenFhirMapperUtils openFhirMapperUtils = new OpenFhirMapperUtils();
        final FhirValueReaders readers = new FhirValueReaders(openFhirMapperUtils);
        final FhirContext ctx = FhirContext.forR4();
        engine = new ToFhirMappingEngine(
                new OpenEhrConditionEvaluator(openFhirStringUtils),
                new FhirInstanceCreator(openFhirStringUtils,
                                        fhirInstanceCreatorUtility),
                fhirInstanceCreatorUtility,
                new FhirPathR4(ctx),
                new OpenEhrFlatPathDataExtractor(openFhirStringUtils,
                                                 new ValueToFHIRParser(
                                                         new TemporalParser(
                                                                 readers),
                                                         new QuantityParser(
                                                                 readers),
                                                         new CodedParser(readers),
                                                         new MediaParser(readers),
                                                         new TextParser(readers),
                                                         new IdentifierParser(
                                                                 readers)
                                                 )),
                openFhirStringUtils,
                new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new NoOpTerminologyTranslator()),
                new ToFhirInstantiator(fhirInstanceCreator),
                new CustomMappingRegistry(),
                openFhirMapperUtils);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Build a JsonObject from a raw JSON string.
     */
    private static JsonObject json(final String raw) {
        return JsonParser.parseString(raw).getAsJsonObject();
    }

    /**
     * Build a MappingHelper with a single openEhrCondition.
     */
    private static MappingHelper helperWith(final Condition condition) {
        final MappingHelper h = new MappingHelper();
        h.setOpenEhrConditions(List.of(condition));
        return h;
    }

    /**
     * Build a MappingHelper with NO openEhrConditions.
     */
    private static MappingHelper helperNoCondition() {
        return new MappingHelper();
    }

    /**
     * Build a Condition with the given fields.
     */
    private static Condition condition(final String targetRootFlatPath,
                                       final String targetAttributeFlatPath,
                                       final String operator,
                                       final String criteria) {
        final Condition c = new Condition();
        c.setOperator(operator);
        c.setCriteria(criteria);
        // targetRoot drives narrowingCriteria — use the flat path directly since these tests
        // construct conditions as HelpersCreator would after flat-path resolution
        c.setTargetRoot(targetRootFlatPath);
        c.setTargetAttribute(targetAttributeFlatPath);
        c.setTargetRootFlatPath(targetRootFlatPath);
        c.getTargetAttributesFlatPath().add(targetAttributeFlatPath);
        return c;
    }

    // -----------------------------------------------------------------------
    // Shared flat JSON with two diagnose groups
    // -----------------------------------------------------------------------

    /**
     * Two diagnose entries:
     * diagnose:0  → diagnosestatus code = "at0016" (Preliminary)
     * diagnose:1  → diagnosestatus code = "at0088" (Refuted)
     * <p>
     * Each group also has a few other sibling entries to ensure filtering is group-scoped.
     */
    private static JsonObject twoDiagnoseGroups() {
        return json("""
                            {
                              "diagnose/context/start_time": "2022-02-03T04:05:06",
                              "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                              "diagnose/diagnose:0/kodierte_diagnose|value": "Cholera",
                              "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                              "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "Preliminary",
                              "diagnose/diagnose:0/klinischer_status/diagnosestatus|terminology": "local",
                              "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                              "diagnose/diagnose:1/kodierte_diagnose|value": "Refuted disease",
                              "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                              "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "Refuted",
                              "diagnose/diagnose:1/klinischer_status/diagnosestatus|terminology": "local"
                            }
                            """);
    }

    // -----------------------------------------------------------------------
    // No-condition passthrough
    // -----------------------------------------------------------------------

    @Test
    public void getRelevantJsonObject_noCondition_returnsFullJsonObject() {
        final JsonObject flat = twoDiagnoseGroups();
        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperNoCondition());

        // Unchanged — all keys present
        Assert.assertEquals(flat.size(), result.size());
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
    }

    // -----------------------------------------------------------------------
    // "one of" operator — split by targetRoot group, keep only matching criteria
    // -----------------------------------------------------------------------

    /**
     * Condition: targetRoot = "diagnose/diagnose[n]/klinischer_status"
     * targetAttribute = "diagnosestatus|code"
     * operator = "one of"
     * criteria = "at0016"   (Preliminary)
     * <p>
     * The targetRoot regex resolves to both diagnose:0 and diagnose:1 groups.
     * Only diagnose:0 has diagnosestatus|code = "at0016", so the returned JsonObject
     * must contain only entries that start with "diagnose/diagnose:0/klinischer_status".
     */
    @Test
    public void getRelevantJsonObject_oneOf_keepsOnlyMatchingGroup() {
        final JsonObject flat = twoDiagnoseGroups();

        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // diagnose:0 klinischer_status entries must be present
        Assert.assertTrue(result.has("diagnose/context/start_time"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|value"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|value"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|terminology"));

        // diagnose:1 klinischer_status entries must NOT be present (different criteria value)
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|value"));
    }

    /**
     * Same flat JSON, but criteria = "at0088" (Refuted) — only diagnose:1 must survive.
     */
    @Test
    public void getRelevantJsonObject_oneOf_keepsOnlySecondGroup() {
        final JsonObject flat = twoDiagnoseGroups();

        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0088");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertTrue(result.has("diagnose/context/start_time"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/kodierte_diagnose|value"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|value"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|terminology"));

        Assert.assertEquals("at0088",
                            result.get("diagnose/diagnose:1/klinischer_status/diagnosestatus|code").getAsString());

        Assert.assertFalse(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
    }

    /**
     * When NO group satisfies the criteria, only context entries remain (no group entries survive).
     * Context entries are always returned even when no group matches.
     */
    @Test
    public void getRelevantJsonObject_oneOf_noMatchingGroup_onlyContextEntries() {
        final JsonObject flat = twoDiagnoseGroups();

        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0999"   // value that doesn't appear in either group
        );

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // Context entry must not be there either if none other are
        Assert.assertFalse(result.has("diagnose/context/start_time"));

        // No group entries survive since no group matched
        Assert.assertFalse(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
    }

    /**
     * When the targetRoot path doesn't exist in the flat JSON at all, the full JsonObject
     * is returned unchanged (no narrowing possible).
     */
    @Test
    public void getRelevantJsonObject_oneOf_targetRootAbsent_returnsFullObject() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/context/start_time": "2022-02-03T04:05:06",
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00"
                                             }
                                             """);

        // targetRoot path doesn't exist in this minimal flat JSON
        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // No narrowing criteria found → full object returned
        Assert.assertEquals(flat.size(), result.size());
    }

    // -----------------------------------------------------------------------
    // "empty" operator — keep only groups where the targetAttribute path is absent
    // -----------------------------------------------------------------------

    /**
     * Flat JSON where diagnose:0 has NO mehrfachkodierungskennzeichen entry (absent),
     * and diagnose:1 DOES have it (present).
     * <p>
     * Condition: targetRoot = "diagnose/diagnose[n]"
     * targetAttribute = "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"
     * operator = "empty"
     * <p>
     * Expected: only diagnose:0 entries survive (the one where the path is absent).
     */
    @Test
    public void getRelevantJsonObject_ifEmpty_keepsGroupWherePathAbsent() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/kodierte_diagnose|value": "Cholera",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/kodierte_diagnose|value": "Bla",
                                               "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0002",
                                               "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|value": "†"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "empty",
                null);

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // diagnose:0 must survive (path absent there)
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|value"));

        // diagnose:1 must be excluded (path present there)
        Assert.assertFalse(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertFalse(result.has(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"));
    }

    /**
     * When both groups have the targeted path, none survives — result is empty.
     */
    @Test
    public void getRelevantJsonObject_ifEmpty_allGroupsHavePath_returnsEmpty() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0003",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0002"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "empty",
                null);

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertEquals(0, result.size());
    }

    /**
     * When neither group has the targeted path, both groups survive — result equals input.
     */
    @Test
    public void getRelevantJsonObject_ifEmpty_neitherGroupHasPath_returnsBoth() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "empty",
                null);

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
    }

    // -----------------------------------------------------------------------
    // "one of" — using the KDS Diagnose statusCoded split scenario
    // (mirrors the real helper: splitByOpenEhrCondition on diagnosestatus)
    // -----------------------------------------------------------------------

    /**
     * Realistic KDS Diagnose scenario: the flat JSON from KDS_Diagnose_multiple_Composition.flat_textvalue.json
     * has two diagnose groups with different diagnosestatus codes.
     * <p>
     * The statusCoded.provisional mapping has:
     * openEhrCondition.targetRoot     = PROBLEM_DIAG/data[at0001]/items[PROBLEM_QUAL]
     * → flat path: "diagnose/diagnose[n]/klinischer_status"
     * openEhrCondition.targetAttribute = "items[at0004]/code_string"
     * → flat attribute path: "diagnose/diagnose[n]/klinischer_status/diagnosestatus"
     * criteria = "at0016"   (Preliminary / unconfirmed)
     * <p>
     * Only entries under diagnose:0 (diagnosestatus code = at0016) must be kept.
     */
    @Test
    public void getRelevantJsonObject_statusCodedScenario_oneOf_preliminary() {
        // Derived from KDS_Diagnose_multiple_Composition.flat_textvalue.json
        final JsonObject flat = json("""
                                             {
                                               "diagnose/context/start_time": "2022-02-03T04:05:06",
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "kodierte_diagnose value",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|terminology": "local",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "Preliminary",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "referenced_kodierte_diagnose value",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|terminology": "local",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "Refuted"
                                             }
                                             """);

        // targetRoot flat path: "diagnose/diagnose[n]/klinischer_status"
        // targetAttribute (the sub-key to check): "diagnosestatus|code" (pipe-subpath)
        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // diagnose:0 klinischer_status entries preserved
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertEquals("at0016",
                            result.get("diagnose/diagnose:0/klinischer_status/diagnosestatus|code").getAsString());

        // diagnose:1 klinischer_status entries excluded
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
    }

    /**
     * Same scenario but criteria = "at0088" (Refuted) — only diagnose:1 must survive.
     */
    @Test
    public void getRelevantJsonObject_statusCodedScenario_oneOf_refuted() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "Preliminary",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "Refuted"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0088");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertTrue(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertEquals("at0088",
                            result.get("diagnose/diagnose:1/klinischer_status/diagnosestatus|code").getAsString());
        Assert.assertFalse(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
    }

    // -----------------------------------------------------------------------
    // "one of" with multiple criteria (criterias list)
    // -----------------------------------------------------------------------

    /**
     * When multiple criteria are specified (criterias list), any group matching any
     * of the criteria must be kept.
     * <p>
     * diagnose:0 → at0016 (Preliminary)
     * diagnose:1 → at0018 (Established)
     * diagnose:2 → at0088 (Refuted)
     * <p>
     * Criteria = ["at0016", "at0018"] → both diagnose:0 and diagnose:1 must survive.
     */
    @Test
    public void getRelevantJsonObject_oneOf_multipleCriterias_keepsBothMatchingGroups() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "Preliminary",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0018",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "Established",
                                               "diagnose/diagnose:2/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:2/klinischer_status/diagnosestatus|value": "Refuted"
                                             }
                                             """);

        final Condition c = new Condition();
        c.setTargetRoot("diagnose/diagnose[n]/klinischer_status");
        c.setTargetAttribute("diagnosestatus|code");
        c.setOperator("one of");
        c.setCriterias(List.of("at0016", "at0018"));
        c.setTargetRootFlatPath("diagnose/diagnose[n]/klinischer_status");
        c.getTargetAttributesFlatPath().add("diagnosestatus|code");
        // criterias: getCriteria() returns criterias.get(0) so engine sees "at0016" as the single criteria

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:2/klinischer_status/diagnosestatus|code"));
    }

    // -----------------------------------------------------------------------
    // "one of" with multiple targetAttributes (OR logic between them)
    // -----------------------------------------------------------------------

    /**
     * When multiple targetAttributes are specified, a group matches if ANY of them satisfies
     * the criteria (OR logic). Each attribute is checked independently against the criteria.
     * <p>
     * diagnose:0 → diagnosestatus|code = "at0016", diagnosestatus|value = "Preliminary"
     * diagnose:1 → diagnosestatus|code = "at0088", diagnosestatus|value = "Refuted"
     * diagnose:2 → diagnosestatus|code = "at0018", diagnosestatus|value = "Established"
     * <p>
     * targetAttributes = ["diagnosestatus|code", "diagnosestatus|value"]
     * criteria = "at0016"
     * <p>
     * diagnose:0: diagnosestatus|code = "at0016" → matches first attribute → kept
     * diagnose:1: neither attribute = "at0016" → excluded
     * diagnose:2: neither attribute = "at0016" → excluded
     */
    @Test
    public void getRelevantJsonObject_oneOf_multipleTargetAttributes_orLogic() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "Preliminary",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "Refuted",
                                               "diagnose/diagnose:2/klinischer_status/diagnosestatus|code": "at0018",
                                               "diagnose/diagnose:2/klinischer_status/diagnosestatus|value": "Established"
                                             }
                                             """);

        final Condition c = new Condition();
        c.setTargetRoot("diagnose/diagnose[n]/klinischer_status");
        c.setOperator("one of");
        c.setCriteria("at0016");
        c.setTargetAttributesFlatPath(List.of("diagnosestatus|code", "diagnosestatus|value"));
        c.setTargetRootFlatPath("diagnose/diagnose[n]/klinischer_status");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // diagnose:0 matched via diagnosestatus|code → all its entries survive
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|value"));

        // diagnose:1 and diagnose:2 had no attribute matching "at0016" → excluded
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:2/klinischer_status/diagnosestatus|code"));
    }

    /**
     * OR across targetAttributes: a group can match via the SECOND attribute even if the first
     * doesn't satisfy the criteria.
     * <p>
     * diagnose:0 → diagnosestatus|code = "at0088" (not matching), diagnosestatus|value = "at0016" (matching)
     * diagnose:1 → diagnosestatus|code = "at0088", diagnosestatus|value = "at0088"
     * <p>
     * criteria = "at0016" → diagnose:0 matches via second attribute, diagnose:1 excluded
     */
    @Test
    public void getRelevantJsonObject_oneOf_multipleTargetAttributes_matchesViaSecondAttribute() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "at0016",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "at0088"
                                             }
                                             """);

        final Condition c = new Condition();
        c.setTargetRoot("diagnose/diagnose[n]/klinischer_status");
        c.setOperator("one of");
        c.setCriteria("at0016");
        c.setTargetAttributesFlatPath(List.of("diagnosestatus|code", "diagnosestatus|value"));
        c.setTargetRootFlatPath("diagnose/diagnose[n]/klinischer_status");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // diagnose:0 matched via second attribute (diagnosestatus|value = "at0016")
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|value"));

        // diagnose:1 had no attribute matching "at0016" → excluded
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
    }

    // -----------------------------------------------------------------------
    // Preprocessor condition applied before mapping condition
    // -----------------------------------------------------------------------

    /**
     * The preprocessor narrows the flat JSON first (e.g. keeps only diagnose:0 group),
     * and then the mapping-level condition further filters within that result.
     * <p>
     * Preprocessor: targetRoot = "diagnose/diagnose[n]"
     * targetAttribute = "klinischer_status/diagnosestatus|code"
     * operator = "one of"
     * criteria = "at0016"
     * <p>
     * Then mapping condition: targetRoot = "diagnose/diagnose[n]/klinischer_status"
     * targetAttribute = "diagnosestatus|code"
     * operator = "one of"
     * criteria = "at0016"
     * <p>
     * Both should select the same group (diagnose:0), result contains only diagnose:0.
     */
    @Test
    public void getRelevantJsonObject_preprocessorThenMappingCondition_bothApplied() {
        final JsonObject flat = twoDiagnoseGroups();

        // Build preprocessor condition
        final Condition preprocessorCond = condition(
                "diagnose/diagnose[n]",
                "klinischer_status/diagnosestatus|code",
                "one of",
                "at0016");

        // Build mapping condition (additional filter)
        final Condition mappingCond = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, preprocessorCond, helperWith(mappingCond));

        // Only diagnose:0 entries survive
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
    }

    /**
     * Preprocessor uses "empty" to first remove groups that have mehrfachkodierung (keeps only diagnose:0).
     * The mapping condition then uses "one of" to further narrow down to diagnose:0 with a specific
     * diagnosestatus code. Since both conditions agree on diagnose:0, it survives. diagnose:1 was already
     * eliminated by the preprocessor.
     * <p>
     * Flat JSON: three groups
     * diagnose:0 — no mehrfachkodierung, diagnosestatus = at0016
     * diagnose:1 — HAS mehrfachkodierung, diagnosestatus = at0016
     * diagnose:2 — no mehrfachkodierung, diagnosestatus = at0088
     * <p>
     * Preprocessor (empty, mehrfachkodierung path) → keeps diagnose:0 and diagnose:2
     * Mapping condition (one of, at0016)            → keeps diagnose:0 only
     */
    @Test
    public void getRelevantJsonObject_preprocessorEmpty_thenMappingOneOf_jointNarrowing() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/context/start_time": "2022-02-03T04:05:06",
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0002",
                                               "diagnose/diagnose:2/kodierte_diagnose|code": "C00",
                                               "diagnose/diagnose:2/klinischer_status/diagnosestatus|code": "at0088"
                                             }
                                             """);

        final Condition preprocessorCond = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "empty",
                null);

        final Condition mappingCond = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, preprocessorCond, helperWith(mappingCond));

        // diagnose:0 passes both filters — survives
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));

        // diagnose:1 eliminated by preprocessor (has mehrfachkodierung) — does not survive
        Assert.assertFalse(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"));

        // diagnose:2 passes preprocessor but fails mapping condition (at0088 ≠ at0016) — does not survive
        Assert.assertFalse(result.has("diagnose/diagnose:2/kodierte_diagnose|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:2/klinischer_status/diagnosestatus|code"));

        // context entry always present
        Assert.assertTrue(result.has("diagnose/context/start_time"));
    }

    /**
     * Preprocessor uses "one of" and eliminates ALL groups. The mapping condition is then applied
     * to the already-empty intermediate result — verifying that nothing leaks through when the
     * preprocessor removes everything.
     * <p>
     * Flat JSON: two groups, both with diagnosestatus = at0016
     * Preprocessor (one of, at9999) → no group matches → only context entries remain
     * Mapping condition (one of, at0016) → applied to the context-only intermediate result
     * → still no group entries (they were already removed by the preprocessor)
     */
    @Test
    public void getRelevantJsonObject_preprocessorRemovesAll_mappingConditionSeesNoGroups() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/context/start_time": "2022-02-03T04:05:06",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00"
                                             }
                                             """);

        final Condition preprocessorCond = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at9999");   // matches nothing

        final Condition mappingCond = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, preprocessorCond, helperWith(mappingCond));

        // preprocessor removed all group entries — mapping condition has nothing left to select
        Assert.assertFalse(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));

        Assert.assertFalse(result.has("diagnose/context/start_time"));
    }

    /**
     * Preprocessor uses "not empty" to keep only groups that have mehrfachkodierung (diagnose:1).
     * The mapping condition (one of, at0016) is then applied to that narrowed result.
     * diagnose:1 has diagnosestatus = at0016 → it survives both.
     * diagnose:0 has no mehrfachkodierung → eliminated by preprocessor despite matching the mapping condition.
     */
    @Test
    public void getRelevantJsonObject_preprocessorNotEmpty_thenMappingOneOf_secondGroupSurvives() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0002"
                                             }
                                             """);

        final Condition preprocessorCond = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "not empty",
                null);

        final Condition mappingCond = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, preprocessorCond, helperWith(mappingCond));

        // diagnose:0 eliminated by preprocessor (no mehrfachkodierung) — does not survive
        Assert.assertFalse(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));

        // diagnose:1 passes preprocessor (has mehrfachkodierung) AND mapping condition (at0016) — survives
        Assert.assertTrue(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"));
    }

    // -----------------------------------------------------------------------
    // Entries not under a diagnose group are not affected by group filtering
    // -----------------------------------------------------------------------

    /**
     * Context-level entries (e.g. diagnose/context/...) are not under any diagnose group
     * and must always be included in the result, regardless of which group(s) are kept.
     */
    @Test
    public void getRelevantJsonObject_oneOf_contextEntriesAlwaysIncluded() {
        final JsonObject flat = twoDiagnoseGroups();

        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // Context entry is always returned — it does not belong to any group
        Assert.assertTrue(result.has("diagnose/context/start_time"));

        // Group entries still filtered correctly
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
    }

    // -----------------------------------------------------------------------
    // "not empty" operator — keep only groups where the targetAttribute path IS present
    // -----------------------------------------------------------------------

    /**
     * Flat JSON where diagnose:0 DOES have mehrfachkodierungskennzeichen (present),
     * and diagnose:1 does NOT have it (absent).
     * <p>
     * Condition: targetRoot = "diagnose/diagnose[n]"
     * targetAttribute = "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"
     * operator = "not empty"
     * <p>
     * Expected: only diagnose:0 entries survive (the one where the path IS present).
     */
    @Test
    public void getRelevantJsonObject_notEmpty_keepsGroupWherePathPresent() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/kodierte_diagnose|value": "Cholera",
                                               "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0002",
                                               "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|value": "†",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/kodierte_diagnose|value": "No coding"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "not empty",
                null);

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // diagnose:0 must survive (path present there)
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|value"));
        Assert.assertTrue(result.has(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"));

        // diagnose:1 must be excluded (path absent there)
        Assert.assertFalse(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/kodierte_diagnose|value"));
    }

    /**
     * When both groups have the targeted path, both survive — result equals input.
     */
    @Test
    public void getRelevantJsonObject_notEmpty_allGroupsHavePath_returnsBoth() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0003",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code": "at0002"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "not empty",
                null);

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertTrue(result.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertTrue(result.has(
                "diagnose/diagnose:0/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertTrue(result.has(
                "diagnose/diagnose:1/mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code"));
    }

    /**
     * When neither group has the targeted path, none survives — result is empty.
     */
    @Test
    public void getRelevantJsonObject_notEmpty_neitherGroupHasPath_returnsEmpty() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "somehting/else|code": "B00"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]",
                "mehrfachkodierungskennzeichen_icd-10-gm/mehrfachkodierungkennzeichen|code",
                "not empty",
                null);

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        Assert.assertEquals(0, result.size());
    }

    // -----------------------------------------------------------------------
    // splitByHierarchy — splits a flat JsonObject by occurrence index of a path
    // -----------------------------------------------------------------------

    /**
     * When splitKey is null, the original JsonObject is returned as a single-element list.
     */
    @Test
    public void splitByHierarchy_nullSplitKey_returnsSingleElementList() {
        final JsonObject flat = twoDiagnoseGroups();
        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat, null);

        Assert.assertEquals(1, result.size());
        Assert.assertSame(flat, result.get(0));
    }

    /**
     * When the splitKey path does not appear in the flat JSON at all, the original
     * object is returned as a single-element list (no occurrences to split on).
     */
    @Test
    public void splitByHierarchy_splitKeyNotFound_returnsSingleElementList() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/context/start_time": "2022-02-03T04:05:06",
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00"
                                             }
                                             """);

        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat,
                                                                          "diagnose/nonexistent[n]/path");

        Assert.assertEquals(1, result.size());
        Assert.assertSame(flat, result.get(0));
    }

    /**
     * When the flat JSON contains two occurrences (diagnose:0 and diagnose:1), the result
     * must be two slices — one per occurrence.
     */
    @Test
    public void splitByHierarchy_twoOccurrences_returnsTwoSlices() {
        final JsonObject flat = twoDiagnoseGroups();

        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat,
                                                                          "diagnose/diagnose[n]");

        Assert.assertEquals(2, result.size());
    }

    /**
     * Each slice must contain only entries for its own occurrence index — entries from the
     * other occurrence must not be present.
     */
    @Test
    public void splitByHierarchy_twoOccurrences_eachSliceContainsOwnEntries() {
        final JsonObject flat = twoDiagnoseGroups();

        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat,
                                                                          "diagnose/diagnose[n]");

        // Determine which slice is :0 and which is :1
        final JsonObject slice0 = result.stream()
                .filter(s -> s.has("diagnose/diagnose:0/kodierte_diagnose|code"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No slice with diagnose:0 entry"));
        final JsonObject slice1 = result.stream()
                .filter(s -> s.has("diagnose/diagnose:1/kodierte_diagnose|code"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No slice with diagnose:1 entry"));

        // slice0 must NOT contain diagnose:1 entries
        Assert.assertFalse(slice0.has("diagnose/diagnose:1/kodierte_diagnose|code"));
        Assert.assertFalse(slice0.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));

        // slice1 must NOT contain diagnose:0 entries
        Assert.assertFalse(slice1.has("diagnose/diagnose:0/kodierte_diagnose|code"));
        Assert.assertFalse(slice1.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
    }

    /**
     * Context entries (entries not under any occurrence) must appear in every slice.
     */
    @Test
    public void splitByHierarchy_contextEntriesAppearsInEverySlice() {
        final JsonObject flat = twoDiagnoseGroups();

        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat,
                                                                          "diagnose/diagnose[n]");

        Assert.assertEquals(2, result.size());
        for (final JsonObject slice : result) {
            Assert.assertTrue("Context entry must appear in every slice",
                              slice.has("diagnose/context/start_time"));
        }
    }

    /**
     * Three occurrences in the flat JSON must produce exactly three slices.
     */
    @Test
    public void splitByHierarchy_threeOccurrences_returnsThreeSlices() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/context/start_time": "2022-02-03T04:05:06",
                                               "diagnose/diagnose:0/kodierte_diagnose|code": "A00",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:1/kodierte_diagnose|code": "B00",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:2/kodierte_diagnose|code": "C00",
                                               "diagnose/diagnose:2/klinischer_status/diagnosestatus|code": "at0018"
                                             }
                                             """);

        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat,
                                                                          "diagnose/diagnose[n]");

        Assert.assertEquals(3, result.size());

        // Each occurrence must appear in exactly one slice
        final long slicesWithDiagnose0 = result.stream()
                .filter(s -> s.has("diagnose/diagnose:0/kodierte_diagnose|code")).count();
        final long slicesWithDiagnose1 = result.stream()
                .filter(s -> s.has("diagnose/diagnose:1/kodierte_diagnose|code")).count();
        final long slicesWithDiagnose2 = result.stream()
                .filter(s -> s.has("diagnose/diagnose:2/kodierte_diagnose|code")).count();

        Assert.assertEquals(1, slicesWithDiagnose0);
        Assert.assertEquals(1, slicesWithDiagnose1);
        Assert.assertEquals(1, slicesWithDiagnose2);

        // Context entry present in all three slices
        final long slicesWithContext = result.stream()
                .filter(s -> s.has("diagnose/context/start_time")).count();
        Assert.assertEquals(3, slicesWithContext);
    }

    /**
     * splitKey with [n] recurring marker is handled the same as without — the marker is stripped
     * before building the regex.
     */
    @Test
    public void splitByHierarchy_recurringMarkerInSplitKey_handledCorrectly() {
        final JsonObject flat = twoDiagnoseGroups();

        final java.util.List<JsonObject> resultWith = engine.splitByHierarchy(flat,
                                                                              "diagnose/diagnose[n]");
        Assert.assertEquals(2, resultWith.size());
    }

    /**
     * Regression: splitting the medication_order flat JSON by
     * "medication_order/medication_order/order[n]" must produce two slices (order:0 and order:1).
     * Each slice must contain only entries whose key starts with the respective occurrence prefix,
     * plus context entries (keys that don't fall under any occurrence prefix at all).
     * <p>
     * Previously, getAllEntriesThatMatch returned full flat keys instead of the occurrence-level
     * prefix, so startsWith checks failed and all entries landed in every slice.
     */
    @Test
    public void splitByHierarchy_medicationOrder_twoOccurrences_eachSliceContainsOwnEntries() throws Exception {
        final JsonObject flat;
        try (final java.io.InputStream is = getClass().getClassLoader()
                .getResourceAsStream("medication_order/medication_order_flat.json")) {
            flat = JsonParser.parseReader(new java.io.InputStreamReader(is)).getAsJsonObject();
        }

        final java.util.List<JsonObject> result = engine.splitByHierarchy(flat,
                                                                          "medication_order/medication_order/order[n]");

        Assert.assertEquals(2, result.size());

        final JsonObject slice0 = result.stream()
                .filter(s -> s.has("medication_order/medication_order/order:0/medication_item"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No slice with order:0/medication_item"));
        final JsonObject slice1 = result.stream()
                .filter(s -> s.has("medication_order/medication_order/order:1/medication_item"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No slice with order:1/medication_item"));

        // order:0 entries must not appear in slice1
        Assert.assertFalse(slice1.has("medication_order/medication_order/order:0/medication_item"));
        Assert.assertFalse(slice1.has("medication_order/medication_order/order:0/route:0"));

        // order:1 entries must not appear in slice0
        Assert.assertFalse(slice0.has("medication_order/medication_order/order:1/medication_item"));
        Assert.assertFalse(slice0.has("medication_order/medication_order/order:1/additional_instruction:0"));

        // context entries (keys not under any order prefix) appear in both slices
        Assert.assertTrue(slice0.has("medication_order/context/start_time"));
        Assert.assertTrue(slice1.has("medication_order/context/start_time"));

        // entries shared at the medication_order/medication_order level (no order index) are context
        Assert.assertTrue(slice0.has("medication_order/medication_order/narrative"));
        Assert.assertTrue(slice1.has("medication_order/medication_order/narrative"));
    }

    // -----------------------------------------------------------------------
    // Keys that belong to the matched group but are NOT the condition path
    // are still included in the result
    // -----------------------------------------------------------------------

    /**
     * Within the matching diagnose:0 group, ALL entries under "diagnose:0/klinischer_status"
     * are included — not just the one that was used for the condition check.
     */
    @Test
    public void getRelevantJsonObject_oneOf_allGroupEntriesKept_notJustConditionKey() {
        final JsonObject flat = json("""
                                             {
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|code": "at0016",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|value": "Preliminary",
                                               "diagnose/diagnose:0/klinischer_status/diagnosestatus|terminology": "local",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|code": "at0088",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|value": "Refuted",
                                               "diagnose/diagnose:1/klinischer_status/diagnosestatus|terminology": "local"
                                             }
                                             """);

        final Condition c = condition(
                "diagnose/diagnose[n]/klinischer_status",
                "diagnosestatus|code",
                "one of",
                "at0016");

        final JsonObject result = engine.getRelevantJsonObject(flat, null, helperWith(c));

        // All three sub-entries of diagnose:0/klinischer_status are kept
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|code"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|value"));
        Assert.assertTrue(result.has("diagnose/diagnose:0/klinischer_status/diagnosestatus|terminology"));

        // All three sub-entries of diagnose:1/klinischer_status are excluded
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|code"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|value"));
        Assert.assertFalse(result.has("diagnose/diagnose:1/klinischer_status/diagnosestatus|terminology"));
    }
}
