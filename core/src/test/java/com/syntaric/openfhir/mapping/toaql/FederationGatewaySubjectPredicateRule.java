package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.ToAqlResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.ehrbase.openehr.sdk.aql.dto.AqlQuery;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorSymbol;
import org.ehrbase.openehr.sdk.aql.dto.condition.LikeCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.LogicalOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.MatchesCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.NotCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.WhereCondition;
import org.ehrbase.openehr.sdk.aql.dto.containment.AbstractContainmentExpression;
import org.ehrbase.openehr.sdk.aql.dto.containment.Containment;
import org.ehrbase.openehr.sdk.aql.dto.containment.ContainmentClassExpression;
import org.ehrbase.openehr.sdk.aql.dto.containment.ContainmentSetOperator;
import org.ehrbase.openehr.sdk.aql.dto.operand.IdentifiedPath;
import org.ehrbase.openehr.sdk.aql.dto.operand.StringPrimitive;
import org.ehrbase.openehr.sdk.aql.dto.orderby.OrderByExpression;
import org.ehrbase.openehr.sdk.aql.dto.path.AqlObjectPath;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;
import org.junit.Assert;

/**
 * Whether a federation gateway can resolve the patient of an AQL from its {@code EHR_STATUS.subject}: the rule of
 * Unio's {@code io.syntaric.unio.federation.aql.analysis.SubjectPredicateExtractor} (and the string-level
 * {@code IdentifierHygieneGuard} after it), openEHR Federation Gatewayrelease 1.0.0, replicated here for the cases openFHIR generates.
 * <p>
 * Accepted is an AQL that
 * <ul>
 *     <li>contains an {@code EHR} (FROM EHR e ...);</li>
 *     <li>names the patient as {@code e/ehr_status/subject/external_ref/id/value = '<id>'}, an equality with a string,
 *     reached from the WHERE root through AND only (never under OR or NOT, never as LIKE or MATCHES), with one value;</li>
 *     <li>optionally names its namespace the same way, as {@code e/ehr_status/subject/external_ref/namespace = '<ns>'};</li>
 *     <li>names the patient through no other path (an ENTRY-level {@code subject}, a non-canonical
 *     {@code external_ref/id}, an ORDER BY over it) and has the identifier nowhere else in its text.</li>
 * </ul>
 * openEHR Federation Gatewayalso accepts an AQL with no subject at all and runs it on every node unscoped; for openFHIR's subject scope
 * that is a failure, so here a subject is required.
 */
public final class FederationGatewaySubjectPredicateRule {

    public static final String SUBJECT_PATH = "ehr_status/subject/external_ref/id/value";
    public static final String SUBJECT_NAMESPACE_PATH = "ehr_status/subject/external_ref/namespace";

    /** The patient an accepted AQL names: the identifier, and its namespace or null. */
    public record Subject(String id, String namespace) {
    }

    private FederationGatewaySubjectPredicateRule() {
    }

    /**
     * Asserts that every AQL of {@code response} is scoped by the subject {@code id} (and {@code namespace}, when not
     * null) in a form openEHR Federation Gatewayaccepts, and by no {@code ehr_id}.
     */
    public static void assertAllScopedBySubject(final ToAqlResponse response, final String id,
                                                final String namespace) {
        Assert.assertNotNull("no AQLs in the response", response.getAqls());
        Assert.assertFalse("no AQLs in the response", response.getAqls().isEmpty());
        for (final ToAqlResponse.AqlResponse aql : response.getAqls()) {
            Assert.assertFalse(aql.getAql(), aql.getAql().contains("{{ehrid}}"));
            assertNotScopedByEhrId(aql.getAql());
            Assert.assertEquals(aql.getAql(), new Subject(id, namespace), accept(aql.getAql()));
        }
    }

    /**
     * No {@code ehr_id} on the EHR, neither as a FROM predicate ({@code EHR e[ehr_id/value=...]}) nor as a WHERE path.
     * Parsed rather than searched for in the text, since a string literal (a search value) may contain the words.
     */
    private static void assertNotScopedByEhrId(final String aql) {
        final AqlQuery query = AqlQueryParser.parse(aql);
        final ContainmentClassExpression ehr = findEhr(query.getFrom());
        Assert.assertNotNull(aql, ehr);
        Assert.assertFalse(aql, ehr.hasPredicates());
        walk(query.getWhere(), true, (leaf, reducible) -> {
            final IdentifiedPath path = statementOf(leaf);
            Assert.assertFalse(aql, path != null && path.getRoot() == ehr && path.getPath() != null
                    && path.getPath().render().startsWith("ehr_id"));
        });
    }

