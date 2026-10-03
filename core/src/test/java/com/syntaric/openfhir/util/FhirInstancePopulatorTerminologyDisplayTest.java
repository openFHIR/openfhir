package com.syntaric.openfhir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.terminology.OfCoding;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;
import java.util.List;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Quantity;
import org.junit.jupiter.api.Test;

/**
 * The populator hands the source coding's display to the terminology translator, so a translation that only
 * rewrites the system can return it and the term survives {@code $tofhir}.
 */
class FhirInstancePopulatorTerminologyDisplayTest {

    private static final String OID = "1.2.752.116.1.1.1";
    private static final String ICD10 = "http://hl7.org/fhir/sid/icd-10";
    private static final String DISPLAY = "Malign tumör i mellanlob, bronk eller lunga";

    /** A translator that rewrites only the system, the way a ConceptMap {@code "*"} element does. */
    private static final class SystemOnlyTranslator implements TerminologyTranslatorInterface {
        @Override
        public Coding translateToFhir(final String code, final String system, final String desiredSystem,
                                      final Terminology terminology) {
            return translateToFhir(code, system, null, desiredSystem, terminology);
        }

        @Override
        public Coding translateToFhir(final String code, final String system, final String display,
                                      final String desiredSystem, final Terminology terminology) {
            return OID.equals(system) ? new Coding(ICD10, code, display) : null;
        }

        @Override
        public Coding translateToOpenEhr(final String code, final String system, final String desiredSystem,
                                         final Terminology terminology, final List<OfCoding> availableCodings) {
            return null;
        }
    }

    /** A translator written against the two original interface methods only. */
    private static final class LegacyTranslator implements TerminologyTranslatorInterface {
        @Override
        public Coding translateToFhir(final String code, final String system, final String desiredSystem,
                                      final Terminology terminology) {
            return OID.equals(system) ? new Coding(ICD10, code, null) : null;
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
        terminology.setConceptmap("http://openfhir.com/ConceptMap/icd10");
        return terminology;
    }

    @Test
    void codeableConceptKeepsDisplayThroughSystemOnlyTranslation() {
        final FhirInstancePopulator populator =
                new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new SystemOnlyTranslator());
        final CodeableConcept source = new CodeableConcept().addCoding(new Coding(OID, "C342", DISPLAY));
        final CodeableConcept target = new CodeableConcept();

        populator.handleSpecificTypePopulation(target, source, terminology());

        assertEquals(ICD10, target.getCodingFirstRep().getSystem());
        assertEquals("C342", target.getCodingFirstRep().getCode());
        assertEquals(DISPLAY, target.getCodingFirstRep().getDisplay());
    }

    @Test
    void codingKeepsDisplayThroughSystemOnlyTranslation() {
        final FhirInstancePopulator populator =
                new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new SystemOnlyTranslator());
        final Coding target = new Coding();

        populator.handleSpecificTypePopulation(target, new Coding(OID, "C342", DISPLAY), terminology());

        assertEquals(ICD10, target.getSystem());
        assertEquals("C342", target.getCode());
        assertEquals(DISPLAY, target.getDisplay());
    }

    @Test
    void quantityUnitReachesTheTranslatorAsDisplay() {
        final FhirInstancePopulator populator =
                new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new SystemOnlyTranslator());
        final Quantity source = new Quantity().setValue(2).setCode("mg").setUnit("milligram").setSystem(OID);
        final Quantity target = new Quantity();

        populator.handleSpecificTypePopulation(target, source, terminology());

        assertEquals(ICD10, target.getSystem());
        assertEquals("mg", target.getCode());
        assertEquals("milligram", target.getUnit());
    }

    /** An implementer of the original two methods is reached through the default overloads, unchanged. */
    @Test
    void legacyTranslatorStillTranslatesThroughTheDefaultOverload() {
        final FhirInstancePopulator populator =
                new FhirInstancePopulator(new NoOpPrePostFhirInstancePopulator(), new LegacyTranslator());
        final CodeableConcept source = new CodeableConcept().addCoding(new Coding(OID, "C342", DISPLAY));
        final CodeableConcept target = new CodeableConcept();

        populator.handleSpecificTypePopulation(target, source, terminology());

        assertEquals(ICD10, target.getCodingFirstRep().getSystem());
        assertEquals("C342", target.getCodingFirstRep().getCode());
        assertNull(target.getCodingFirstRep().getDisplay());
    }
}
