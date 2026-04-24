package com.syntaric.openfhir.mapping.stu3;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import com.syntaric.openfhir.util.OpenFhirTestUtility;
import com.syntaric.openfhir.fc.schema.Spec;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Tests that STU3 support works end-to-end:
 * - the context YAML spec.version: STU3 is parsed correctly
 * - {@link FhirContextRegistry} returns a STU3-specific {@link FhirContext} and {@link IFhirPath}
 * - the STU3-only {@code Observation.related} field (removed in R4) is reachable via FHIRPath
 * - the STU3 FhirPath evaluation produces the expected display value used in the mapping
 */
public class Stu3BloodPressureTest {

    private static final String CONTEXT_PATH = "/stu3_blood_pressure/stu3-blood-pressure.context.yml";
    private static final String BUNDLE_PATH  = "/stu3_blood_pressure/stu3-blood-pressure-bundle.json";

    private FhirContextRegistry registry;
    private FhirConnectContext  context;

    @Before
    public void setUp() throws Exception {
        registry = new FhirContextRegistry();

        try (final InputStream is = getClass().getResourceAsStream(CONTEXT_PATH)) {
            context = OpenFhirTestUtility.getYaml().readValue(is, FhirConnectContext.class);
        }
    }

    // -------------------------------------------------------------------------
    // Context YAML parsing
    // -------------------------------------------------------------------------

    @Test
    public void contextYaml_specVersion_isSTU3() {
        Assert.assertNotNull("spec must not be null", context.getSpec());
        Assert.assertEquals(Spec.Version.STU3, context.getSpec().getVersion());
    }

    @Test
    public void contextYaml_templateId_isBloodPressure() {
        Assert.assertEquals("Blood Pressure", context.getContext().getTemplate().getId());
    }

    // -------------------------------------------------------------------------
    // FhirContextRegistry — STU3 instances
    // -------------------------------------------------------------------------

    @Test
    public void registry_stu3Context_isNotR4() {
        final FhirContext stu3 = registry.getContext(Spec.Version.STU3);
        final FhirContext r4   = registry.getContext(Spec.Version.R4);

        Assert.assertNotSame("STU3 and R4 contexts must be distinct instances", stu3, r4);
        Assert.assertEquals(ca.uhn.fhir.context.FhirVersionEnum.DSTU3, stu3.getVersion().getVersion());
        Assert.assertEquals(ca.uhn.fhir.context.FhirVersionEnum.R4,    r4.getVersion().getVersion());
    }

    @Test
    public void registry_stu3FhirPath_isCached() {
        final IFhirPath first  = registry.getFhirPath(Spec.Version.STU3);
        final IFhirPath second = registry.getFhirPath(Spec.Version.STU3);
        Assert.assertSame("registry must return the same cached IFhirPath instance", first, second);
    }

    // -------------------------------------------------------------------------
    // STU3-only field: Observation.related (removed in R4)
    // -------------------------------------------------------------------------

    @Test
    public void stu3FhirPath_observationRelated_isEvaluable() {
        final IFhirPath stu3Path = registry.getFhirPath(Spec.Version.STU3);

        // Build an STU3 Observation with the STU3-only .related field
        final Observation obs = new Observation();
        final Observation.ObservationRelatedComponent related = new Observation.ObservationRelatedComponent();
        related.setType(Observation.ObservationRelationshipType.DERIVEDFROM);
        final Reference ref = new Reference();
        ref.setDisplay("Derived from ambulatory measurement");
        related.setTarget(ref);
        obs.addRelated(related);

        // Evaluate the STU3-only path — this would throw / return empty under R4
        final List<StringType> results = stu3Path.evaluate(obs, "Observation.related.target.display", StringType.class);

        Assert.assertFalse("related.target.display must resolve to a non-empty list", results.isEmpty());
        Assert.assertEquals("Derived from ambulatory measurement", results.get(0).getValue());
    }

    @Test
    public void stu3FhirPath_observationRelated_fromParsedBundle() throws Exception {
        final FhirContext stu3Ctx  = registry.getContext(Spec.Version.STU3);
        final IFhirPath   stu3Path = registry.getFhirPath(Spec.Version.STU3);

        // Parse the sample STU3 JSON bundle
        try (final InputStream is = getClass().getResourceAsStream(BUNDLE_PATH)) {
            final Bundle bundle = stu3Ctx.newJsonParser().parseResource(Bundle.class, is);

            final Observation obs = (Observation) bundle.getEntry().get(0).getResource();

            final Optional<StringType> display = stu3Path.evaluateFirst(
                    obs, "Observation.related.target.display", StringType.class);

            Assert.assertTrue("related.target.display must be present in the parsed STU3 bundle", display.isPresent());
            Assert.assertEquals("Derived from ambulatory measurement", display.get().getValue());
        }
    }

    @Test
    public void r4FhirPath_observationRelated_isNotEvaluable() {
        // Verify that the same path fails under R4 — proving this field is truly STU3-only.
        // In R4, Observation.related does not exist; FHIRPath returns empty rather than a value.
        final FhirContext r4Ctx  = registry.getContext(Spec.Version.R4);
        final IFhirPath   r4Path = registry.getFhirPath(Spec.Version.R4);

        final org.hl7.fhir.r4.model.Observation r4Obs = new org.hl7.fhir.r4.model.Observation();
        // Attempt to add the field via raw JSON round-trip so FHIRPath sees the exact same structure
        final String stu3Json = registry.getContext(Spec.Version.STU3)
                .newJsonParser()
                .encodeResourceToString(buildStu3Observation());

        // Parse the STU3 JSON with the R4 parser — R4 will silently drop unknown fields like `related`
        final org.hl7.fhir.r4.model.Observation r4Parsed =
                r4Ctx.newJsonParser().parseResource(org.hl7.fhir.r4.model.Observation.class, stu3Json);

        final List<org.hl7.fhir.r4.model.StringType> results = r4Path.evaluate(
                r4Parsed, "Observation.related.target.display", org.hl7.fhir.r4.model.StringType.class);

        Assert.assertTrue(
                "R4 FHIRPath must not resolve Observation.related — it was removed in R4",
                results.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Observation buildStu3Observation() {
        final Observation obs = new Observation();
        final Observation.ObservationRelatedComponent related = new Observation.ObservationRelatedComponent();
        related.setType(Observation.ObservationRelationshipType.DERIVEDFROM);
        final Reference ref = new Reference();
        ref.setDisplay("Derived from ambulatory measurement");
        related.setTarget(ref);
        obs.addRelated(related);
        return obs;
    }
}
