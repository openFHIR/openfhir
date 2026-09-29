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
 * A FHIR path that walks <em>through</em> a single-valued element which an earlier walk already built must
 * continue into it, not replace it. Before, every intermediate segment was instantiated afresh and set on
 * the parent, so a second walk of {@code $resource.method.coding} discarded the first {@code method} with
 * its coding.
 * <p>
 * The fixture set in /single_parent_reuse/ reuses the /manual_multipath/ template and flat composition
 * (three {@code any_event} occurrences) with a model that anchors one Observation per OBSERVATION
 * instance and maps:
 * <ul>
 *   <li>the repeating {@code position} of every event to {@code $resource.method.coding} — one
 *       {@code method}, three codings</li>
 *   <li>{@code location_of_measurement} to {@code $resource.bodySite.coding} and, from a sibling
 *       top-level {@code manual}, {@code $resource.bodySite.text} — both on the same {@code bodySite}
 *       without a {@code type: NONE} parent</li>
 * </ul>
 */
public class SingleValuedParentReuseToFhirTest extends GenericTest {

    final String MODEL_MAPPINGS = "/single_parent_reuse/";
    final String CONTEXT_MAPPING = "/single_parent_reuse/blood-pressure.context.yml";
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

    private Observation mapToObservation() {
        final Composition composition = new FlatJsonUnmarshaller().unmarshal(getFlat(HELPER_LOCATION + FLAT),
                                                                             new OPTParser(
                                                                                     operationaltemplate).parse());
        final Bundle bundle = (Bundle) toFhir.compositionsToFhir(context, List.of(composition), webTemplate);
        final List<Observation> observations = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(Observation.class::isInstance)
                .map(Observation.class::cast)
                .collect(Collectors.toList());
        Assert.assertEquals("one Observation per OBSERVATION instance", 1, observations.size());
        return observations.get(0);
    }

    @Test
    public void repeatingElementThroughSingleValuedParent_keepsEveryOccurrence() {
        final Observation observation = mapToObservation();

        final List<String> codes = observation.getMethod().getCoding().stream()
                .map(Coding::getCode)
                .collect(Collectors.toList());
        Assert.assertEquals("every event's position must land on the one method, not only the last: " + codes,
                            List.of("at1000", "at1000", "at1000"), codes);
    }

    @Test
    public void siblingWritersThroughSingleValuedParent_shareIt() {
        final Observation observation = mapToObservation();

        Assert.assertEquals("at0025", observation.getBodySite().getCodingFirstRep().getCode());
        Assert.assertEquals("the manual text must join the coding on the same bodySite",
                            "THIS IS LOCATION OF MEASUREMENT", observation.getBodySite().getText());
    }
}
