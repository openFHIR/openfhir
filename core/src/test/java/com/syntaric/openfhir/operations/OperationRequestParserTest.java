package com.syntaric.openfhir.operations;

import com.syntaric.openfhir.producers.FhirContextRegistry;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class OperationRequestParserTest {

    private final FhirContextRegistry fhirContextRegistry = new FhirContextRegistry();
    private OperationRequestParser parser;

    @Before
    public void setUp() {
        parser = new OperationRequestParser(fhirContextRegistry);
    }

    private String encode(final Parameters parameters) {
        return fhirContextRegistry.getDefaultContext().newJsonParser().encodeResourceToString(parameters);
    }

    private Parameters fullBody() {
        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("composition").setValue(new StringType("{\"a/b\": 1}"));
        parameters.addParameter().setName("templateId").setValue(new StringType("body-template"));
        final Parameters.ParametersParameterComponent context = parameters.addParameter().setName("context");
        context.addPart().setName("ehr_id").setValue(new StringType("body-ehr"));
        context.addPart().setName("patient").setValue(new Reference("Patient/body"));
        context.addPart().setName("who").setValue(new Reference("Practitioner/body"));
        context.addPart().setName("onBehalfOf").setValue(new Reference("Organization/body"));
        return parameters;
    }

    @Test
    public void bodyTakesPrecedenceOverQueryParams() {
        final ToFhirOperationRequest request = parser.parseToFhirRequest(encode(fullBody()),
                "query-template", "query-ehr", "Patient/query", "Practitioner/query", "Organization/query", "req-1");

        Assert.assertEquals("{\"a/b\": 1}", request.getComposition());
        Assert.assertEquals("body-template", request.getTemplateId());
        Assert.assertEquals("body-ehr", request.getCallContext().getEhrId());
        Assert.assertEquals("Patient/body", request.getCallContext().getPatientRef());
        Assert.assertEquals("Practitioner/body", request.getCallContext().getWhoRef());
        Assert.assertEquals("Organization/body", request.getCallContext().getOnBehalfOfRef());
        Assert.assertEquals("req-1", request.getCallContext().getRequestId());
    }

    @Test
    public void queryParamsFillFieldsMissingFromBody() {
        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("composition").setValue(new StringType("{\"a/b\": 1}"));

        final ToFhirOperationRequest request = parser.parseToFhirRequest(encode(parameters),
                "query-template", "query-ehr", "Patient/query", "Practitioner/query", "Organization/query", null);

        Assert.assertEquals("query-template", request.getTemplateId());
        Assert.assertEquals("query-ehr", request.getCallContext().getEhrId());
        Assert.assertEquals("Patient/query", request.getCallContext().getPatientRef());
        Assert.assertEquals("Practitioner/query", request.getCallContext().getWhoRef());
        Assert.assertEquals("Organization/query", request.getCallContext().getOnBehalfOfRef());
    }

    @Test
    public void partialBodyContextMergesWithQuery() {
        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("composition").setValue(new StringType("{}"));
        final Parameters.ParametersParameterComponent context = parameters.addParameter().setName("context");
        context.addPart().setName("patient").setValue(new Reference("Patient/body"));

        final ToFhirOperationRequest request = parser.parseToFhirRequest(encode(parameters),
                null, "query-ehr", "Patient/query", null, null, null);

        Assert.assertEquals("Patient/body", request.getCallContext().getPatientRef());
        Assert.assertEquals("query-ehr", request.getCallContext().getEhrId());
        Assert.assertNull(request.getCallContext().getWhoRef());
        Assert.assertNull(request.getTemplateId());
    }

    @Test
    public void contextPartsAcceptStringValues() {
        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("composition").setValue(new StringType("{}"));
        final Parameters.ParametersParameterComponent context = parameters.addParameter().setName("context");
        context.addPart().setName("patient").setValue(new StringType("Patient/as-string"));

        final ToFhirOperationRequest request = parser.parseToFhirRequest(encode(parameters),
                null, null, null, null, null, null);

        Assert.assertEquals("Patient/as-string", request.getCallContext().getPatientRef());
    }

    @Test
    public void missingCompositionIsRejectedAsRequired() {
        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("templateId").setValue(new StringType("t"));
        try {
            parser.parseToFhirRequest(encode(parameters), null, null, null, null, null, null);
            Assert.fail("expected OperationRequestException");
        } catch (final OperationRequestException e) {
            Assert.assertEquals(OperationOutcome.IssueType.REQUIRED, e.getIssueType());
            Assert.assertEquals(400, e.getStatus().value());
        }
    }

    @Test
    public void malformedBodyIsRejectedAsStructure() {
        try {
            parser.parseToFhirRequest("this is not json", null, null, null, null, null, null);
            Assert.fail("expected OperationRequestException");
        } catch (final OperationRequestException e) {
            Assert.assertEquals(OperationOutcome.IssueType.STRUCTURE, e.getIssueType());
            Assert.assertEquals(400, e.getStatus().value());
        }
    }

    @Test
    public void blankBodyIsRejectedAsRequired() {
        try {
            parser.parseToFhirRequest("  ", null, null, null, null, null, null);
            Assert.fail("expected OperationRequestException");
        } catch (final OperationRequestException e) {
            Assert.assertEquals(OperationOutcome.IssueType.REQUIRED, e.getIssueType());
        }
    }

    @Test
    public void formatDefaultsToCanonicalAndValidates() {
        Assert.assertEquals("canonical", parser.parseFormat(null));
        Assert.assertEquals("canonical", parser.parseFormat(""));
        Assert.assertEquals("canonical", parser.parseFormat("CANONICAL"));
        Assert.assertEquals("flat", parser.parseFormat("flat"));
        Assert.assertEquals("flat", parser.parseFormat("Flat"));
        try {
            parser.parseFormat("xml");
            Assert.fail("expected OperationRequestException");
        } catch (final OperationRequestException e) {
            Assert.assertEquals(OperationOutcome.IssueType.INVALID, e.getIssueType());
        }
    }
}
