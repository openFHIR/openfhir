package com.syntaric.openfhir.operations;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.Assert;
import org.junit.Test;

public class OperationOutcomeFactoryTest {

    private final OperationOutcomeFactory factory = new OperationOutcomeFactory();

    @Test
    public void errorProducesSingleErrorIssue() {
        final OperationOutcome outcome = factory.error(OperationOutcome.IssueType.REQUIRED, "templateId missing");

        Assert.assertEquals(1, outcome.getIssue().size());
        Assert.assertEquals(OperationOutcome.IssueSeverity.ERROR, outcome.getIssueFirstRep().getSeverity());
        Assert.assertEquals(OperationOutcome.IssueType.REQUIRED, outcome.getIssueFirstRep().getCode());
        Assert.assertEquals("templateId missing", outcome.getIssueFirstRep().getDiagnostics());
    }

    @Test
    public void fromIssuesMapsCollectedWarnings() {
        final MappingIssueCollector collector = new MappingIssueCollector();
        collector.addWarning("skipped element one");
        collector.addWarning("skipped element two");

        final OperationOutcome outcome = factory.fromIssues(collector.getIssues());

        Assert.assertEquals(2, outcome.getIssue().size());
        for (final OperationOutcome.OperationOutcomeIssueComponent issue : outcome.getIssue()) {
            Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, issue.getSeverity());
            Assert.assertEquals(OperationOutcome.IssueType.INCOMPLETE, issue.getCode());
        }
        Assert.assertEquals("skipped element one", outcome.getIssue().get(0).getDiagnostics());
    }

    @Test
    public void unknownSeverityAndCodeFallBackToWarningIncomplete() {
        final MappingIssueCollector collector = new MappingIssueCollector();
        collector.add("bogus-severity", "bogus-code", "diag");

        final OperationOutcome outcome = factory.fromIssues(collector.getIssues());

        Assert.assertEquals(OperationOutcome.IssueSeverity.WARNING, outcome.getIssueFirstRep().getSeverity());
        Assert.assertEquals(OperationOutcome.IssueType.INCOMPLETE, outcome.getIssueFirstRep().getCode());
    }
}
