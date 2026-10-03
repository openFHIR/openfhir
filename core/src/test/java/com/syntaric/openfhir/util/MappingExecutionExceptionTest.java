package com.syntaric.openfhir.util;

import ca.uhn.fhir.parser.DataFormatException;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.MappingContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.operations.OperationRequestException;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpStatus;

/**
 * Message policy of {@link MappingExecutionException} (issue #120): the context is always named, a
 * caller-correctable cause keeps its text, an engine fault only shows its class name plus a reference id.
 */
public class MappingExecutionExceptionTest {

    private static final String NPE_TEXT =
            "Cannot invoke \"org.openehr.schemas.v1.OPERATIONALTEMPLATE.getLanguage()\" because it is null";

    private static MappingContext context() {
        final MappingHelper helper = new MappingHelper();
        helper.setModelMetadataName("Body weight");
        helper.setArchetype("openEHR-EHR-OBSERVATION.body_weight.v2");
        helper.setMappingName("weight");
        helper.setOriginalOpenEhrPath("$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0004]");
        helper.setOriginalFhirPath("$resource.value");
        return MappingContext.of(helper, FhirConnectConst.UNIDIRECTIONAL_TOFHIR);
    }

    @Test
    public void engineFaultHidesCauseTextAndCarriesReference() {
        final MappingExecutionException e = new MappingExecutionException(context(), new NullPointerException(NPE_TEXT));

        Assert.assertFalse(e.isCallerError());
        Assert.assertEquals("exception", e.issueCode());
        Assert.assertNotNull(e.getReference());
        final String message = e.getMessage();
        Assert.assertFalse("internal exception text leaked: " + message, message.contains("OPERATIONALTEMPLATE"));
        Assert.assertTrue(message, message.contains("NullPointerException"));
        Assert.assertTrue(message, message.contains("reference " + e.getReference()));
        Assert.assertTrue(message, message.contains("mapping 'weight' of model mapper 'Body weight'"));
        Assert.assertTrue(message, message.contains("archetype 'openEHR-EHR-OBSERVATION.body_weight.v2'"));
        Assert.assertTrue(message, message.contains("openEHR '$archetype/data[at0002]/events[at0003]/data[at0001]/items[at0004]'"));
        Assert.assertTrue(message, message.contains("FHIR '$resource.value'"));
        Assert.assertTrue(message, message.contains("while mapping to FHIR"));
        Assert.assertSame(e.getCause(), e.getCause());
    }

    @Test
    public void callerErrorEchoesCauseText() {
        final MappingExecutionException e = new MappingExecutionException(context(),
                new IllegalArgumentException("Unsupported RM type 'DV_FOO'"));

        Assert.assertTrue(e.isCallerError());
        Assert.assertEquals("processing", e.issueCode());
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("Unsupported RM type 'DV_FOO'"));
        Assert.assertFalse(e.getMessage(), e.getMessage().contains("see the engine log"));
    }

    @Test
    public void dataFormatExceptionIsAStructureCallerError() {
        final MappingExecutionException e = new MappingExecutionException(context(),
                new DataFormatException("Invalid date/time format: \"2024-13-45\""));

        Assert.assertTrue(e.isCallerError());
        Assert.assertEquals("structure", e.issueCode());
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("Invalid date/time format"));
    }

    @Test
    public void operationRequestExceptionIsAProcessingCallerError() {
        final MappingExecutionException e = new MappingExecutionException(context(),
                OperationRequestException.badRequest(OperationOutcome.IssueType.INVALID, "bad request"));

        Assert.assertTrue(e.isCallerError());
        Assert.assertEquals("processing", e.issueCode());
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("bad request"));
    }

    @Test
    public void issueCodesResolveToOperationOutcomeIssueTypes() {
        Assert.assertEquals(OperationOutcome.IssueType.STRUCTURE,
                OperationOutcome.IssueType.fromCode(MappingExecutionException.issueCode(new DataFormatException("x"))));
        Assert.assertEquals(OperationOutcome.IssueType.PROCESSING,
                OperationOutcome.IssueType.fromCode(MappingExecutionException.issueCode(new IllegalArgumentException("x"))));
        Assert.assertEquals(OperationOutcome.IssueType.EXCEPTION,
                OperationOutcome.IssueType.fromCode(MappingExecutionException.issueCode(new IllegalStateException("x"))));
        Assert.assertEquals(OperationOutcome.IssueType.PROCESSING,
                OperationOutcome.IssueType.fromCode(MappingExecutionException.issueCode(
                        new OperationRequestException(HttpStatus.BAD_REQUEST, OperationOutcome.IssueType.INVALID, "x"))));
    }

    @Test
    public void describeToleratesMissingFields() {
        final MappingContext context = MappingContext.of(new MappingHelper(), FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR);
        final MappingExecutionException e = new MappingExecutionException(context, new IllegalStateException("boom"));

        Assert.assertTrue(e.getMessage(), e.getMessage().startsWith(
                "Failed to execute mapping '?' of model mapper '?' (archetype '?', openEHR '?', FHIR '?') while mapping to openEHR: "));
    }

    @Test
    public void contextFallsBackToResolvedPathsWhenOriginalsAreMissing() {
        final MappingHelper helper = new MappingHelper();
        helper.setFullOpenEhrFlatPath("growth_chart/body_weight/any_event:0/weight");
        helper.setFhir("value");
        final MappingContext context = MappingContext.of(helper, FhirConnectConst.UNIDIRECTIONAL_TOOPENEHR);

        Assert.assertEquals("growth_chart/body_weight/any_event:0/weight", context.openEhrPath());
        Assert.assertEquals("value", context.fhirPath());
    }
}
