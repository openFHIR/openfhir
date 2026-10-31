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
import org.ehrbase.openehr.sdk.aql.dto.AqlQuery;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorSymbol;
import org.ehrbase.openehr.sdk.aql.dto.condition.LogicalOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.WhereCondition;
import org.ehrbase.openehr.sdk.aql.dto.operand.IdentifiedPath;
import org.ehrbase.openehr.sdk.aql.dto.operand.StringPrimitive;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;
import org.junit.Assert;
import org.junit.Test;

/**
 * A search value is compared in an AQL WHERE, and it comes from the request. Text is a string literal in which a
 * quote or backslash is escaped, so a value cannot leave the literal and widen the query beyond its patient; a value
 * compared to a number or a boolean must be one.
 */
public class ToAqlSearchValueEscapingTest {

    private static final String ARCHETYPE = "openEHR-EHR-OBSERVATION.sample.v1";
    private static final String ELEMENT = "$composition/content[" + ARCHETYPE + "]/data[at0001]/items[at0004]";
    private static final String EHR_ID = "7d44b88c-4199-4bad-97dc-d78268e01398";
    private static final String SUBJECT_ID = "194303193454";
    private static final String SUBJECT_NAMESPACE = "1.2.752.129.2.1.3.1";

    /** Leaves the literal if only the quote is escaped, or if nothing is. */
    private static final List<String> INJECTIONS = List.of(
            "x' OR e/ehr_id/value LIKE '*",
            "x\\' OR e/ehr_id/value LIKE \\'*",
            "x\\",
            "x' OR '1'='1");

    private final ToAqlMappingEngine engine = new ToAqlMappingEngine(new OpenEhrAqlPopulator());

    @Test
    public void aCodedTextCode_staysInItsLiteral() {
        for (final String value : INJECTIONS) {
            assertInOneLiteral("DV_CODED_TEXT", value, "value/defining_code/code_string");
        }
        Assert.assertEquals("SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.sample.v1] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0001]/items[at0004]/value/defining_code/code_string = 'x\\' OR e/ehr_id/value LIKE \\'*'",
                            entry(map("DV_CODED_TEXT", INJECTIONS.get(0))));
    }

    @Test
    public void aText_staysInItsLiteral() {
        INJECTIONS.forEach(value -> assertInOneLiteral("DV_TEXT", value, "value/value"));
    }

    @Test
    public void anIdentifier_staysInItsLiteral() {
        INJECTIONS.forEach(value -> assertInOneLiteral("DV_IDENTIFIER", value, "value/id"));
    }

    @Test
    public void aDate_staysInItsLiteral() {
        for (final String value : List.of("2024-01-02' OR e/ehr_id/value LIKE '*", "2024-01-02\\")) {
            assertInOneLiteral("DV_DATE_TIME", value, "value/value");
        }
    }

    @Test
    public void aQuoteInALegitimateValue_isMatchedLiterally() {
        final ToAqlResponse response = map("DV_TEXT", "O'Brien");

        Assert.assertEquals("SELECT h FROM EHR e CONTAINS OBSERVATION h [openEHR-EHR-OBSERVATION.sample.v1] WHERE e/ehr_id/value='{{ehrid}}' AND h/data[at0001]/items[at0004]/value/value = 'O\\'Brien'",
                            entry(response));
        assertInOneLiteral("DV_TEXT", "O'Brien", "value/value");
    }

