package com.syntaric.openfhir.mapping.toaql;

import com.syntaric.openfhir.aql.ToAqlRequest;
import com.syntaric.openfhir.aql.ToAqlResponse;
import com.syntaric.openfhir.rest.RequestValidationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.aql.dto.AqlQuery;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.ComparisonOperatorSymbol;
import org.ehrbase.openehr.sdk.aql.dto.condition.LogicalOperatorCondition;
import org.ehrbase.openehr.sdk.aql.dto.condition.WhereCondition;
import org.ehrbase.openehr.sdk.aql.dto.containment.AbstractContainmentExpression;
import org.ehrbase.openehr.sdk.aql.dto.containment.Containment;
import org.ehrbase.openehr.sdk.aql.dto.containment.ContainmentClassExpression;
import org.ehrbase.openehr.sdk.aql.dto.containment.ContainmentSetOperator;
import org.ehrbase.openehr.sdk.aql.dto.operand.IdentifiedPath;
import org.ehrbase.openehr.sdk.aql.dto.operand.StringPrimitive;
import org.ehrbase.openehr.sdk.aql.parser.AqlQueryParser;

/**
 * Scopes the AQLs of a {@code /toaql} response to the patient the request names. Every AQL the engine generates
 * scopes the patient with the WHERE condition {@code e/ehr_id/value='{{ehrid}}'} (see {@link ToAqlMappingEngine}).
 * <ul>
 *     <li>with an {@code ehrId}, the placeholder is filled with it, {@link AqlLiterals#escape escaped}, wherever it
 *     is: a backslash goes before a backslash and either quote, so the ehrId stays in a single- or double-quoted
 *     literal;</li>
 *     <li>with a {@code subject}, that condition is replaced by
 *     {@code e/ehr_status/subject/external_ref/id/value = '<id>'}, followed by
 *     {@code AND e/ehr_status/subject/external_ref/namespace = '<namespace>'} when a namespace is given. That is the
 *     form a federation gateway (Unio's {@code SubjectPredicateExtractor}) resolves the patient from. The result is
 *     parsed and kept only if the subject conditions are proven conjuncts at the top of its WHERE. Every other AQL is
 *     <em>removed</em> from the response and reported as an {@code ERROR} for {@link #PATIENT_SCOPE_PARAM} in
 *     {@code unhandledParams}, so a caller that runs every AQL it gets never runs one not scoped to the patient;</li>
 *     <li>with neither, the placeholder is left for the caller.</li>
 * </ul>
 */
@Slf4j
public final class AqlPatientScope {

    public static final String EHR_ID_PLACEHOLDER = "{{ehrid}}";
    public static final String SUBJECT_ID_PATH = "ehr_status/subject/external_ref/id/value";
    public static final String SUBJECT_NAMESPACE_PATH = "ehr_status/subject/external_ref/namespace";

    /**
     * Name under which AQLs left out because they could not be scoped by subject are reported in
     * {@code unhandledParams}. It is no FHIR search parameter, so it can't be confused with one (an unmapped FHIR
     * {@code subject} parameter is reported under {@code subject}).
     */
    public static final String PATIENT_SCOPE_PARAM = "_patientScope";

