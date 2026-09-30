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
 * End to end: a DV_CODED_TEXT whose flat JSON carries a TERM_MAPPING ({@code _mapping:0}) is parsed
 * into a CodeableConcept with two codings, and a {@code fhirCondition} on the mapping — the same one
 * that filters codings in the FHIR→openEHR direction — selects which of them is written to FHIR.
 * Covers the amend → extract → populate chain through {@code $tofhir}.
 *
 * <p>Fixtures in /coding_selection/ reuse the /manual_multipath/ Blood Pressure template.
 */
public class CodingSelectionToFhirTest extends GenericTest {

    private static final String SNOMED = "http://snomed.info/sct";

    final String MODEL_MAPPINGS = "/coding_selection/";
    final String CONTEXT_MAPPING = "/coding_selection/blood-pressure.context.yml";
    final String FLAT = "/coding_selection/blood-pressure_flat.json";
    final String OPT = "/manual_multipath/Blood Pressure.opt";

    @SneakyThrows
    @Override
    protected void prepareState() {
        context = getContext(CONTEXT_MAPPING);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    private Observation mapToObservation() {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(FLAT),
                new OPTParser(operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        final List<Observation> observations = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .collect(Collectors.toList());
        Assert.assertEquals(1, observations.size());
        return observations.get(0);
    }

    private static List<String> systems(final List<Coding> codings) {
        return codings.stream().map(Coding::getSystem).collect(Collectors.toList());
    }

    /** {@code with.fhir: $resource.bodySite.coding} + condition {@code system one of SNOMED}. */
    @Test
    public void codingTarget_conditionSelectsTheTermMappingTarget() {
        final Observation observation = mapToObservation();

        Assert.assertEquals(1, observation.getBodySite().getCoding().size());
        final Coding bodySite = observation.getBodySite().getCodingFirstRep();
        Assert.assertEquals(SNOMED, bodySite.getSystem());
        Assert.assertEquals("368209003", bodySite.getCode());
    }

    /** {@code with.fhir: $resource.method} + condition {@code coding.system not of local}. */
    @Test
    public void codeableConceptTarget_conditionDropsTheLocalCoding() {
        final Observation observation = mapToObservation();

        Assert.assertEquals(List.of(SNOMED), systems(observation.getMethod().getCoding()));
        Assert.assertEquals("37931006", observation.getMethod().getCodingFirstRep().getCode());
        Assert.assertEquals("Auscultation", observation.getMethod().getText());
    }

    /**
     * Nested: {@code $resource.component} ({@code type: NONE}) → child {@code with.fhir: code} + condition
     * {@code targetRoot: code.coding}, spelled relative to the child. This is the dosageInstruction → route
     * shape of the Karolinska mappings.
     */
    @Test
    public void nestedCodeableConceptTarget_relativeCondition_selectsTheTermMappingTarget() {
        final Observation observation = mapToObservation();

        Assert.assertEquals(1, observation.getComponent().size());
        final org.hl7.fhir.r4.model.CodeableConcept code = observation.getComponentFirstRep().getCode();
        Assert.assertEquals(List.of(SNOMED), systems(code.getCoding()));
        Assert.assertEquals("37931006", code.getCodingFirstRep().getCode());
        Assert.assertEquals("Auscultation", code.getText());
    }

    /** A condition no coding satisfies is not a gate: the element is written with both codings. */
    @Test
    public void conditionNothingSatisfies_writesAllCodings() {
        final Observation observation = mapToObservation();

        Assert.assertEquals(List.of("local", SNOMED), systems(observation.getCode().getCoding()));
        Assert.assertEquals("at0015", observation.getCode().getCodingFirstRep().getCode());
        Assert.assertEquals("720737000", observation.getCode().getCoding().get(1).getCode());
    }
}
