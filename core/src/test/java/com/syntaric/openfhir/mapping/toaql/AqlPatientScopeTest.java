package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.FhirQueryParam;
import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.db.entity.FhirConnectContextEntity;
import com.syntaric.openfhir.fc.schema.context.Context;
import com.syntaric.openfhir.fc.schema.context.ContextTemplate;
import com.syntaric.openfhir.fc.schema.context.FhirConnectContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.rest.RequestValidationException;
import java.util.ArrayList;
import java.util.List;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.operand.StringPrimitive;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;
import org.junit.Assert;
import org.junit.Test;

/**
 * The patient scope of the AQLs {@code /toaql} returns: the {@code ehrId}, or the {@code subject} of the EHR, the
 * scope a federation gateway such as openEHR Federation Gatewayresolves the patient from.
 */
public class AqlPatientScopeTest {

    private static final String ID = "194303193454";
    private static final String NAMESPACE = "1.2.752.129.2.1.3.1";
    private static final String ENTRY = "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_id/value='{{ehrid}}'";
    private static final String ENTRY_WITH_CONDITION = ENTRY + " AND h/data[at0001]/items[at0002]/value/defining_code/code_string = '38341003'";

    @Test
    public void ehrId_fillsThePlaceholder() {
        final ToAqlResponse response = scope(new ToAqlRequest(null, "7d44b88c", "Condition"), ENTRY_WITH_CONDITION);

        Assert.assertEquals("SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_id/value='7d44b88c' AND h/data[at0001]/items[at0002]/value/defining_code/code_string = '38341003'",
                            aql(response));
        Assert.assertNull(response.getUnhandledParams());
    }

    @Test
    public void noPatient_leavesThePlaceholderForTheCaller() {
        final ToAqlResponse response = scope(new ToAqlRequest(null, null, "Condition"), ENTRY);

        Assert.assertEquals(ENTRY, aql(response));
        Assert.assertNull(response.getUnhandledParams());
    }

