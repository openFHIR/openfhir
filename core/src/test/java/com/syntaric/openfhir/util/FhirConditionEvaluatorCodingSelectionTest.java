package com.syntaric.openfhir.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import java.util.List;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FhirConditionEvaluator#selectCodings}: in the openEHR→FHIR direction a coding-level
 * fhirCondition selects which of a DV_CODED_TEXT's codings (its own code plus its TERM_MAPPING
 * targets) are written. It is a selection, never a gate.
 */
public class FhirConditionEvaluatorCodingSelectionTest {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String CYTODOS = "http://cytodos.se/route";

    private final FhirConditionEvaluator evaluator = new FhirConditionEvaluator(new OpenFhirStringUtils());
    private final IFhirPath fhirPath = new FhirPathR4(FhirContext.forR4());

    private static Condition condition(final String targetRoot, final String targetAttribute,
                                       final String operator, final String... criterias) {
        final Condition c = new Condition();
        c.setTargetRoot(targetRoot);
        c.setTargetAttributes(List.of(targetAttribute));
        c.setOperator(operator);
        c.setCriterias(List.of(criterias));
        return c;
    }

    private static MappingHelper helper(final String fullFhirPath, final Condition... conditions) {
        final MappingHelper helper = new MappingHelper();
        helper.setMappingName("route");
        helper.setFullFhirPath(fullFhirPath);
        helper.setFhirConditions(List.of(conditions));
        return helper;
    }

    /** The Karolinska shape: the element's own Cytodos code first, the SNOMED TERM_MAPPING target second. */
    private static CodeableConcept cytodosWithSnomedMapping() {
        return new CodeableConcept()
                .addCoding(new Coding(CYTODOS, "3.0000", "Intravenös"))
                .addCoding(new Coding(SNOMED, "47625008", "Intravenous route"));
    }

    private static List<String> systems(final CodeableConcept cc) {
        return cc.getCoding().stream().map(Coding::getSystem).collect(Collectors.toList());
    }

