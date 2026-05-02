package com.syntaric.openfhir.mapping.stu3;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.datavalues.quantity.DvQuantity;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.mapping.GenericTest;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.StringType;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * End-to-end bidirectional mapping test for the STU3 FHIR version.
 * Extends {@link GenericTest} and overrides {@link #getFhirContext()} /
 * {@link #getFhirPath()} to wire all engines with STU3 instead of R4.
 */
public class Stu3BloodPressureBidirectionalTest extends GenericTest {

    private static final String MODEL_MAPPINGS  = "/stu3_blood_pressure/";
    private static final String CONTEXT_MAPPING = "/stu3_blood_pressure/stu3-blood-pressure.context.yml";
    private static final String OPT             = "Blood Pressure.opt";
    private static final String FLAT            = "stu3-blood-pressure_flat.json";
    private static final String BUNDLE_PATH     = "/stu3_blood_pressure/stu3-blood-pressure-bundle.json";

    private final FhirContextRegistry registry = new FhirContextRegistry();

    @Override
    protected FhirContext getFhirContext() {
        return registry.getContext(Spec.Version.STU3);
    }

    @Override
    protected IFhirPath getFhirPath() {
        return registry.getFhirPath(Spec.Version.STU3);
    }

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(
                getClass().getResourceAsStream(MODEL_MAPPINGS + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate,
                getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    // -------------------------------------------------------------------------
    // openEHR → FHIR (toFhir direction)
    // -------------------------------------------------------------------------

    @Test
    public void toFhir_systolicComponentPresent() throws IOException {
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(unmarshalFlat()), webTemplate);

        Assert.assertFalse("Bundle must have entries", bundle.getEntry().isEmpty());
        final Observation obs =
                (Observation) bundle.getEntry().get(0).getResource();

        final boolean hasSystolic = obs.getComponent().stream()
                .anyMatch(c -> c.getCode().getCoding().stream()
                        .anyMatch(coding -> "8480-6".equals(coding.getCode())));
        Assert.assertTrue("Systolic component (8480-6) must be present", hasSystolic);
    }

    @Test
    public void toFhir_diastolicComponentPresent() throws IOException {
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(unmarshalFlat()), webTemplate);

        final Observation obs =
                (Observation) bundle.getEntry().get(0).getResource();

        final boolean hasDiastolic = obs.getComponent().stream()
                .anyMatch(c -> c.getCode().getCoding().stream()
                        .anyMatch(coding -> "8462-4".equals(coding.getCode())));
        Assert.assertTrue("Diastolic component (8462-4) must be present", hasDiastolic);
    }

    // -------------------------------------------------------------------------
    // FHIR → openEHR with the STU3 sample bundle
    // -------------------------------------------------------------------------

    @Test
    public void toOpenEhr_systolicFromStu3Bundle() throws IOException {
        final Composition result = toOpenEhr.fhirToCompositionRm(context, parseStu3(), webTemplate);

        final List<Object> values = result.itemsAtPath(systolicPath());
        Assert.assertFalse("Systolic must be present in the mapped composition", values.isEmpty());
        Assert.assertEquals("Systolic must be 120 mmHg", 120.0, ((DvQuantity) values.get(0)).getMagnitude(), 0.001);
    }

    @Test
    public void toOpenEhr_diastolicFromStu3Bundle() throws IOException {
        final Composition result = toOpenEhr.fhirToCompositionRm(context, parseStu3(), webTemplate);

        final List<Object> values = result.itemsAtPath(diastolicPath());
        Assert.assertFalse("Diastolic must be present in the mapped composition", values.isEmpty());
        Assert.assertEquals("Diastolic must be 80 mmHg", 80.0, ((DvQuantity) values.get(0)).getMagnitude(), 0.001);
    }

    // -------------------------------------------------------------------------
    // STU3 FHIRPath engine — Observation.related is STU3-only
    // -------------------------------------------------------------------------

    /**
     * Verifies that {@link #getFhirPath()} returns the STU3 engine, which resolves
     * {@code Observation.related.target.display} — a field removed in R4.
     * Evaluated directly against a parsed STU3 Observation (bypassing the mapping
     * engine, whose Java model is bound to {@code *}).
     */
    @Test
    public void stu3FhirPath_resolvesRelated_fromParsedBundle() throws IOException {
        final String json = IOUtils.toString(getClass().getResourceAsStream(BUNDLE_PATH));
        final org.hl7.fhir.dstu3.model.Bundle stu3Bundle =
                getFhirContext().newJsonParser().parseResource(org.hl7.fhir.dstu3.model.Bundle.class, json);

        final org.hl7.fhir.dstu3.model.Observation obs =
                (org.hl7.fhir.dstu3.model.Observation) stu3Bundle.getEntry().get(0).getResource();

        final Optional<StringType> display =
                getFhirPath().evaluateFirst(obs, "Observation.related.target.display", StringType.class);

        Assert.assertTrue("STU3 FHIRPath must resolve Observation.related.target.display", display.isPresent());
        Assert.assertEquals("Derived from ambulatory measurement", display.get().getValue());
    }

    // -------------------------------------------------------------------------
    // Bidirectional round-trip: flat → FHIR Bundle → openEHR
    // -------------------------------------------------------------------------

    @Test
    public void bidirectional_systolicRoundTrip() throws IOException {
        final Composition source = unmarshalFlat();
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(source), webTemplate);
        final Composition result = toOpenEhr.fhirToCompositionRm(context, bundle, webTemplate);

        assertQuantityRoundTrip("Systolic", source, result, systolicPath());
    }

    @Test
    public void bidirectional_diastolicRoundTrip() throws IOException {
        final Composition source = unmarshalFlat();
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(source), webTemplate);
        final Composition result = toOpenEhr.fhirToCompositionRm(context, bundle, webTemplate);

        assertQuantityRoundTrip("Diastolic", source, result, diastolicPath());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Composition unmarshalFlat() throws IOException {
        return new FlatJsonUnmarshaller().unmarshal(getFlat(MODEL_MAPPINGS + FLAT), webTemplate);
    }

    /**
     * Parses the STU3 sample bundle JSON with the R4 parser so it fits the
     * {@code Bundle} type expected by {@link com.syntaric.openfhir.mapping.toopenehr.ToOpenEhr}.
     * R4 retains the shared {@code component} fields (systolic/diastolic) and silently
     * drops the STU3-only {@code related} field.
     */
    private Bundle parseStu3() throws IOException {
        final String json = IOUtils.toString(getClass().getResourceAsStream(BUNDLE_PATH));
        return FhirContext.forDstu3().newJsonParser().parseResource(Bundle.class, json);
    }

    private void assertQuantityRoundTrip(final String label,
                                         final Composition source,
                                         final Composition result,
                                         final String path) {
        final List<Object> sourceValues = source.itemsAtPath(path);
        Assert.assertFalse(label + " source must not be empty", sourceValues.isEmpty());

        for (final Object sv : sourceValues) {
            final Double expected = ((DvQuantity) sv).getMagnitude();
            Assert.assertTrue(
                    label + " magnitude " + expected + " must survive round-trip",
                    result.itemsAtPath(path).stream()
                            .anyMatch(item -> ((DvQuantity) item).getMagnitude().equals(expected)));
        }
    }

    private static String systolicPath() {
        return "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]"
                + "/data[at0001]/events[at0006]/data[at0003]/items[at0004]/value";
    }

    private static String diastolicPath() {
        return "/content[openEHR-EHR-OBSERVATION.blood_pressure.v2]"
                + "/data[at0001]/events[at0006]/data[at0003]/items[at0005]/value";
    }
}
