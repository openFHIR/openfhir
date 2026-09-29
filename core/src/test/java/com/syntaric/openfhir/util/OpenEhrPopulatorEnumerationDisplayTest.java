package com.syntaric.openfhir.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.fc.schema.terminology.Terminology;
import com.syntaric.openfhir.terminology.OfCoding;
import com.syntaric.openfhir.terminology.TerminologyTranslatorInterface;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationAdministration.MedicationAdministrationStatus;
import org.junit.jupiter.api.Test;

/**
 * A FHIR enumeration ({@code MedicationAdministration.status} and the like) has no display of its own, so the
 * openEHR {@code |value} takes the display the terminology translation returns, and never the translated code.
 */
class OpenEhrPopulatorEnumerationDisplayTest {

    private static final String PATH = "test_template/ism_transition/current_state";
    private static final String OPENEHR = "openehr";

    /** A translator that maps the ISM state {@code completed} to openEHR {@code 532}, with or without a display. */
    private static final class IsmTranslator implements TerminologyTranslatorInterface {
        private final String display;

        private IsmTranslator(final String display) {
            this.display = display;
        }

        @Override
        public Coding translateToFhir(final String code, final String system, final String desiredSystem,
                                      final Terminology terminology) {
            return null;
        }

        @Override
        public Coding translateToOpenEhr(final String code, final String system, final String desiredSystem,
                                         final Terminology terminology, final List<OfCoding> availableCodings) {
            return "completed".equals(code) ? new Coding(OPENEHR, "532", display) : null;
        }
    }

    private static Terminology terminology() {
        final Terminology terminology = new Terminology();
        terminology.setType("local");
        terminology.setConceptmap("http://openfhir.com/ConceptMap/ism");
        return terminology;
    }

    private static Enumeration<MedicationAdministrationStatus> completed() {
        return new MedicationAdministration().setStatus(MedicationAdministrationStatus.COMPLETED).getStatusElement();
    }

    private static JsonObject populate(final TerminologyTranslatorInterface translator, final String openEhrType,
                                       final Terminology terminology) {
        final OpenEhrPopulator populator = new OpenEhrPopulator(new OpenFhirMapperUtils(), translator,
                                                                new NoOpPrePostOpenEhrPopulator(),
                                                                new OpenFhirStringUtils());
        final JsonObject flat = new JsonObject();
        populator.setOpenEhrValue(null, PATH, completed(), openEhrType, false, flat, terminology, null);
        return flat;
    }

    @Test
    void codedTextTakesValueFromTheTranslatedDisplay() {
        final JsonObject flat = populate(new IsmTranslator("completed"), FhirConnectConst.DV_CODED_TEXT,
                                         terminology());

        assertEquals("532", flat.get(PATH + "|code").getAsString());
        assertEquals(OPENEHR, flat.get(PATH + "|terminology").getAsString());
        assertEquals("completed", flat.get(PATH + "|value").getAsString());
    }

    @Test
    void codePhraseTakesValueFromTheTranslatedDisplay() {
        final JsonObject flat = populate(new IsmTranslator("completed"), FhirConnectConst.CODE_PHRASE,
                                         terminology());

        assertEquals("532", flat.get(PATH + "|code").getAsString());
        assertEquals("completed", flat.get(PATH + "|value").getAsString());
        assertFalse(flat.has(PATH + "|terminology"));
    }

    /**
     * A translation that names no display falls back to the translated <em>code</em>. An inline mapping of
     * {@code permit -> at0035} declares the openEHR side of the pair; writing the FHIR token {@code permit}
     * into {@code |value} instead would leak the untranslated source value into the composition.
     */
    @Test
    void translationWithoutDisplayFallsBackToTheTranslatedCode() {
        final JsonObject flat = populate(new IsmTranslator(null), FhirConnectConst.DV_CODED_TEXT, terminology());

        assertEquals("532", flat.get(PATH + "|code").getAsString());
        assertEquals("532", flat.get(PATH + "|value").getAsString());
    }

    /** Same fallback on a CODE_PHRASE, which writes no {@code |terminology}. */
    @Test
    void codePhraseWithoutDisplayFallsBackToTheTranslatedCode() {
        final JsonObject flat = populate(new IsmTranslator(null), FhirConnectConst.CODE_PHRASE, terminology());

        assertEquals("532", flat.get(PATH + "|code").getAsString());
        assertEquals("532", flat.get(PATH + "|value").getAsString());
        assertFalse(flat.has(PATH + "|terminology"));
    }

    @Test
    void noTerminologyWritesTheEnumerationValueAsBoth() {
        final JsonObject flat = populate(new IsmTranslator("completed"), FhirConnectConst.DV_CODED_TEXT, null);

        assertEquals("completed", flat.get(PATH + "|code").getAsString());
        assertEquals("completed", flat.get(PATH + "|value").getAsString());
        assertFalse(flat.has(PATH + "|terminology"));
    }
}