    /**
     * The ehr_id scope as a WHERE condition on the EHR's alias ({@code e/ehr_id/value='{{ehrid}}'}), as generated or
     * as written in a {@code _query}, in single or double quotes. The FROM form {@code EHR e[ehr_id/value='{{ehrid}}']}
     * has no alias before {@code ehr_id} and does not match.
     */
    private static final Pattern EHR_ID_CONDITION = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)/ehr_id/value\\s*=\\s*(['\"])" + Pattern.quote(EHR_ID_PLACEHOLDER) + "\\2");

    private AqlPatientScope() {
    }

    /**
     * @throws RequestValidationException when the request names the patient both by {@code ehrId} and by
     *                                    {@code subject}, or gives a {@code subject} without an {@code id}
     */
    public static void validate(final ToAqlRequest request) {
        final ToAqlRequest.Subject subject = request.getSubject();
        if (subject == null) {
            return;
        }
        if (StringUtils.isNotEmpty(request.getEhrId())) {
            throw new RequestValidationException(
                    "Name the patient either by 'ehrId' or by 'subject', not both: they scope the AQL differently, "
                            + "and a federation gateway resolves the patient from 'subject' only.", null);
        }
        if (StringUtils.isBlank(subject.getId())) {
            throw new RequestValidationException("'subject.id' is required when 'subject' is given.", null);
        }
    }

    /**
     * Scopes every AQL of {@code response} to the patient {@code request} names, see the class comment. Call
     * {@link #validate(ToAqlRequest)} first.
     */
    public static void apply(final ToAqlRequest request, final ToAqlResponse response) {
        if (response.getAqls() == null) {
            return;
        }
        if (StringUtils.isNotEmpty(request.getEhrId())) {
            // the placeholder is inside a string literal; the ehrId comes from the request, so it is escaped
            final String ehrId = AqlLiterals.escape(request.getEhrId());
            response.getAqls().forEach(aql -> aql.setAql(aql.getAql().replace(EHR_ID_PLACEHOLDER, ehrId)));
        } else if (request.getSubject() != null) {
            scopeBySubject(request.getSubject(), response);
        }
    }

    private static void scopeBySubject(final ToAqlRequest.Subject subject, final ToAqlResponse response) {
        final List<ToAqlResponse.AqlResponse> scoped = new ArrayList<>();
        int dropped = 0;
        for (final ToAqlResponse.AqlResponse aql : response.getAqls()) {
            final String bySubject = bySubject(aql.getAql(), subject);
            if (bySubject == null) {
                // the AQL as it came: the {{ehrid}} placeholder and no patient data
                log.warn("Left out an AQL that can't be scoped by subject: {}", aql.getAql());
                dropped++;
                continue;
            }
            aql.setAql(bySubject);
            scoped.add(aql);
        }
        response.setAqls(scoped);
        if (dropped > 0) {
            response.addUnhandledParam(PATIENT_SCOPE_PARAM, ToAqlResponse.UnhandledParamType.ERROR, String.format(
                    "%d AQL(s) could not be scoped by subject and were left out of the response. Only an AQL that "
                            + "scopes the patient with exactly one WHERE condition <alias>/ehr_id/value='%s', "
                            + "ANDed at the top of its WHERE, can be; a context's _query with "
                            + "EHR e[ehr_id/value='%s'] in its FROM, or with no patient scope, can't.",
                    dropped, EHR_ID_PLACEHOLDER, EHR_ID_PLACEHOLDER));
        }
    }

    /**
     * {@code aql} with its ehr_id condition replaced by the subject conditions, or null when the result is not proven
     * scoped to the subject: the placeholder must occur exactly once, in that condition, and the result must parse
     * with the subject conditions among the conjuncts at the top of its WHERE, on the EHR the query contains.
     */
    static String bySubject(final String aql, final ToAqlRequest.Subject subject) {
        if (StringUtils.countMatches(aql, EHR_ID_PLACEHOLDER) != 1) {
            return null;
        }
        final Matcher matcher = EHR_ID_CONDITION.matcher(aql);
        if (!matcher.find()) {
            return null;
        }
        final String scoped = aql.substring(0, matcher.start())
                + subjectCondition(matcher.group(1), subject)
                + aql.substring(matcher.end());
        return isScopedBy(scoped, subject) ? scoped : null;
    }

    /**
     * Whether {@code aql} parses, and its WHERE has {@code <ehr>/ehr_status/subject/external_ref/id/value = '<id>'}
     * (and the namespace equality, when a namespace is given) among the conditions reached from its root through AND
     * only, on the EHR the query contains.
     */
    static boolean isScopedBy(final String aql, final ToAqlRequest.Subject subject) {
        final AqlQuery query;
        try {
            query = AqlQueryParser.parse(aql);
        } catch (final Exception e) {
            // not the parser's message: it can quote the subject's identifier
            log.warn("An AQL scoped by subject doesn't parse");
            return false;
        }
        final ContainmentClassExpression ehr = findEhr(query.getFrom());
        if (ehr == null) {
            return false;
        }
        final List<WhereCondition> conjuncts = new ArrayList<>();
        collectConjuncts(query.getWhere(), conjuncts);
        final boolean idScoped = conjuncts.stream()
                .anyMatch(c -> isEquality(c, ehr, SUBJECT_ID_PATH, subject.getId()));
        final boolean namespaceScoped = StringUtils.isBlank(subject.getNamespace()) || conjuncts.stream()
                .anyMatch(c -> isEquality(c, ehr, SUBJECT_NAMESPACE_PATH, subject.getNamespace()));
        return idScoped && namespaceScoped;
    }

    /** The conditions reached from the root of a WHERE through AND only; nothing below an OR or a NOT. */
    private static void collectConjuncts(final WhereCondition condition, final List<WhereCondition> conjuncts) {
        if (condition instanceof LogicalOperatorCondition logical) {
            if (logical.getSymbol() == LogicalOperatorCondition.ConditionLogicalOperatorSymbol.AND) {
                logical.getValues().forEach(child -> collectConjuncts(child, conjuncts));
            }
        } else if (condition != null) {
            conjuncts.add(condition);
        }
    }

    private static boolean isEquality(final WhereCondition condition, final ContainmentClassExpression ehr,
                                      final String path, final String value) {
        return condition instanceof ComparisonOperatorCondition comparison
                && comparison.getSymbol() == ComparisonOperatorSymbol.EQ
                && comparison.getStatement() instanceof IdentifiedPath identified
                && identified.getRoot() == ehr
                && identified.getPath() != null
                && path.equals(identified.getPath().render())
                && comparison.getValue() instanceof StringPrimitive literal
                && value.equals(literal.getValue());
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
            return set.getValues().stream().map(AqlPatientScope::findEhr)
                    .filter(Objects::nonNull).findFirst().orElse(null);
        }
        return null;
    }

    /**
     * {@code <alias>/ehr_status/subject/external_ref/id/value = '<id>'}, and
     * {@code AND <alias>/ehr_status/subject/external_ref/namespace = '<namespace>'} when a namespace is given.
     */
    static String subjectCondition(final String ehrAlias, final ToAqlRequest.Subject subject) {
        final StringBuilder condition = new StringBuilder()
                .append(ehrAlias).append('/').append(SUBJECT_ID_PATH).append(" = ").append(literal(subject.getId()));
        if (StringUtils.isNotBlank(subject.getNamespace())) {
            condition.append(" AND ")
                    .append(ehrAlias).append('/').append(SUBJECT_NAMESPACE_PATH).append(" = ")
                    .append(literal(subject.getNamespace()));
        }
        return condition.toString();
    }

    /** An AQL string literal, see {@link AqlLiterals#string}. */
    static String literal(final String value) {
        return AqlLiterals.string(value);
    }
}