    @Test
    public void codeableConceptTarget_targetRootIsCoding_keepsOnlyMatchingCodings() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "one of", SNOMED));

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(List.of(SNOMED), systems(cc));
        Assert.assertEquals("47625008", cc.getCodingFirstRep().getCode());
    }

    @Test
    public void codingTarget_targetRootEqualsFullPath_keepsOnlyMatchingCodings() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route.coding",
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "one of", SNOMED));

        evaluator.selectCodings(cc, helper, true, fhirPath);

        Assert.assertEquals(List.of(SNOMED), systems(cc));
    }

    @Test
    public void notOf_dropsTheListedCodings() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "not of", CYTODOS));

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(List.of(SNOMED), systems(cc));
    }

    @Test
    public void multipleCriterias_areOrImplied_multipleConditions_areAnded() {
        final CodeableConcept cc = cytodosWithSnomedMapping()
                .addCoding(new Coding(SNOMED, "255560000", "Intravenous"));
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "one of", SNOMED, CYTODOS),
                condition("MedicationRequest.dosageInstruction.route.coding", "code", "one of", "255560000"));

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(1, cc.getCoding().size());
        Assert.assertEquals("255560000", cc.getCodingFirstRep().getCode());
    }

    /** Selection, not a gate: a condition nothing satisfies leaves the codings as they are. */
    @Test
    public void noSurvivor_leavesCodingsUntouched() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "one of",
                        "http://loinc.org"));

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(List.of(CYTODOS, SNOMED), systems(cc));
    }

    /**
     * The common bidirectional idiom — a condition on {@code coding.system} describing what a child
     * {@code manual} writes later — keeps the single raw coding it would otherwise have gated away.
     */
    @Test
    public void singleCodingThatFails_isKept() {
        final CodeableConcept cc = new CodeableConcept().addCoding(new Coding("local", "at0026", "Active"));
        final MappingHelper helper = helper("Condition.clinicalStatus",
                condition("Condition.clinicalStatus.coding", "system", "one of",
                        "http://terminology.hl7.org/CodeSystem/condition-clinical"));

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(1, cc.getCoding().size());
        Assert.assertEquals("at0026", cc.getCodingFirstRep().getCode());
    }

    @Test
    public void unrelatedTargetRoot_leavesCodingsUntouched() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        // a sibling of the mapped element
        final MappingHelper sibling = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.method.coding", "system", "one of", SNOMED));
        evaluator.selectCodings(cc, sibling, false, fhirPath);
        Assert.assertEquals(2, cc.getCoding().size());

        // a parent of the mapped element
        final MappingHelper parent = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction", "route.coding.system", "one of", SNOMED));
        evaluator.selectCodings(cc, parent, false, fhirPath);
        Assert.assertEquals(2, cc.getCoding().size());

        // the CodeableConcept itself (not its codings) for a CodeableConcept target
        final MappingHelper self = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route", "coding.system", "one of", SNOMED));
        evaluator.selectCodings(cc, self, false, fhirPath);
        Assert.assertEquals(2, cc.getCoding().size());

        // deeper than the codings
        final MappingHelper deeper = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route.coding.extension", "url", "one of", "x"));
        evaluator.selectCodings(cc, deeper, false, fhirPath);
        Assert.assertEquals(2, cc.getCoding().size());
    }

    /** {@code coding} below a Coding target is not the coding itself — nothing is selected. */
    @Test
    public void codingTarget_targetRootBelowIt_leavesCodingsUntouched() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route.coding",
                condition("MedicationRequest.dosageInstruction.route.coding.coding", "system", "one of", SNOMED));

        evaluator.selectCodings(cc, helper, true, fhirPath);

        Assert.assertEquals(2, cc.getCoding().size());
    }

    @Test
    public void emptyAndNotEmptyConditions_areIgnored() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route",
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "not empty"),
                condition("MedicationRequest.dosageInstruction.route.coding", "version", "empty"));

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(2, cc.getCoding().size());
    }

    /**
     * The legacy path-end placement HelpersCreator stamps on a condition whose targetRoot extends
     * the mapped path ({@code with.fhir: $resource.route} + {@code targetRoot: $resource.route.coding}
     * → prefix {@code coding}): the prefix stands in for the relative targetRoot.
     */
    @Test
    public void mappedPathEndAttributePrefix_codingPrefixSelectsForCodeableConceptTarget() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final Condition condition = condition("MedicationRequest.dosageInstruction.route.coding", "system",
                "one of", SNOMED);
        condition.setMappedPathEndAttributePrefix("coding");
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route", condition);

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(List.of(SNOMED), systems(cc));
    }

    /**
     * A child mapping with a relative path ({@code with.fhir: route} under a {@code $resource.dosageInstruction}
     * parent) and a relative condition ({@code targetRoot: route.coding}): HelpersCreator stamps the legacy
     * prefix {@code ""} (the raw targetRoot neither prefixes the raw path nor starts with the resource
     * type), but the amended targetRoot is the mapped path's {@code coding} — and that is what decides.
     */
    @Test
    public void nestedRelativeCondition_emptyLegacyPrefix_selectsFromAmendedTargetRoot() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final Condition condition = condition("MedicationRequest.dosageInstruction.route.coding", "system",
                "one of", SNOMED);
        condition.setMappedPathEndAttributePrefix("");
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route", condition);

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(List.of(SNOMED), systems(cc));
    }

    /** Same shape, but the legacy prefix is the whole absolute targetRoot (the other degenerate value). */
    @Test
    public void nestedCondition_absoluteLegacyPrefix_selectsFromAmendedTargetRoot() {
        final CodeableConcept cc = cytodosWithSnomedMapping();
        final Condition condition = condition("MedicationRequest.dosageInstruction.route.coding", "system",
                "one of", SNOMED);
        condition.setMappedPathEndAttributePrefix("MedicationRequest.dosageInstruction.route.coding");
        final MappingHelper helper = helper("MedicationRequest.dosageInstruction.route", condition);

        evaluator.selectCodings(cc, helper, false, fhirPath);

        Assert.assertEquals(List.of(SNOMED), systems(cc));
    }

    /**
     * Legacy prefix {@code ""} means "the attributes apply to what the mapped path yields"; for a Coding
     * target those are the codings, so it selects even when the amended targetRoot says nothing useful.
     * For a CodeableConcept target it does not, unless the amended targetRoot addresses the codings
     * (see {@link #nestedRelativeCondition_emptyLegacyPrefix_selectsFromAmendedTargetRoot}).
     */
    @Test
    public void mappedPathEndAttributePrefix_emptyPrefix_isCodingLevelForCodingTargetOnly() {
        final Condition condition = condition("MedicationRequest.dosageInstruction", "system", "one of", SNOMED);
        condition.setMappedPathEndAttributePrefix("");

        final CodeableConcept codingTarget = cytodosWithSnomedMapping();
        evaluator.selectCodings(codingTarget,
                helper("MedicationRequest.dosageInstruction.route.coding", condition), true, fhirPath);
        Assert.assertEquals(List.of(SNOMED), systems(codingTarget));

        final CodeableConcept ccTarget = cytodosWithSnomedMapping();
        evaluator.selectCodings(ccTarget,
                helper("MedicationRequest.dosageInstruction.route", condition), false, fhirPath);
        Assert.assertEquals(2, ccTarget.getCoding().size());
    }

    @Test
    public void helperWithoutConditionsOrPath_isANoOp() {
        final CodeableConcept cc = cytodosWithSnomedMapping();

        evaluator.selectCodings(cc, new MappingHelper(), false, fhirPath);
        evaluator.selectCodings(cc, null, false, fhirPath);
        evaluator.selectCodings(cc, helper(null,
                condition("MedicationRequest.dosageInstruction.route.coding", "system", "one of", SNOMED)),
                false, fhirPath);

        Assert.assertEquals(2, cc.getCoding().size());
    }
}
