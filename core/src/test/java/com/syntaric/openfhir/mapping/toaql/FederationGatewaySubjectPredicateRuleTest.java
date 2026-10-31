package com.syntaric.openfhir.mapping.toaql;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link FederationGatewaySubjectPredicateRule} rejects what Unio's SubjectPredicateExtractor rejects, so that an AQL it accepts
 * means something.
 */
public class FederationGatewaySubjectPredicateRuleTest {

    private static final String FROM = "SELECT h FROM EHR e CONTAINS EVALUATION h [openEHR-EHR-EVALUATION.problem_diagnosis.v1] ";
    private static final String SUBJECT = "e/ehr_status/subject/external_ref/id/value = '194303193454'";
    private static final String NAMESPACE = "e/ehr_status/subject/external_ref/namespace = '1.2.752.129.2.1.3.1'";

    @Test
    public void acceptsTheSubjectAndItsNamespaceAndedAtTheTop() {
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject("194303193454", "1.2.752.129.2.1.3.1"),
                            FederationGatewaySubjectPredicateRule.accept(FROM + "WHERE " + SUBJECT + " AND " + NAMESPACE
                                                                    + " AND h/data[at0001]/items[at0002]/value/value = 'x'"));
        Assert.assertEquals(new FederationGatewaySubjectPredicateRule.Subject("194303193454", null),
                            FederationGatewaySubjectPredicateRule.accept(FROM + "WHERE " + SUBJECT));
    }

    @Test
    public void rejectsTheSubjectUnderOrNotOrLike() {
        assertRejected(FROM + "WHERE " + SUBJECT + " OR h/data[at0001]/items[at0002]/value/value = 'x'");
        assertRejected(FROM + "WHERE NOT " + SUBJECT);
        assertRejected(FROM + "WHERE e/ehr_status/subject/external_ref/id/value LIKE '1943*'");
        assertRejected(FROM + "WHERE " + SUBJECT + " AND (" + NAMESPACE + " OR h/data[at0001]/items[at0002]/value/value = 'x')");
    }

    @Test
    public void rejectsNoSubjectTwoSubjectsAndTheEhrIdScope() {
        assertRejected(FROM + "WHERE e/ehr_id/value = '7d44b88c-4199-4bad-97dc-d78268e01398'");
        assertRejected(FROM + "WHERE " + SUBJECT + " AND e/ehr_status/subject/external_ref/id/value = '1'");
    }

    @Test
    public void rejectsTheIdentifierElsewhere() {
        assertRejected(FROM + "WHERE " + SUBJECT + " AND h/data[at0001]/items[at0002]/value/value = '194303193454'");
        assertRejected(FROM + "WHERE " + SUBJECT + " AND h/subject/external_ref/id/value = 'x'");
    }

    private static void assertRejected(final String aql) {
        try {
            FederationGatewaySubjectPredicateRule.accept(aql);
        } catch (final AssertionError expected) {
            return;
        }
        Assert.fail("accepted: " + aql);
    }
}
