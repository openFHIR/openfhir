package com.syntaric.openfhir;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.mapping.toaql.AqlPatientScope;
import com.syntaric.openfhir.mapping.toaql.ToAql;
import com.syntaric.openfhir.mapping.toaql.FederationGatewaySubjectPredicateRule;
import com.syntaric.openfhir.rest.RequestValidationException;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * {@link OpenFhirEngine#toAql}, what both {@code /openfhir/toaql} controllers call: the AQLs {@link ToAql} generates
 * are scoped to the patient the request names before they are returned.
 */
public class OpenFhirEngineToAqlTest {

    private static final String ID = "194303193454";
    private static final String NAMESPACE = "1.2.752.129.2.1.3.1";
    private static final String ENTRY = "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_id/value='{{ehrid}}'";
    /** a context's _query with the ehr_id in its FROM */
    private static final String QUERY_IN_FROM = "SELECT c FROM EHR e[ehr_id/value='{{ehrid}}'] CONTAINS COMPOSITION c[openEHR-EHR-COMPOSITION.health_summary.v1]";

    private ToAql toAql;
    private OpenFhirEngine engine;

    @Before
    public void setUp() {
        toAql = Mockito.mock(ToAql.class);
        // a fresh response per call: the engine scopes the one it gets
        doAnswer(invocation -> new ToAqlResponse()
                .addAql(ENTRY, ToAqlResponse.AqlType.ENTRY, "EPS Patient Summary")
                .addAql(QUERY_IN_FROM, ToAqlResponse.AqlType.COMPOSITION, "EPS Patient Summary"))
                .when(toAql).toAql(any());
        engine = new OpenFhirEngine(null, null, null, null, toAql, null, null, null, null, null, null, null, List.of());
    }

    @Test
    public void bySubject_scopesWhatCanBeAndLeavesOutTheRest() {
        final ToAqlResponse response = engine.toAql(new ToAqlRequest(null, null, "AllergyIntolerance",
                                                                     new ToAqlRequest.Subject(ID, NAMESPACE)));

        Assert.assertEquals(1, response.getAqls().size());
        Assert.assertEquals("SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.adverse_reaction_risk.v2] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1'",
                            response.getAqls().get(0).getAql());
        FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(response, ID, NAMESPACE);
        Assert.assertEquals(1, response.getUnhandledParams().size());
        Assert.assertEquals(AqlPatientScope.PATIENT_SCOPE_PARAM, response.getUnhandledParams().get(0).getParamName());
        Assert.assertEquals(ToAqlResponse.UnhandledParamType.ERROR, response.getUnhandledParams().get(0).getType());
    }

    @Test
    public void byEhrId_fillsThePlaceholder() {
        final ToAqlResponse response = engine.toAql(new ToAqlRequest(null, "7d44b88c", "AllergyIntolerance"));

        Assert.assertEquals(ENTRY.replace("{{ehrid}}", "7d44b88c"), response.getAqls().get(0).getAql());
        Assert.assertEquals(QUERY_IN_FROM.replace("{{ehrid}}", "7d44b88c"), response.getAqls().get(1).getAql());
        Assert.assertNull(response.getUnhandledParams());
    }

    @Test
    public void withoutAPatient_leavesThePlaceholder() {
        final ToAqlResponse response = engine.toAql(new ToAqlRequest(null, null, "AllergyIntolerance"));

        Assert.assertEquals(ENTRY, response.getAqls().get(0).getAql());
        Assert.assertEquals(QUERY_IN_FROM, response.getAqls().get(1).getAql());
    }

    @Test
    public void byEhrIdAndSubject_isRejectedBeforeAnythingIsGenerated() {
        Assert.assertThrows(RequestValidationException.class, () -> engine.toAql(
                new ToAqlRequest(null, "7d44b88c", "AllergyIntolerance", new ToAqlRequest.Subject(ID, null))));
        Assert.assertThrows(RequestValidationException.class, () -> engine.toAql(
                new ToAqlRequest(null, null, "AllergyIntolerance", new ToAqlRequest.Subject(" ", NAMESPACE))));
        verify(toAql, never()).toAql(any());
    }
}
