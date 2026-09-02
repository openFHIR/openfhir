package com.syntaric.openfhir.rest;

import com.syntaric.openfhir.OpenFhirEngine;
import com.syntaric.openfhir.operations.FhirOperationsService;
import com.syntaric.openfhir.operations.MappingCallContext;
import com.syntaric.openfhir.operations.MappingIssueCollector;
import com.syntaric.openfhir.operations.NoOpPatientResolver;
import com.syntaric.openfhir.operations.OperationOutcomeFactory;
import com.syntaric.openfhir.operations.OperationRequestParser;
import com.syntaric.openfhir.operations.ProvenanceGenerator;
import com.syntaric.openfhir.operations.SubjectReferencePopulator;
import com.syntaric.openfhir.producers.FhirContextRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Provenance;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class FhirOperationsControllerTest {

    private static final String CANONICAL_COMPOSITION = "{\"_type\": \"COMPOSITION\"}";
    private static final String FLAT_COMPOSITION = "{\"growth_chart/context/start_time\": \"2022-02-03T04:05:06\"}";

    private final FhirContextRegistry fhirContextRegistry = new FhirContextRegistry();
    private OpenFhirEngine openFhirEngine;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        openFhirEngine = Mockito.mock(OpenFhirEngine.class);
        final OperationOutcomeFactory outcomeFactory = new OperationOutcomeFactory();
        final FhirOperationsService service = new FhirOperationsService(openFhirEngine,
                new SubjectReferencePopulator(new NoOpPatientResolver()),
                new ProvenanceGenerator("Device/openfhir-engine", "openFHIR engine"),
                outcomeFactory,
                fhirContextRegistry);
        final FhirOperationsController controller = new FhirOperationsController(service,
                new OperationRequestParser(fhirContextRegistry));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new FhirOperationExceptionHandler(outcomeFactory, fhirContextRegistry))
                .build();
    }

    private String toFhirBody(final String composition, final String templateId, final String patientRef) {
        final Parameters parameters = new Parameters();
        parameters.addParameter().setName("composition").setValue(new StringType(composition));
        if (templateId != null) {
            parameters.addParameter().setName("templateId").setValue(new StringType(templateId));
        }
        if (patientRef != null) {
            parameters.addParameter().setName("context")
                    .addPart().setName("patient").setValue(new Reference(patientRef));
        }
        return encode(parameters);
    }

    private String encode(final org.hl7.fhir.r4.model.Resource resource) {
        return fhirContextRegistry.getDefaultContext().newJsonParser().encodeResourceToString(resource);
    }

    private <T extends org.hl7.fhir.r4.model.Resource> T parse(final Class<T> type, final String body) {
        return fhirContextRegistry.getDefaultContext().newJsonParser().parseResource(type, body);
    }

    /**
     * The mock has no gson wired, so mimic {@link OpenFhirEngine#deduceIncomingPayloadType} for the two
     * composition fixtures used here.
     */
    private void stubPayloadTypeDetection() {
        Mockito.when(openFhirEngine.deduceIncomingPayloadType(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("\"_type\"")
                        ? OpenFhirEngine.IncomingOpenEhrType.COMPOSITION
                        : OpenFhirEngine.IncomingOpenEhrType.FLAT);
    }

    private void mockEngineToFhirBundle(final Bundle bundle) {
        stubPayloadTypeDetection();
        Mockito.when(openFhirEngine.toFhirBundle(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.any(MappingCallContext.class), ArgumentMatchers.any(MappingIssueCollector.class)))
                .thenReturn(bundle);
    }

    @Test
    public void toFhirHappyPathReturnsBundleWithProvenanceAndSubject() throws Exception {
        final Bundle mapped = new Bundle();
        mapped.addEntry().setResource(new Observation());
        mockEngineToFhirBundle(mapped);

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .accept(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .content(toFhirBody(CANONICAL_COMPOSITION, "Growth chart", "Patient/ctx-1")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        Assert.assertTrue(response.getContentType().startsWith(FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE));
        final Bundle bundle = parse(Bundle.class, response.getContentAsString());
        Assert.assertEquals(2, bundle.getEntry().size());
        final Observation observation = (Observation) bundle.getEntry().get(0).getResource();
        Assert.assertEquals("Patient/ctx-1", observation.getSubject().getReference());
        final Provenance provenance = (Provenance) bundle.getEntry().get(1).getResource();
        Assert.assertEquals(1, provenance.getTarget().size());
        Assert.assertEquals("Device/openfhir-engine", provenance.getAgentFirstRep().getWho().getReference());
    }

    @Test
    public void toFhirFlatWithoutTemplateIdIsRejectedWithOperationOutcome() throws Exception {
        stubPayloadTypeDetection();

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .content(toFhirBody(FLAT_COMPOSITION, null, null)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        final OperationOutcome outcome = parse(OperationOutcome.class, response.getContentAsString());
        Assert.assertEquals(OperationOutcome.IssueType.REQUIRED, outcome.getIssueFirstRep().getCode());
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
    }

    @Test
    public void toFhirFlatWithQueryTemplateIdPasses() throws Exception {
        final Bundle mapped = new Bundle();
        mapped.addEntry().setResource(new Observation());
        mockEngineToFhirBundle(mapped);

        mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .param("templateId", "Growth chart")
                        .content(toFhirBody(FLAT_COMPOSITION, null, null)))
                .andExpect(status().isOk());

        Mockito.verify(openFhirEngine).toFhirBundle(ArgumentMatchers.eq(FLAT_COMPOSITION),
                ArgumentMatchers.eq("Growth chart"), ArgumentMatchers.any(), ArgumentMatchers.any());
    }

    @Test
    public void toFhirQueryContextAliasIsApplied() throws Exception {
        final Bundle mapped = new Bundle();
        mapped.addEntry().setResource(new Observation());
        mockEngineToFhirBundle(mapped);

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .param("patient", "Patient/from-query")
                        .param("who", "Practitioner/from-query")
                        .content(toFhirBody(CANONICAL_COMPOSITION, "Growth chart", null)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        final Bundle bundle = parse(Bundle.class, response.getContentAsString());
        final Observation observation = (Observation) bundle.getEntry().get(0).getResource();
        Assert.assertEquals("Patient/from-query", observation.getSubject().getReference());
        final Provenance provenance = (Provenance) bundle.getEntry().get(1).getResource();
        Assert.assertEquals("Practitioner/from-query", provenance.getAgentFirstRep().getWho().getReference());
    }

    @Test
    public void toFhirBodyContextWinsOverQueryAlias() throws Exception {
        final Bundle mapped = new Bundle();
        mapped.addEntry().setResource(new Observation());
        mockEngineToFhirBundle(mapped);

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .param("patient", "Patient/from-query")
                        .content(toFhirBody(CANONICAL_COMPOSITION, "Growth chart", "Patient/from-body")))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        final Bundle bundle = parse(Bundle.class, response.getContentAsString());
        final Observation observation = (Observation) bundle.getEntry().get(0).getResource();
        Assert.assertEquals("Patient/from-body", observation.getSubject().getReference());
    }

    @Test
    public void toFhirAcceptsPlainApplicationJson() throws Exception {
        final Bundle mapped = new Bundle();
        mapped.addEntry().setResource(new Observation());
        mockEngineToFhirBundle(mapped);

        mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType("application/json")
                        .content(toFhirBody(CANONICAL_COMPOSITION, "Growth chart", null)))
                .andExpect(status().isOk());
    }

    @Test
    public void toFhirEngineErrorsBecomeOperationOutcome() throws Exception {
        stubPayloadTypeDetection();
        Mockito.when(openFhirEngine.toFhirBundle(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Composition not properly unmarshalled"));

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .content(toFhirBody(CANONICAL_COMPOSITION, "Growth chart", null)))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        final OperationOutcome outcome = parse(OperationOutcome.class, response.getContentAsString());
        Assert.assertEquals(OperationOutcome.IssueType.PROCESSING, outcome.getIssueFirstRep().getCode());
        Assert.assertTrue(outcome.getIssueFirstRep().getDiagnostics()
                .contains("Composition not properly unmarshalled"));
    }

    @Test
    public void toOpenEhrHappyPathReturnsParametersWithComposition() throws Exception {
        Mockito.when(openFhirEngine.toOpenEhr(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(MappingIssueCollector.class)))
                .thenReturn("{\"_type\": \"COMPOSITION\"}");

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$toopenehr")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .param("templateId", "Growth chart")
                        .content("{\"resourceType\": \"Bundle\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        Assert.assertTrue(response.getContentType().startsWith(FhirMediaTypes.APPLICATION_FHIR_JSON_VALUE));
        final Parameters parameters = parse(Parameters.class, response.getContentAsString());
        final Parameters.ParametersParameterComponent composition = parameters.getParameter().stream()
                .filter(p -> "composition".equals(p.getName())).findFirst().orElse(null);
        Assert.assertNotNull(composition);
        Assert.assertEquals("{\"_type\": \"COMPOSITION\"}", composition.getValue().primitiveValue());
        Assert.assertTrue(parameters.getParameter().stream().noneMatch(p -> "outcome".equals(p.getName())));
    }

    @Test
    public void toOpenEhrFormatFlatIsPassedToEngine() throws Exception {
        Mockito.when(openFhirEngine.toOpenEhr(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(MappingIssueCollector.class)))
                .thenReturn("{}");

        mockMvc.perform(MockMvcRequestBuilders.post("/$toopenehr")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .param("format", "flat")
                        .content("{\"resourceType\": \"Bundle\"}"))
                .andExpect(status().isOk());

        final ArgumentCaptor<Boolean> flatCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(openFhirEngine).toOpenEhr(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                flatCaptor.capture(), ArgumentMatchers.any(MappingIssueCollector.class));
        Assert.assertTrue(flatCaptor.getValue());
    }

    @Test
    public void toOpenEhrDefaultsToCanonical() throws Exception {
        Mockito.when(openFhirEngine.toOpenEhr(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(MappingIssueCollector.class)))
                .thenReturn("{}");

        mockMvc.perform(MockMvcRequestBuilders.post("/$toopenehr")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .content("{\"resourceType\": \"Bundle\"}"))
                .andExpect(status().isOk());

        final ArgumentCaptor<Boolean> flatCaptor = ArgumentCaptor.forClass(Boolean.class);
        Mockito.verify(openFhirEngine).toOpenEhr(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                flatCaptor.capture(), ArgumentMatchers.any(MappingIssueCollector.class));
        Assert.assertFalse(flatCaptor.getValue());
    }

    @Test
    public void toOpenEhrInvalidFormatIsRejectedWithOperationOutcome() throws Exception {
        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$toopenehr")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .param("format", "xml")
                        .content("{\"resourceType\": \"Bundle\"}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse();

        final OperationOutcome outcome = parse(OperationOutcome.class, response.getContentAsString());
        Assert.assertEquals(OperationOutcome.IssueType.INVALID, outcome.getIssueFirstRep().getCode());
    }

    @Test
    public void toOpenEhrSurfacesCollectedIssuesAsOutcomeParameter() throws Exception {
        Mockito.when(openFhirEngine.toOpenEhr(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.anyBoolean(), ArgumentMatchers.any(MappingIssueCollector.class)))
                .thenAnswer(invocation -> {
                    final MappingIssueCollector collector = invocation.getArgument(3);
                    collector.addWarning("a gap was found");
                    return "{}";
                });

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$toopenehr")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .content("{\"resourceType\": \"Bundle\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        final Parameters parameters = parse(Parameters.class, response.getContentAsString());
        final Parameters.ParametersParameterComponent outcomeParam = parameters.getParameter().stream()
                .filter(p -> "outcome".equals(p.getName())).findFirst().orElse(null);
        Assert.assertNotNull(outcomeParam);
        final OperationOutcome outcome = (OperationOutcome) outcomeParam.getResource();
        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, outcome.getIssueFirstRep().getSeverity());
        Assert.assertEquals(OperationOutcome.IssueType.INCOMPLETE, outcome.getIssueFirstRep().getCode());
        Assert.assertEquals("a gap was found", outcome.getIssueFirstRep().getDiagnostics());
    }

    @Test
    public void toFhirSurfacesCollectedIssuesAsOperationOutcomeEntry() throws Exception {
        stubPayloadTypeDetection();
        Mockito.when(openFhirEngine.toFhirBundle(ArgumentMatchers.anyString(), ArgumentMatchers.any(),
                        ArgumentMatchers.any(MappingCallContext.class), ArgumentMatchers.any(MappingIssueCollector.class)))
                .thenAnswer(invocation -> {
                    final MappingIssueCollector collector = invocation.getArgument(3);
                    collector.addWarning("skipped an element");
                    final Bundle bundle = new Bundle();
                    bundle.addEntry().setResource(new Observation());
                    return bundle;
                });

        final MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.post("/$tofhir")
                        .contentType(FhirMediaTypes.APPLICATION_FHIR_JSON)
                        .content(toFhirBody(CANONICAL_COMPOSITION, "Growth chart", null)))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        final Bundle bundle = parse(Bundle.class, response.getContentAsString());
        // Observation + Provenance + OperationOutcome
        Assert.assertEquals(3, bundle.getEntry().size());
        final OperationOutcome outcome = bundle.getEntry().stream()
                .filter(e -> e.getResource() instanceof OperationOutcome)
                .map(e -> (OperationOutcome) e.getResource())
                .findFirst().orElse(null);
        Assert.assertNotNull(outcome);
        Assert.assertEquals("skipped an element", outcome.getIssueFirstRep().getDiagnostics());
    }
}
