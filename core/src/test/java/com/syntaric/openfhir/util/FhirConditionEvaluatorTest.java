package com.syntaric.openfhir.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import java.util.List;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link FhirConditionEvaluator} — the programmatic replacement for the deprecated
 * condition-in-fhirPath string splicing. Each test builds in-memory R4 resources and pins the
 * filtering semantics the where() clause used to provide.
 */
public class FhirConditionEvaluatorTest {

    private final OpenFhirStringUtils stringUtils = new OpenFhirStringUtils();
    private final FhirConditionEvaluator evaluator = new FhirConditionEvaluator(stringUtils);
    private final IFhirPath fhirPath = new FhirPathR4(FhirContext.forR4());

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Condition condition(final String targetRoot, final List<String> targetAttributes,
                                       final String operator, final List<String> criterias) {
        final Condition c = new Condition();
        c.setTargetRoot(targetRoot);
        c.setTargetAttributes(targetAttributes);
        c.setOperator(operator);
        c.setCriterias(criterias);
        return c;
    }

    private static MappingHelper helper(final String fullFhirPath, final Condition fhirCondition) {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath(fullFhirPath);
        helper.setFhirConditions(List.of(fhirCondition));
        return helper;
    }

    /**
     * Observation with two components: one coded 9999-9 (value 10), one coded 1111-1 (value 20).
     */
    private static Observation twoComponentObservation() {
        final Observation observation = new Observation();
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("9999-9")))
                .setValue(new Quantity().setValue(10));
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("1111-1")))
                .setValue(new Quantity().setValue(20));
        return observation;
    }

    private static Observation observationWithCodings(final String... codes) {
        final Observation observation = new Observation();
        final CodeableConcept code = new CodeableConcept();
        for (final String c : codes) {
            code.addCoding(new Coding().setCode(c));
        }
        observation.setCode(code);
        return observation;
    }

    // -----------------------------------------------------------------------
    // Mid-path targetRoot: Observation.component.where(...).value
    // -----------------------------------------------------------------------

    @Test
    public void oneOf_midPathTargetRoot_filtersComponents() {
        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code"), "one of", List.of("9999-9")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                twoComponentObservation(), fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("10", ((Quantity) results.get(0)).getValueElement().getValueAsString());
    }

    @Test
    public void oneOf_midPathTargetRoot_noMatch_returnsEmpty() {
        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code"), "one of", List.of("0000-0")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                twoComponentObservation(), fhirPath, Base.class);

        Assert.assertTrue(results.isEmpty());
    }

    // -----------------------------------------------------------------------
    // targetRoot == fullFhirPath: coding.where(...) — the filtered elements ARE the results
    // -----------------------------------------------------------------------

    @Test
    public void oneOf_targetRootEqualsFullPath_multipleCriteriasAreOrImplied() {
        final MappingHelper helper = helper("Observation.code.coding",
                condition("Observation.code.coding", List.of("code"), "one of", List.of("active", "inactive")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "code.coding",
                observationWithCodings("active", "resolved"), fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("active", ((Coding) results.get(0)).getCode());
    }

    @Test
    public void notOf_multipleCriterias_excludesAllListed() {
        final MappingHelper helper = helper("Observation.code.coding",
                condition("Observation.code.coding", List.of("code"), "not of", List.of("active", "resolved")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "code.coding",
                observationWithCodings("active", "resolved", "other"), fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("other", ((Coding) results.get(0)).getCode());
    }

    // -----------------------------------------------------------------------
    // type operator: attribute value's string must CONTAIN the criteria
    // -----------------------------------------------------------------------

    @Test
    public void typeOperator_usesContainsSemantics() {
        final Observation observation = new Observation();
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("a")))
                .setValue(new StringType("value-with-marker-inside"));
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("b")))
                .setValue(new StringType("nothing"));

        final MappingHelper helper = helper("Observation.component.code",
                condition("Observation.component", List.of("value"), "type", List.of("marker")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.code",
                observation, fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("a", ((CodeableConcept) results.get(0)).getCodingFirstRep().getCode());
    }

    // -----------------------------------------------------------------------
    // Multiple targetAttributes (OR-implied)
    // -----------------------------------------------------------------------

    @Test
    public void oneOf_multipleTargetAttributes_matchesViaSecondAttribute() {
        final Observation observation = new Observation();
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("no-match").setDisplay("the-display")))
                .setValue(new Quantity().setValue(1));
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("no-match").setDisplay("other")))
                .setValue(new Quantity().setValue(2));

        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code", "code.coding.display"),
                        "one of", List.of("the-display")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                observation, fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("1", ((Quantity) results.get(0)).getValueElement().getValueAsString());
    }

    @Test
    public void notOf_multipleTargetAttributes_failsWhenAnyAttributeMatches() {
        final Observation observation = new Observation();
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("harmless").setDisplay("excluded")))
                .setValue(new Quantity().setValue(1));

        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code", "code.coding.display"),
                        "not of", List.of("excluded")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                observation, fhirPath, Base.class);

        Assert.assertTrue(results.isEmpty());
    }

    @Test
    public void oneOf_multipleTargetAttributesAndCriterias_combined() {
        final Observation observation = new Observation();
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("code-b").setDisplay("display-x")))
                .setValue(new Quantity().setValue(1));
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("code-x").setDisplay("display-a")))
                .setValue(new Quantity().setValue(2));
        observation.addComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("code-x").setDisplay("display-x")))
                .setValue(new Quantity().setValue(3));

        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code", "code.coding.display"),
                        "one of", List.of("code-b", "display-a")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                observation, fhirPath, Base.class);

        Assert.assertEquals(2, results.size());
    }

    // -----------------------------------------------------------------------
    // Legacy bracket criteria syntax
    // -----------------------------------------------------------------------

    @Test
    public void legacyCriteriaSyntax_loincPrefix() {
        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code"), "one of", List.of("[$loinc.9999-9]")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                twoComponentObservation(), fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("10", ((Quantity) results.get(0)).getValueElement().getValueAsString());
    }

    @Test
    public void legacyCriteriaSyntax_plainBrackets() {
        final MappingHelper helper = helper("Observation.code.coding",
                condition("Observation.code.coding", List.of("code"), "one of", List.of("[bd]")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "code.coding",
                observationWithCodings("bd", "ro"), fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("bd", ((Coding) results.get(0)).getCode());
    }

    // -----------------------------------------------------------------------
    // $fhirRoot-anchored child condition (blood pressure shape): toResolveOn is the component
    // -----------------------------------------------------------------------

    @Test
    public void fhirRootAnchoredCondition_evaluatesOnComponentItself() {
        final Observation.ObservationComponentComponent matching = new Observation.ObservationComponentComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("8462-4")))
                .setValue(new Quantity().setValue(80));
        final Observation.ObservationComponentComponent nonMatching = new Observation.ObservationComponentComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("8480-6")))
                .setValue(new Quantity().setValue(120));

        // amended $fhirRoot targetRoot == parent's full path == the helper's own full path minus "value"
        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code.coding.code"), "one of", List.of("8462-4")));

        final List<? extends IBase> matchingResults = evaluator.evaluateWithConditions(helper, "value",
                matching, fhirPath, Base.class);
        Assert.assertEquals(1, matchingResults.size());
        Assert.assertEquals("80", ((Quantity) matchingResults.get(0)).getValueElement().getValueAsString());

        final List<? extends IBase> nonMatchingResults = evaluator.evaluateWithConditions(helper, "value",
                nonMatching, fhirPath, Base.class);
        Assert.assertTrue(nonMatchingResults.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Diverging / sibling-anchored targetRoot: condition attribute lives NEXT TO the mapped path
    // -----------------------------------------------------------------------

    @Test
    public void divergingTargetRoot_filtersViaSiblingAttribute() {
        final Encounter encounter = new Encounter();
        encounter.addLocation()
                .setPhysicalType(new CodeableConcept().addCoding(new Coding().setCode("ro")))
                .setStatus(Encounter.EncounterLocationStatus.ACTIVE);
        encounter.addLocation()
                .setPhysicalType(new CodeableConcept().addCoding(new Coding().setCode("bd")))
                .setStatus(Encounter.EncounterLocationStatus.PLANNED);

        final MappingHelper helper = helper("Encounter.location.status",
                condition("Encounter.location.physicalType.coding", List.of("code"), "one of", List.of("ro")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "location.status",
                encounter, fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("active",
                ((org.hl7.fhir.r4.model.Enumeration<?>) results.get(0)).getValueAsString());
    }

    // -----------------------------------------------------------------------
    // parentRootPassesConditions
    // -----------------------------------------------------------------------

    @Test
    public void parentRootPassesConditions_passesAndFails() {
        final Observation.ObservationComponentComponent component = new Observation.ObservationComponentComponent()
                .setCode(new CodeableConcept().addCoding(new Coding().setCode("8462-4")));

        final MappingHelper passingHelper = helper("Observation.component",
                condition("Observation.component", List.of("code.coding.code"), "one of", List.of("8462-4")));
        Assert.assertTrue(evaluator.parentRootPassesConditions(passingHelper, component, fhirPath, Base.class));

        final MappingHelper failingHelper = helper("Observation.component",
                condition("Observation.component", List.of("code.coding.code"), "one of", List.of("8480-6")));
        Assert.assertFalse(evaluator.parentRootPassesConditions(failingHelper, component, fhirPath, Base.class));
    }

    @Test
    public void parentRootPassesConditions_emptyOperatorConditionsAreIgnored() {
        final MappingHelper helper = helper("Observation.component",
                condition("Observation.component", List.of("interpretation"), "not empty", List.of()));

        Assert.assertTrue(evaluator.parentRootPassesConditions(helper,
                new Observation.ObservationComponentComponent(), fhirPath, Base.class));
    }

    @Test
    public void parentRootPassesConditions_nullFullPath_relativizesAgainstResourceType() {
        final Observation observation = observationWithCodings("gate-code");
        // no fullFhirPath on the helper (mapping without a fhir path) — targetRoot anchored at the resource
        final MappingHelper helper = new MappingHelper();
        helper.setFhirConditions(List.of(
                condition("Observation", List.of("code.coding.code"), "one of", List.of("gate-code"))));

        Assert.assertTrue(evaluator.parentRootPassesConditions(helper, observation, fhirPath, Base.class));
    }

    // -----------------------------------------------------------------------
    // resourcePassesCondition: profile match + STU3 preprocessor shapes
    // -----------------------------------------------------------------------

    @Test
    public void resourcePassesCondition_profileMatch() {
        final Observation observation = new Observation();
        observation.getMeta().addProfile("http://example.org/profile");

        final Condition matching = condition("Observation", List.of("meta.profile"), "one of",
                List.of("http://example.org/profile"));
        Assert.assertTrue(evaluator.resourcePassesCondition(matching, observation, fhirPath, Base.class));

        final Condition notMatching = condition("Observation", List.of("meta.profile"), "one of",
                List.of("http://example.org/other"));
        Assert.assertFalse(evaluator.resourcePassesCondition(notMatching, observation, fhirPath, Base.class));
    }

    @Test
    public void resourcePassesCondition_stu3PreprocessorShape() {
        final org.hl7.fhir.dstu3.model.Observation observation = new org.hl7.fhir.dstu3.model.Observation();
        observation.setCode(new org.hl7.fhir.dstu3.model.CodeableConcept()
                .addCoding(new org.hl7.fhir.dstu3.model.Coding().setCode("85354-9")));

        final IFhirPath stu3FhirPath = new org.hl7.fhir.dstu3.hapi.fluentpath.FhirPathDstu3(FhirContext.forDstu3());

        final Condition matching = condition("Observation.code.coding", List.of("code"), "one of",
                List.of("85354-9"));
        Assert.assertTrue(evaluator.resourcePassesCondition(matching, observation, stu3FhirPath,
                org.hl7.fhir.dstu3.model.Base.class));

        final Condition notMatching = condition("Observation.code.coding", List.of("code"), "one of",
                List.of("1234-5"));
        Assert.assertFalse(evaluator.resourcePassesCondition(notMatching, observation, stu3FhirPath,
                org.hl7.fhir.dstu3.model.Base.class));
    }

    // -----------------------------------------------------------------------
    // Error path: invalid attribute path must not throw — same catch-log-null contract as
    // plain path evaluation
    // -----------------------------------------------------------------------

    @Test
    public void invalidAttributePath_returnsNullInsteadOfThrowing() {
        final MappingHelper helper = helper("Observation.component.value",
                condition("Observation.component", List.of("code..coding"), "one of", List.of("9999-9")));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "component.value",
                twoComponentObservation(), fhirPath, Base.class);

        Assert.assertNull(results);
    }

    // -----------------------------------------------------------------------
    // mappedPathEndAttributePrefix — the legacy path-end placement
    //
    // The old string splicing appended the where() clause to the END of the mapped path whenever
    // the condition's raw targetRoot did not prefix the raw path (see
    // HelpersCreator#mappedPathEndAttributePrefix, and the dispatch tests in HelpersCreatorTest).
    // For such conditions the predicate runs on the mapped path's RESULTS, with the stored prefix
    // prepended to each targetAttribute. HelpersCreator stamps the amended condition with this
    // value; here we set it directly to show what each value does.
    // -----------------------------------------------------------------------

    /**
     * Prefix "" — the condition attributes are evaluated on each element the mapped path yields,
     * regardless of what the targetRoot says: the addresses themselves get filtered by city.
     */
    @Test
    public void mappedPathEndPrefix_empty_conditionFiltersThePathResults() {
        final Patient patient = new Patient();
        patient.addAddress().setCity("Berlin");
        patient.addAddress().setCity("Hamburg");

        final Condition condition = condition("Patient", List.of("city"), "one of", List.of("Berlin"));
        condition.setMappedPathEndAttributePrefix("");
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Patient.address");
        helper.setFhirConditions(List.of(condition));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "address",
                patient, fhirPath, Base.class);

        Assert.assertEquals(1, results.size());
        Assert.assertEquals("Berlin", ((Address) results.get(0)).getCity());
    }

    /**
     * The same condition WITHOUT the prefix anchors at its targetRoot ("Patient") instead: the
     * attribute ("city") is then evaluated on the Patient itself, where it resolves to nothing —
     * the condition fails and no address comes back. This is the difference the prefix encodes.
     */
    @Test
    public void mappedPathEndPrefix_absent_sameConditionAnchorsAtTargetRoot() {
        final Patient patient = new Patient();
        patient.addAddress().setCity("Berlin");
        patient.addAddress().setCity("Hamburg");

        final Condition condition = condition("Patient", List.of("city"), "one of", List.of("Berlin"));
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Patient.address");
        helper.setFhirConditions(List.of(condition));

        final List<? extends IBase> results = evaluator.evaluateWithConditions(helper, "address",
                patient, fhirPath, Base.class);

        Assert.assertTrue(results.isEmpty());
    }

    /**
     * A prefix that cannot resolve on the path's results (here the resource type name, as the
     * legacy splice produced for a targetRoot unrelated to the mapped path — the IPS/EPS
     * emptyReason shape): the attribute path never yields a value, so "not of" ALWAYS passes and
     * "one of" NEVER does, no matter what the data says. Deliberately preserved.
     */
    @Test
    public void mappedPathEndPrefix_unresolvablePrefix_makesTheConditionDegenerate() {
        final Observation observation = observationWithCodings("excluded-code");

        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");

        // "not of excluded-code" — the coding DOES carry the excluded code, yet the condition
        // passes because "Composition.coding.code" resolves to nothing on a CodeableConcept
        final Condition notOf = condition("Observation", List.of("coding.code"), "not of",
                List.of("excluded-code"));
        notOf.setMappedPathEndAttributePrefix("Composition");
        helper.setFhirConditions(List.of(notOf));
        Assert.assertEquals(1, evaluator.evaluateWithConditions(helper, "code",
                observation, fhirPath, Base.class).size());

        // "one of excluded-code" — the coding matches, yet the condition never does
        final Condition oneOf = condition("Observation", List.of("coding.code"), "one of",
                List.of("excluded-code"));
        oneOf.setMappedPathEndAttributePrefix("Composition");
        helper.setFhirConditions(List.of(oneOf));
        Assert.assertTrue(evaluator.evaluateWithConditions(helper, "code",
                observation, fhirPath, Base.class).isEmpty());
    }

    /**
     * A relativized prefix ("address", from a targetRoot that extends the mapped path — e.g.
     * targetRoot $resource.address below a $resource mapping): the condition still gates
     * sensibly, evaluating "address.city" on the resource the path yields.
     */
    @Test
    public void mappedPathEndPrefix_relativizedPrefix_gatesViaTheExtension() {
        final Patient patient = new Patient();
        patient.addAddress().setCity("Berlin");

        final Condition condition = condition("Patient.address", List.of("city"), "one of", List.of("Berlin"));
        condition.setMappedPathEndAttributePrefix("address");
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Patient");
        helper.setFhirConditions(List.of(condition));

        final List<? extends IBase> matching = evaluator.evaluateWithConditions(helper, "Patient",
                patient, fhirPath, Base.class);
        Assert.assertEquals(1, matching.size());
        Assert.assertSame(patient, matching.get(0));

        condition.setCriterias(List.of("Hamburg"));
        Assert.assertTrue(evaluator.evaluateWithConditions(helper, "Patient",
                patient, fhirPath, Base.class).isEmpty());
    }

    // -----------------------------------------------------------------------
    // Condition deserialization: deprecated singular keys land in the plural lists
    // -----------------------------------------------------------------------

    @Test
    public void conditionDeserialization_singularKeysNormalizedToPluralLists() throws Exception {
        final ObjectMapper objectMapper = new ObjectMapper();

        final Condition fromSingular = objectMapper.readValue(
                "{\"targetRoot\":\"$resource\",\"targetAttribute\":\"code.coding.code\","
                        + "\"operator\":\"one of\",\"criteria\":\"8462-4\"}",
                Condition.class);
        Assert.assertEquals(List.of("code.coding.code"), fromSingular.getTargetAttributes());
        Assert.assertEquals(List.of("8462-4"), fromSingular.getCriterias());

        final Condition fromPlural = objectMapper.readValue(
                "{\"targetRoot\":\"$resource\",\"targetAttributes\":[\"a\",\"b\"],"
                        + "\"operator\":\"one of\",\"criterias\":[\"x\",\"y\"]}",
                Condition.class);
        Assert.assertEquals(List.of("a", "b"), fromPlural.getTargetAttributes());
        Assert.assertEquals(List.of("x", "y"), fromPlural.getCriterias());
    }
}
