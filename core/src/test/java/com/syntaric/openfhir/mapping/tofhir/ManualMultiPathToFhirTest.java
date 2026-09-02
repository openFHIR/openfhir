package com.syntaric.openfhir.mapping.tofhir;

import com.nedap.archie.rm.composition.Composition;
import com.syntaric.openfhir.mapping.GenericTest;
import java.util.List;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.serialisation.flatencoding.std.umarshal.FlatJsonUnmarshaller;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

/**
 * Reproduces the issue where a manual mapping with multiple NESTED fhir paths sharing a common
 * prefix (e.g. "code.coding.code" + "code.coding.system" relative to $fhirRoot) ends up with only
 * the last entry populated — each entry overwrites the previously created intermediate elements
 * instead of merging into them.
 * <p>
 * The fixture set in /manual_multipath/ is a copy of /blood_pressure/ where:
 * <ul>
 *   <li>the systolic loincCode manual mapping uses nested paths from "$fhirRoot":
 *       "code.coding.code", "code.coding.system" and the mixed-depth sibling "code.text"
 *       (the reported broken variant)</li>
 *   <li>the diastolic loincCode manual mapping targets "$fhirRoot.code.coding" directly with
 *       simple paths "code" and "system" (the reported working variant, kept as a control)</li>
 * </ul>
 */
public class ManualMultiPathToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/manual_multipath/";
    final String CONTEXT_MAPPING = "/manual_multipath/simple-blood-pressure.context.yml";
    final String HELPER_LOCATION = "/manual_multipath/";
    final String OPT = "Blood Pressure.opt";
    final String FLAT = "blood-pressure_flat.json";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(HELPER_LOCATION + OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    private List<Observation> mapToObservations() {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             new OPTParser(
                                                                                     operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        return bundle.getEntry().stream()
                .map(en -> (Observation) en.getResource())
                .collect(Collectors.toList());
    }

    private static String describeComponentCodings(final Observation observation) {
        return observation.getComponent().stream()
                .flatMap(comp -> comp.getCode().getCoding().stream())
                .map(coding -> "[code=" + coding.getCode() + ", system=" + coding.getSystem() + "]")
                .collect(Collectors.joining(", "));
    }

    /**
     * Broken variant: manual with two nested paths ("code.coding.code" + "code.coding.system")
     * relative to $fhirRoot. Both values must end up on the SAME coding of the systolic component.
     */
    @Test
    public void nestedManualPathsBothApplied() {
        final List<Observation> observations = mapToObservations();
        Assert.assertEquals(3, observations.size());

        for (final Observation observation : observations) {
            final List<Coding> systolicCodings = observation.getComponent().stream()
                    .flatMap(comp -> comp.getCode().getCoding().stream())
                    .filter(coding -> "8480-6".equals(coding.getCode()))
                    .collect(Collectors.toList());
            Assert.assertFalse(
                    "No component coding with code 8480-6 found (it was overwritten by the second manual "
                            + "entry); present codings: " + describeComponentCodings(observation),
                    systolicCodings.isEmpty());
            Assert.assertTrue(
                    "Coding with code 8480-6 is missing system http://loinc.org; present codings: "
                            + describeComponentCodings(observation),
                    systolicCodings.stream().anyMatch(coding -> "http://loinc.org".equals(coding.getSystem())));
            // mixed-depth sibling ("code.text" next to "code.coding.*") must land on the same
            // CodeableConcept as the coding
            Assert.assertTrue(
                    "Component with the 8480-6 coding is missing code.text 'Systolic blood pressure'",
                    observation.getComponent().stream()
                            .filter(comp -> comp.getCode().getCoding().stream()
                                    .anyMatch(coding -> "8480-6".equals(coding.getCode())))
                            .anyMatch(comp -> "Systolic blood pressure".equals(comp.getCode().getText())));
        }
    }

    /**
     * Control variant: manual targeting "$fhirRoot.code.coding" directly with simple paths
     * ("code" + "system"). Expected to work per the report.
     */
    @Test
    public void simpleManualPathsOnCodingRootBothApplied() {
        final List<Observation> observations = mapToObservations();
        Assert.assertEquals(3, observations.size());

        for (final Observation observation : observations) {
            final List<Coding> diastolicCodings = observation.getComponent().stream()
                    .flatMap(comp -> comp.getCode().getCoding().stream())
                    .filter(coding -> "8462-4".equals(coding.getCode()))
                    .collect(Collectors.toList());
            Assert.assertFalse(
                    "No component coding with code 8462-4 found; present codings: "
                            + describeComponentCodings(observation),
                    diastolicCodings.isEmpty());
            Assert.assertTrue(
                    "Coding with code 8462-4 is missing system http://loinc.org; present codings: "
                            + describeComponentCodings(observation),
                    diastolicCodings.stream().anyMatch(coding -> "http://loinc.org".equals(coding.getSystem())));
        }
    }
}