    @Test
    public void aValueComparedToANumber_mustBeANumber() {
        for (final String value : List.of("500 OR e/ehr_id/value LIKE '*'", "500' OR '1'='1", "gt500", "500|kg", "")) {
            final RequestValidationException e = Assert.assertThrows(value, RequestValidationException.class,
                                                                     () -> map("DV_QUANTITY", value));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("'code'") && e.getMessage().contains("number"));
            Assert.assertFalse("the value is not echoed: " + e.getMessage(), !value.isEmpty() && e.getMessage().contains(value));
        }
    }

    /** What valid numbers and booleans produce does not change. */
    @Test
    public void aNumberOrABoolean_isComparedAsBefore() {
        for (final String value : List.of("500", "-1.5", "1e3", "1.5E-3", ".5", "007")) {
            Assert.assertTrue(value, entry(map("DV_QUANTITY", value))
                    .endsWith("h/data[at0001]/items[at0004]/value/magnitude = " + value));
        }
        for (final String value : List.of("true", "false", "TRUE")) {
            Assert.assertTrue(value, entry(map("DV_BOOLEAN", value))
                    .endsWith("h/data[at0001]/items[at0004]/value/value = " + value));
        }
    }

    /**
     * What the openEHR SDK does not read as a number (an exponent with a plus, past the range of a long or a double)
     * is not one either: it used to make the AQL invalid, which was then replaced by the unfiltered one.
     */
    @Test
    public void aNumberTheAqlParserCannotRead_isNotANumber() {
        for (final String value : List.of("1e+3", "-1e+3", ".5e+2", "9223372036854775808", "-9223372036854775809",
                                          "1e309", "1e999", "-1e999", "500.", "+5", "1_000", "0x10", "NaN", "5 ")) {
            final RequestValidationException e = Assert.assertThrows(value, RequestValidationException.class,
                                                                     () -> map("DV_QUANTITY", value));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("'code'"));
        }
    }

    @Test
    public void aNumberAtTheBoundaryOfWhatTheParserReads_isComparedAsIs() {
        for (final String value : List.of("9223372036854775807", "-9223372036854775808", "1e308", "-1e308", ".5e2",
                                          "1e-3", "1e-400", "-0")) {
            Assert.assertTrue(value, entry(map("DV_QUANTITY", value))
                    .endsWith("h/data[at0001]/items[at0004]/value/magnitude = " + value));
        }
    }

    /** A double quote stays in the literal too, should a value end up in a double-quoted one. */
    @Test
    public void aDoubleQuote_staysInItsLiteral() {
        assertInOneLiteral("DV_TEXT", "x\" OR e/ehr_id/value LIKE \"*", "value/value");
        Assert.assertTrue(entry(map("DV_TEXT", "say \"hi\"")).endsWith("/value/value = 'say \\\"hi\\\"'"));
    }

    @Test
    public void aValueComparedToABoolean_mustBeABoolean() {
        for (final String value : List.of("true OR e/ehr_id/value LIKE '*'", "yes", "1")) {
            final RequestValidationException e = Assert.assertThrows(value, RequestValidationException.class,
                                                                     () -> map("DV_BOOLEAN", value));
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("'code'") && e.getMessage().contains("true or false"));
        }
    }

    /** The ehrId is filled into the placeholder's literal escaped, like a search value. */
    @Test
    public void theEhrId_staysInItsLiteral() {
        for (final String ehrId : INJECTIONS) {
            final ToAqlResponse response = map("DV_CODED_TEXT", "29463-7");
            AqlPatientScope.apply(new ToAqlRequest(null, ehrId, "Observation"), response);
            for (final ToAqlResponse.AqlResponse aql : response.getAqls()) {
                final List<WhereCondition> conjuncts = conjunctsOf(aql.getAql());
                Assert.assertEquals(aql.getAql(), 2, conjuncts.size());
                Assert.assertEquals(aql.getAql(), ehrId, literalOf(conjuncts, "ehr_id/value"));
                Assert.assertEquals(aql.getAql(), "29463-7", literalOf(conjuncts, "value/defining_code/code_string"));
            }
        }
    }

    /**
     * The value ends up in exactly one string literal of every AQL (ENTRY and COMPOSITION), the AQL parses, and the
     * patient scope stays a conjunct at the top of the WHERE, by ehrId and by subject.
     */
    private void assertInOneLiteral(final String rmType, final String value, final String attribute) {
        final ToAqlResponse byEhrId = map(rmType, value);
        Assert.assertEquals(value, 2, byEhrId.getAqls().size());
        AqlPatientScope.apply(new ToAqlRequest(null, EHR_ID, "Observation"), byEhrId);
        for (final ToAqlResponse.AqlResponse aql : byEhrId.getAqls()) {
            final List<WhereCondition> conjuncts = conjunctsOf(aql.getAql());
            Assert.assertEquals(aql.getAql(), 2, conjuncts.size());
            Assert.assertEquals(aql.getAql(), EHR_ID, literalOf(conjuncts, "ehr_id/value"));
            Assert.assertEquals(aql.getAql(), value, literalOf(conjuncts, attribute));
        }

        final ToAqlResponse bySubject = map(rmType, value);
        AqlPatientScope.apply(new ToAqlRequest(null, null, "Observation",
                                               new ToAqlRequest.Subject(SUBJECT_ID, SUBJECT_NAMESPACE)), bySubject);
        Assert.assertNull(value + ": " + bySubject.getUnhandledParams(), bySubject.getUnhandledParams());
        Assert.assertEquals(value, 2, bySubject.getAqls().size());
        FederationGatewaySubjectPredicateRule.assertAllScopedBySubject(bySubject, SUBJECT_ID, SUBJECT_NAMESPACE);
        for (final ToAqlResponse.AqlResponse aql : bySubject.getAqls()) {
            final List<WhereCondition> conjuncts = conjunctsOf(aql.getAql());
            Assert.assertEquals(aql.getAql(), 3, conjuncts.size());
            Assert.assertEquals(aql.getAql(), value, literalOf(conjuncts, attribute));
        }
    }

    /**
     * The WHERE of {@code aql}, which must parse, as its conjuncts: a WHERE that is not an AND of equalities (an OR,
     * a NOT, a LIKE anywhere) fails.
     */
    private static List<WhereCondition> conjunctsOf(final String aql) {
        final AqlQuery query = AqlQueryParser.parse(aql);
        final List<WhereCondition> conjuncts = new ArrayList<>();
        collect(aql, query.getWhere(), conjuncts);
        return conjuncts;
    }

    private static void collect(final String aql, final WhereCondition condition, final List<WhereCondition> sink) {
        if (condition instanceof LogicalOperatorCondition logical) {
            Assert.assertEquals(aql, LogicalOperatorCondition.ConditionLogicalOperatorSymbol.AND, logical.getSymbol());
            logical.getValues().forEach(child -> collect(aql, child, sink));
        } else {
            Assert.assertTrue(aql + ": " + condition, condition instanceof ComparisonOperatorCondition comparison
                    && comparison.getSymbol() == ComparisonOperatorSymbol.EQ);
            sink.add(condition);
        }
    }

    /** The string the one conjunct on a path ending in {@code attribute} compares to. */
    private static String literalOf(final List<WhereCondition> conjuncts, final String attribute) {
        final List<String> literals = conjuncts.stream()
                .map(ComparisonOperatorCondition.class::cast)
                .filter(c -> c.getStatement() instanceof IdentifiedPath path
                        && path.getPath().render().endsWith(attribute))
                .map(c -> ((StringPrimitive) c.getValue()).getValue())
                .toList();
        Assert.assertEquals(attribute + " in " + conjuncts, 1, literals.size());
        return literals.get(0);
    }

    private static String entry(final ToAqlResponse response) {
        return response.getAqls().stream()
                .filter(a -> a.getType() == ToAqlResponse.AqlType.ENTRY)
                .map(ToAqlResponse.AqlResponse::getAql)
                .findFirst().orElseThrow(() -> new AssertionError("no ENTRY AQL: " + response));
    }

    private ToAqlResponse map(final String rmType, final String value) {
        final MappingHelper helper = new MappingHelper();
        helper.setFullFhirPath("Observation.code");
        helper.setFhir("code");
        helper.setArchetype(ARCHETYPE);
        helper.setFullOpenEhrPath(ELEMENT);
        helper.setPossibleRmTypes(List.of(rmType));
        helper.setOpenEhrNodeRmType("ELEMENT");

        final Context context = new Context();
        final ContextTemplate template = new ContextTemplate();
        template.setId("Sample");
        context.setTemplate(template);
        context.setStart("openEHR-EHR-COMPOSITION.sample.v1");
        final FhirConnectContext fhirConnectContext = new FhirConnectContext();
        fhirConnectContext.setContext(context);
        final FhirConnectContextEntity entity = new FhirConnectContextEntity();
        entity.setFhirConnectContext(fhirConnectContext);

        final ToAql.ToAqlModels model = ToAql.ToAqlModels.builder()
                .context(entity)
                .mappingHelpers(List.of(helper))
                .mainArchetype(ARCHETYPE)
                .build();
        return engine.map(List.of(model), "Observation", List.of(new FhirQueryParam("code", value, null)), false);
    }
}