    /**
     * @return the subject the AQL names
     * @throws AssertionError naming the reason openEHR Federation Gatewaywould reject the AQL
     */
    public static Subject accept(final String aql) {
        final AqlQuery query = AqlQueryParser.parse(aql);
        final ContainmentClassExpression ehr = findEhr(query.getFrom());
        if (ehr == null) {
            throw reject(aql, "the query is not scoped on an EHR (FROM EHR e ...)");
        }

        final List<ComparisonOperatorCondition> consumed = new ArrayList<>();
        final Set<String> ids = new LinkedHashSet<>();
        final Set<String> namespaces = new LinkedHashSet<>();
        walk(query.getWhere(), true, (leaf, reducible) -> {
            final IdentifiedPath path = statementOf(leaf);
            if (path == null || path.getPath() == null) {
                return;
            }
            final String rendered = path.getPath().render();
            if (path.getRoot() == ehr && (SUBJECT_PATH.equals(rendered) || SUBJECT_NAMESPACE_PATH.equals(rendered))) {
                if (!reducible || !(leaf instanceof ComparisonOperatorCondition comparison)
                        || comparison.getSymbol() != ComparisonOperatorSymbol.EQ
                        || !(comparison.getValue() instanceof StringPrimitive literal)) {
                    throw reject(aql, "'" + rendered + "' is not a string equality ANDed at the top level of WHERE");
                }
                consumed.add(comparison);
                (SUBJECT_PATH.equals(rendered) ? ids : namespaces).add(literal.getValue());
            } else if (path.getRoot() == ehr && rendered.contains("external_ref/id")) {
                throw reject(aql, "'" + rendered + "' is a non-canonical form of the subject");
            } else if (path.getRoot() != ehr && attributes(path.getPath()).contains("subject")) {
                throw reject(aql, "'" + rendered + "' names the patient through an ENTRY-level subject");
            }
        });
        if (ids.size() != 1) {
            throw reject(aql, ids.isEmpty() ? "no subject predicate" : "more than one subject identifier " + ids);
        }
        if (namespaces.size() > 1) {
            throw reject(aql, "more than one subject namespace " + namespaces);
        }
        if (!namespaces.isEmpty() && consumed.size() < 2) {
            throw reject(aql, "a namespace without a subject identifier");
        }
        for (final OrderByExpression order : query.getOrderBy() == null ? List.<OrderByExpression>of()
                : query.getOrderBy()) {
            if (order.getStatement() != null && order.getStatement().getRoot() == ehr
                    && order.getStatement().getPath().render().contains("external_ref")) {
                throw reject(aql, "ORDER BY over the subject");
            }
        }

        final String id = ids.iterator().next();
        assertIdentifierNowhereElse(aql, query, consumed, id);
        return new Subject(id, namespaces.isEmpty() ? null : namespaces.iterator().next());
    }

    /** Unio's IdentifierHygieneGuard: what is dispatched, the AQL without the consumed predicates, never has the id. */
    private static void assertIdentifierNowhereElse(final String aql, final AqlQuery query,
                                                    final List<ComparisonOperatorCondition> consumed,
                                                    final String id) {
        final Set<WhereCondition> strip = Collections.newSetFromMap(new IdentityHashMap<>());
        strip.addAll(consumed);
        query.setWhere(strip(query.getWhere(), strip));
        final String dispatched = query.render().toLowerCase(Locale.ROOT);
        final String needle = id.toLowerCase(Locale.ROOT);
        final String encoded = URLEncoder.encode(id, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        if (dispatched.contains(needle) || dispatched.contains(encoded)) {
            throw reject(aql, "the subject identifier occurs outside the subject predicate");
        }
    }

    private static WhereCondition strip(final WhereCondition condition, final Set<WhereCondition> strip) {
        if (condition == null || strip.contains(condition)) {
            return null;
        }
        if (condition instanceof LogicalOperatorCondition logical) {
            final List<WhereCondition> kept = new ArrayList<>();
            for (final WhereCondition child : logical.getValues()) {
                final WhereCondition stripped = strip(child, strip);
                if (stripped != null) {
                    kept.add(stripped);
                }
            }
            if (kept.isEmpty()) {
                return null;
            }
            if (kept.size() == 1) {
                return kept.get(0);
            }
            logical.setValues(kept);
        }
        return condition;
    }

    @FunctionalInterface
    private interface LeafVisitor {
        /** @param reducible whether the leaf is reached from the WHERE root through AND only */
        void visit(WhereCondition leaf, boolean reducible);
    }

    private static void walk(final WhereCondition condition, final boolean reducible, final LeafVisitor visitor) {
        if (condition == null) {
            return;
        }
        if (condition instanceof LogicalOperatorCondition logical) {
            final boolean and = logical.getSymbol() == LogicalOperatorCondition.ConditionLogicalOperatorSymbol.AND;
            logical.getValues().forEach(child -> walk(child, reducible && and, visitor));
        } else if (condition instanceof NotCondition not) {
            walk(not.getConditionDto(), false, visitor);
        } else {
            visitor.visit(condition, reducible);
        }
    }

    private static IdentifiedPath statementOf(final WhereCondition leaf) {
        if (leaf instanceof ComparisonOperatorCondition comparison
                && comparison.getStatement() instanceof IdentifiedPath path) {
            return path;
        }
        if (leaf instanceof LikeCondition like) {
            return like.getStatement();
        }
        if (leaf instanceof MatchesCondition matches) {
            return matches.getStatement();
        }
        return null;
    }

    private static ContainmentClassExpression findEhr(final Containment containment) {
        if (containment instanceof ContainmentClassExpression classExpression
                && "EHR".equalsIgnoreCase(classExpression.getType())) {
            return classExpression;
        }
        if (containment instanceof AbstractContainmentExpression expression) {
            return findEhr(expression.getContains());
        }
        if (containment instanceof ContainmentSetOperator set) {
            for (final Containment child : set.getValues()) {
                final ContainmentClassExpression found = findEhr(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static List<String> attributes(final AqlObjectPath path) {
        return path.getPathNodes().stream().map(AqlObjectPath.PathNode::getAttribute).toList();
    }

    private static AssertionError reject(final String aql, final String reason) {
        return new AssertionError("Unio's SubjectPredicateExtractor would reject this AQL: " + reason + ": " + aql);
    }
}
