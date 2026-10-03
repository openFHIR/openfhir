package com.syntaric.openfhir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.syntaric.openfhir.fc.schema.model.Condition;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.terminology.OfCoding;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.Test;

/**
 * A coding-level fhirCondition on the mapping selects, after terminology translation, which of the
 * parsed CodeableConcept's codings reach the FHIR target — the Karolinska route/form shape: the
 * element carries a local Cytodos code with the SNOMED equivalent as a TERM_MAPPING, and the profile
 * allows exactly one SNOMED coding.
 */
class FhirInstancePopulatorCodingSelectionTest {

    private static final String SNOMED = "http://snomed.info/sct";
    private static final String CYTODOS = "Cytodos";
    private static final String ROUTE = "MedicationRequest.dosageInstruction.route";

    /** A {@code "*"} passthrough ConceptMap: rewrites the openEHR terminology id to the FHIR system. */
    private static final class SystemOnlyTranslator implements TerminologyTranslatorInterface {
        @Override
        public Coding translateToFhir(final String code, final String system, final String desiredSystem,
                                      final Terminology terminology) {
            return translateToFhir(code, system, null, desiredSystem, terminology);
        }

        @Override
        public Coding translateToFhir(final String code, final String system, final String display,
                                      final String desiredSystem, final Terminology terminology) {
            return "SNOMED-CT".equals(system) ? new Coding(SNOMED, code, display) : null;
        }

        @Override
        public Coding translateToOpenEhr(final String code, final String system, final String desiredSystem,
                                         final Terminology terminology, final List<OfCoding> availableCodings) {
            return null;
        }
    }

    private static Terminology terminology() {
        final Terminology terminology = new Terminology();
        terminology.setType("local");
        terminology.setConceptmap("http://openfhir.com/ConceptMap/snomed-passthrough");
        return terminology;
    }

    /** What CodedParser yields for a DV_CODED_TEXT with one TERM_MAPPING, before translation. */
    private static CodeableConcept parsedRoute() {
        return new CodeableConcept()
                .setText("Intravenös")
                .addCoding(new Coding(CYTODOS, "3.0000", "Intravenös"))
                .addCoding(new Coding("SNOMED-CT", "47625008", "Intravenous route"));
    }

    private static MappingHelper helper(final String fullFhirPath, final String targetRoot, final String operator,
                                        final String... criterias) {
        final Condition condition = new Condition();
        condition.setTargetRoot(targetRoot);
        condition.setTargetAttributes(List.of("system"));
        condition.setOperator(operator);
        condition.setCriterias(List.of(criterias));
        final MappingHelper helper = new MappingHelper();
        helper.setMappingName("route");
        helper.setFullFhirPath(fullFhirPath);
        helper.setFhirConditions(List.of(condition));
        return helper;
    }

    private final FhirInstancePopulator populator =
            new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new SystemOnlyTranslator());

    @Test
    void codingTarget_conditionOnTranslatedSystem_populatesTheSnomedCoding() {
        final Coding target = new Coding();

        populator.handleSpecificTypePopulation(target, parsedRoute(), terminology(),
                helper(ROUTE + ".coding", ROUTE + ".coding", "one of", SNOMED));

        assertEquals(SNOMED, target.getSystem());
        assertEquals("47625008", target.getCode());
        assertEquals("Intravenous route", target.getDisplay());
    }

    @Test
    void codeableConceptTarget_conditionOnItsCoding_keepsOneCoding() {
        final CodeableConcept target = new CodeableConcept();

        populator.handleSpecificTypePopulation(target, parsedRoute(), terminology(),
                helper(ROUTE, ROUTE + ".coding", "one of", SNOMED));

        assertEquals(1, target.getCoding().size());
        assertEquals(SNOMED, target.getCodingFirstRep().getSystem());
        assertEquals("47625008", target.getCodingFirstRep().getCode());
        assertEquals("Intravenös", target.getText());
    }

    @Test
    void enumerationTarget_takesTheFirstSurvivingCoding() {
        final Enumeration<MedicationRequest.MedicationRequestStatus> target =
                new Enumeration<>(new MedicationRequest.MedicationRequestStatusEnumFactory());
        final CodeableConcept parsed = new CodeableConcept()
                .addCoding(new Coding("local", "at0001", "planned"))
                .addCoding(new Coding("http://hl7.org/fhir/CodeSystem/medicationrequest-status", "active", null));

        populator.handleSpecificTypePopulation(target, parsed, terminology(),
                helper("MedicationRequest.status", "MedicationRequest.status.coding", "not of", "local"));

        assertEquals("active", target.getValueAsString());
    }

    @Test
    void noMatchingCoding_populatesUnchanged() {
        final CodeableConcept target = new CodeableConcept();

        populator.handleSpecificTypePopulation(target, parsedRoute(), terminology(),
                helper(ROUTE, ROUTE + ".coding", "one of", "http://loinc.org"));

        assertEquals(2, target.getCoding().size());
        assertEquals(CYTODOS, target.getCodingFirstRep().getSystem());
    }

    @Test
    void withoutAMappingHelper_populatesUnchanged() {
        final CodeableConcept target = new CodeableConcept();

        populator.handleSpecificTypePopulation(target, parsedRoute(), terminology());

        assertEquals(2, target.getCoding().size());
    }
}
