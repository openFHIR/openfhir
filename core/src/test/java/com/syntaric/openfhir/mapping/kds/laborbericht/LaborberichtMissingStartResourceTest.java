package com.syntaric.openfhir.mapping.kds.laborbericht;

import com.google.gson.JsonObject;
import com.syntaric.openfhir.mapping.kds.KdsGenericTest;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.junit.Assert;
import org.junit.Test;

/**
 * Covers issue #119: input that carries none of the resource type the template starts from used to crash with a
 * bare JDK "Index 0 out of bounds for length 0" instead of reporting that nothing could be mapped.
 * <p>
 * The KDS Laborbericht template starts from {@code OBSERVATION.laboratory_test_result.v1}, whose generating FHIR
 * resource is a DiagnosticReport — so a Bundle holding only an Observation has no starting resource.
 */
public class LaborberichtMissingStartResourceTest extends KdsGenericTest {

    final String MODEL_MAPPINGS = "/kds/core";
    final String CONTEXT = "/kds/core/projects/org.highmed/KDS/laborbericht/KDS_laborbericht.context.yaml";
    final String OPT = "/kds/laborbericht/KDS_Laborbericht.opt";

    @SneakyThrows
    @Override
    public void prepareState() {
        context = getContext(CONTEXT);
        operationaltemplateSerialized = IOUtils.toString(this.getClass().getResourceAsStream(OPT));
        operationaltemplate = getOperationalTemplate();
        repo.initRepository(context, operationaltemplate, getClass().getResource(MODEL_MAPPINGS).getFile());
        webTemplate = new OPTParser(operationaltemplate).parse();
    }

    @Test
    public void bareObservationYieldsEmptyFlatAndAReportedIssue() {
        final Bundle bundle = new Bundle();
        bundle.addEntry().setResource(new Observation());

        final MappingIssueCollector issueCollector = new MappingIssueCollector();
        final JsonObject flat = toOpenEhr.fhirToFlatJsonObject(context, bundle, webTemplate, issueCollector);

        Assert.assertNotNull(flat);
        Assert.assertTrue("nothing should have been mapped, got: " + flat, flat.entrySet().isEmpty());
        Assert.assertFalse("the caller should be told why nothing was mapped", issueCollector.isEmpty());
    }

    /**
     * A Bundle with no entries at all takes the same route — it must not crash either.
     */
    @Test
    public void emptyBundleYieldsEmptyFlatAndAReportedIssue() {
        final MappingIssueCollector issueCollector = new MappingIssueCollector();
        final JsonObject flat = toOpenEhr.fhirToFlatJsonObject(context, new Bundle(), webTemplate, issueCollector);

        Assert.assertNotNull(flat);
        Assert.assertTrue(flat.entrySet().isEmpty());
        Assert.assertFalse(issueCollector.isEmpty());
    }
}