    @Test
    public void subject_scopesByTheSubjectInsteadOfTheEhrId() {
        final ToAqlResponse response = scope(subject(ID, null), ENTRY_WITH_CONDITION);

        Assert.assertEquals("SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND h/data[at0001]/items[at0002]/value/defining_code/code_string = '38341003'",
                            aql(response));
        Assert.assertNull(response.getUnhandledParams());
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject(ID, null), FederationGatewaySubjectPredicateRule.accept(aql(response)));
    }

    @Test
    public void subjectWithNamespace_andsTheNamespace() {
        final ToAqlResponse response = scope(subject(ID, NAMESPACE), ENTRY_WITH_CONDITION);

        Assert.assertEquals("SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454' AND e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1' AND h/data[at0001]/items[at0002]/value/defining_code/code_string = '38341003'",
                            aql(response));
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject(ID, NAMESPACE), FederationGatewaySubjectPredicateRule.accept(aql(response)));
    }

    @Test
    public void subjectWithBlankNamespace_leavesTheNamespaceOut() {
        final ToAqlResponse response = scope(subject(ID, " "), ENTRY);

        Assert.assertEquals("SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_status/subject/external_ref/id/value = '194303193454'",
                            aql(response));
    }

    @Test
    public void subject_quotesAndBackslashesCannotLeaveTheStringLiteral() {
        final String id = "1' OR '1'='1";
        final String namespace = "urn:x\\' OR e/ehr_id/value = 'y";

        final ToAqlResponse response = scope(subject(id, namespace), ENTRY_WITH_CONDITION);

        Assert.assertEquals("SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE e/ehr_status/subject/external_ref/id/value = '1\\' OR \\'1\\'=\\'1' AND e/ehr_status/subject/external_ref/namespace = 'urn:x\\\\\\' OR e/ehr_id/value = \\'y' AND h/data[at0001]/items[at0002]/value/defining_code/code_string = '38341003'",
                            aql(response));
        AqlQueryParser.parse(aql(response));
        // read back as exactly what was sent, and still one predicate each, ANDed at the top
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject(id, namespace),
                            FederationGatewaySubjectPredicateRule.accept(aql(response)));
    }

    @Test
    public void ehrIdAndSubjectTogether_areRejected() {
        final RequestValidationException e = Assert.assertThrows(RequestValidationException.class,
                () -> AqlPatientScope.validate(new ToAqlRequest(null, "7d44b88c", "Condition",
                                                                new ToAqlRequest.Subject(ID, NAMESPACE))));
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("ehrId") && e.getMessage().contains("subject"));
    }

    @Test
    public void subjectWithoutId_isRejected() {
        final RequestValidationException e = Assert.assertThrows(RequestValidationException.class,
                () -> AqlPatientScope.validate(subject(" ", NAMESPACE)));
        Assert.assertTrue(e.getMessage(), e.getMessage().contains("subject.id"));
    }

    @Test
    public void subjectOrEhrIdOrNeither_areValid() {
        AqlPatientScope.validate(subject(ID, null));
        AqlPatientScope.validate(new ToAqlRequest(null, "7d44b88c", "Condition"));
        AqlPatientScope.validate(new ToAqlRequest(null, null, "Condition"));
    }

    /** A context's {@code _query} scoped by a WHERE condition is scoped by subject too, whatever its EHR alias. */
    @Test
    public void subject_scopesAQueryWithTheEhrIdInWhere() {
        final ToAqlResponse response = scope(subject(ID, NAMESPACE),
                "SELECT c FROM EHR x CONTAINS COMPOSITION c[openEHR-EHR-COMPOSITION.health_summary.v1] WHERE x/ehr_id/value = '{{ehrid}}' ORDER BY c/context/start_time/value DESC");

        Assert.assertEquals("SELECT c FROM EHR x CONTAINS COMPOSITION c[openEHR-EHR-COMPOSITION.health_summary.v1] WHERE x/ehr_status/subject/external_ref/id/value = '194303193454' AND x/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1' ORDER BY c/context/start_time/value DESC",
                            aql(response));
        Assert.assertNull(response.getUnhandledParams());
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject(ID, NAMESPACE), FederationGatewaySubjectPredicateRule.accept(aql(response)));
    }

    /**
     * A context's {@code _query} with the ehr_id in the FROM ({@code EHR e[ehr_id/value='{{ehrid}}']}) cannot be
     * scoped by subject: it is left out of the response, with an ERROR saying so, so that nobody runs it unscoped.
     */
    @Test
    public void subject_leavesOutAQueryWithTheEhrIdInFrom() {
        assertLeftOut(subject(ID, NAMESPACE),
                      "SELECT c FROM EHR e[ehr_id/value='{{ehrid}}'] CONTAINS COMPOSITION c[openEHR-EHR-COMPOSITION.health_summary.v1]");
    }

    @Test
    public void subject_leavesOutAQueryWithoutAnyPatientScope() {
        assertLeftOut(subject(ID, null),
                      "SELECT c FROM EHR e CONTAINS COMPOSITION c[openEHR-EHR-COMPOSITION.health_summary.v1]");
    }

    /** The placeholder anywhere but in the one ehr_id condition would be left in the AQL. */
    @Test
    public void subject_leavesOutAQueryWithThePlaceholderTwice() {
        assertLeftOut(subject(ID, null), ENTRY + " AND h/data[at0001]/items[at0002]/value/value = '{{ehrid}}'");
        assertLeftOut(subject(ID, null),
                      "SELECT c FROM EHR e[ehr_id/value='{{ehrid}}'] CONTAINS COMPOSITION c WHERE e/ehr_id/value='{{ehrid}}'");
    }

    /** A condition the patient is not ANDed with at the top of the WHERE does not scope the query. */
    @Test
    public void subject_leavesOutAQueryWhereTheEhrIdIsUnderOrOrNot() {
        assertLeftOut(subject(ID, null),
                      "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE h/data[at0001]/items[at0002]/value/value = 'x' OR e/ehr_id/value='{{ehrid}}'");
        assertLeftOut(subject(ID, null),
                      "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE NOT e/ehr_id/value='{{ehrid}}'");
        // an OR at the top of the WHERE, as a search value with a quote in it can produce
        assertLeftOut(subject(ID, NAMESPACE),
                      ENTRY + " AND h/data[at0001]/items[at0002]/value/value = 'x' OR h/data[at0001]/items[at0002]/value/value = 'y'");
    }

    /** The condition has to be on the EHR, not on another alias that happens to have an ehr_id path. */
    @Test
    public void subject_leavesOutAQueryWhereTheEhrIdConditionIsNotOnTheEhr() {
        assertLeftOut(subject(ID, null),
                      "SELECT c FROM EHR e CONTAINS COMPOSITION c WHERE c/ehr_id/value='{{ehrid}}'");
    }

    @Test
    public void subject_leavesOutOnlyWhatCannotBeScoped() {
        final ToAqlResponse response = new ToAqlResponse()
                .addAql(ENTRY, ToAqlResponse.AqlType.ENTRY, "a template")
                .addAql("SELECT c FROM EHR e[ehr_id/value='{{ehrid}}'] CONTAINS COMPOSITION c",
                        ToAqlResponse.AqlType.COMPOSITION, "a template");

        AqlPatientScope.apply(subject(ID, null), response);

        Assert.assertEquals(1, response.getAqls().size());
        Assert.assertEquals(ToAqlResponse.AqlType.ENTRY, response.getAqls().get(0).getType());
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject(ID, null),
                            FederationGatewaySubjectPredicateRule.accept(response.getAqls().get(0).getAql()));
        Assert.assertEquals(1, response.getUnhandledParams().size());
        Assert.assertEquals(AqlPatientScope.PATIENT_SCOPE_PARAM, response.getUnhandledParams().get(0).getParamName());
    }

    /** A parenthesised AND is still at the top of the WHERE. */
    @Test
    public void subject_scopesAConditionInsideANestedAnd() {
        final ToAqlResponse response = scope(subject(ID, null),
                "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] WHERE h/data[at0001]/items[at0002]/value/value = 'x' AND (e/ehr_id/value='{{ehrid}}' AND h/data[at0001]/items[at0003]/value/value = 'y')");

        Assert.assertTrue(aql(response), aql(response).contains("(e/ehr_status/subject/external_ref/id/value = '194303193454' AND"));
        Assert.assertNull(response.getUnhandledParams());
    }

    /** A _query may write the placeholder in double quotes; an ehrId with a double quote stays in that literal. */
    @Test
    public void ehrId_staysInADoubleQuotedLiteral() {
        final String ehrId = "x\" OR e/ehr_id/value LIKE \"*";
        final ToAqlResponse response = scope(new ToAqlRequest(null, ehrId, "Composition"),
                "SELECT c FROM EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value = \"{{ehrid}}\"");

        Assert.assertEquals("SELECT c FROM EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value = \"x\\\" OR e/ehr_id/value LIKE \\\"*\"",
                            aql(response));
        final ComparisonOperatorCondition where = (ComparisonOperatorCondition) AqlQueryParser.parse(aql(response))
                .getWhere();
        Assert.assertEquals(ehrId, ((StringPrimitive) where.getValue()).getValue());
    }

    /** A double-quoted ehr_id condition in a _query is scoped by subject like a single-quoted one. */
    @Test
    public void subject_scopesAQueryWithADoubleQuotedPlaceholder() {
        final ToAqlResponse response = scope(subject(ID, null),
                "SELECT c FROM EHR e CONTAINS COMPOSITION c WHERE e/ehr_id/value = \"{{ehrid}}\"");

        Assert.assertEquals("SELECT c FROM EHR e CONTAINS COMPOSITION c WHERE e/ehr_status/subject/external_ref/id/value = '194303193454'",
                            aql(response));
        Assert.assertNull(response.getUnhandledParams());
    }

    /** The request is logged; the person it names is not. */
    @Test
    public void subject_isNotInTheRequestsToString() {
        final String logged = new ToAqlRequest("t", null, "Condition", new ToAqlRequest.Subject(ID, NAMESPACE)).toString();

        Assert.assertFalse(logged, logged.contains(ID));
        Assert.assertFalse(logged, logged.contains(NAMESPACE));
        Assert.assertTrue(logged, logged.contains("Condition"));
    }

    private static void assertLeftOut(final ToAqlRequest request, final String aql) {
        final ToAqlResponse response = scope(request, aql);

        Assert.assertTrue(aql, response.getAqls().isEmpty());
        Assert.assertEquals(aql, 1, response.getUnhandledParams().size());
        final ToAqlResponse.UnhandledParam error = response.getUnhandledParams().get(0);
        Assert.assertEquals("_patientScope", error.getParamName());
        Assert.assertEquals(ToAqlResponse.UnhandledParamType.ERROR, error.getType());
        Assert.assertTrue(error.getMessage(), error.getMessage().contains("left out"));
    }

    /**
     * Every form of AQL the engine generates (archetype only or with conditions, ENTRY or COMPOSITION, narrowed to
     * the template or not), for every entry type, is scoped by subject in the form openEHR Federation Gatewayaccepts.
     */
    @Test
    public void subject_everyGeneratedForm_ofEveryEntryType_isAcceptedByUnio() {
        final ToAqlMappingEngine engine = new ToAqlMappingEngine(new OpenEhrAqlPopulator());
        for (final String entryType : List.of("OBSERVATION", "EVALUATION", "INSTRUCTION", "ACTION", "ADMIN_ENTRY")) {
            for (final boolean narrowToTemplate : List.of(false, true)) {
                for (final String namespace : new String[]{null, NAMESPACE}) {
                    final String archetype = "openEHR-EHR-" + entryType + ".sample.v1";
                    final ToAqlResponse archetypeOnly = engine.archetypeOnlyAql(List.of(model(archetype)),
                                                                                narrowToTemplate);
                    final List<FhirQueryParam> params = new ArrayList<>();
                    params.add(new FhirQueryParam("code", "29463-7", null));
                    params.add(new FhirQueryParam("status", "final", null));
                    final ToAqlResponse withConditions = engine.map(List.of(model(archetype)), "Observation", params,
                                                                    narrowToTemplate);
                    Assert.assertEquals(entryType, 2, archetypeOnly.getAqls().size());
                    Assert.assertFalse(entryType, withConditions.getAqls().isEmpty());

                    final ToAqlRequest request = subject(ID, namespace);
                    AqlPatientScope.apply(request, archetypeOnly);
                    AqlPatientScope.apply(request, withConditions);

                    FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(archetypeOnly, ID, namespace);
                    FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(withConditions, ID, namespace);
                    Assert.assertNull(archetypeOnly.getUnhandledParams());
                }
            }
        }
    }

    private static ToAqlRequest subject(final String id, final String namespace) {
        return new ToAqlRequest(null, null, "Condition", new ToAqlRequest.Subject(id, namespace));
    }

    private static ToAqlResponse scope(final ToAqlRequest request, final String aql) {
        AqlPatientScope.validate(request);
        final ToAqlResponse response = new ToAqlResponse().addAql(aql, ToAqlResponse.AqlType.ENTRY, "a template");
        AqlPatientScope.apply(request, response);
        return response;
    }

    private static String aql(final ToAqlResponse response) {
        Assert.assertEquals(1, response.getAqls().size());
        return response.getAqls().get(0).getAql();
    }

    private static ToAql.ToAqlModels model(final String archetype) {
        final MappingHelper code = new MappingHelper();
        code.setFullFhirPath("Observation.code");
        code.setFhir("code");
        code.setPossibleRmTypes(List.of("DV_CODED_TEXT"));
        code.setArchetype(archetype);
        code.setFullOpenEhrPath("$composition/content[" + archetype + "]/data[at0001]/items[at0004]");

        final MappingHelper status = new MappingHelper();
        status.setFullFhirPath("Observation.status");
        status.setFhir("status");
        status.setPossibleRmTypes(List.of("DV_CODED_TEXT"));
        status.setArchetype(archetype);
        status.setFullOpenEhrPath("$composition/content[" + archetype + "]/data[at0001]/items[at0005]");

        final Context context = new Context();
        final ContextTemplate template = new ContextTemplate();
        template.setId("Sample");
        context.setTemplate(template);
        context.setStart("openEHR-EHR-COMPOSITION.sample.v1");
        final FhirConnectContext fhirConnectContext = new FhirConnectContext();
        fhirConnectContext.setContext(context);
        final FhirConnectContextEntity entity = new FhirConnectContextEntity();
        entity.setFhirConnectContext(fhirConnectContext);

        return ToAql.ToAqlModels.builder()
                .context(entity)
                .mappingHelpers(List.of(code, status))
                .mainArchetype(archetype)
                .build();
    }
}
