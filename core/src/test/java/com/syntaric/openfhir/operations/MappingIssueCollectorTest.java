package com.syntaric.openfhir.operations;

import ca.uhn.fhir.parser.DataFormatException;
import com.syntaric.openfhir.fc.FhirConnectConst;
import com.syntaric.openfhir.mapping.MappingContext;
import com.syntaric.openfhir.mapping.helpers.MappingHelper;
import com.syntaric.openfhir.util.MappingExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class MappingIssueCollectorTest {

    private static MappingExecutionException failure(final Throwable cause) {
        final MappingHelper helper = new MappingHelper();
        helper.setModelMetadataName("Body weight");
        helper.setMappingName("weight");
        return new MappingExecutionException(MappingContext.of(helper, FhirConnectConst.UNIDIRECTIONAL_TOFHIR), cause);
    }

    @Test
    public void lenientCollectorRecordsErrorsAndKeepsGoing() {
        final MappingIssueCollector collector = new MappingIssueCollector();
        Assert.assertFalse(collector.isFailFast());
        Assert.assertFalse(collector.hasErrors());

        collector.addWarning("something was skipped");
        final MappingExecutionException engineFault = failure(new NullPointerException("internal"));
        collector.addError(engineFault);
        collector.addError(failure(new DataFormatException("bad date")));
        collector.addError(failure(new IllegalArgumentException("bad argument")));

        Assert.assertTrue(collector.hasErrors());
        Assert.assertEquals(4, collector.getIssues().size());

        final MappingIssueCollector.MappingIssue warning = collector.getIssues().get(0);
        Assert.assertEquals(MappingIssueCollector.SEVERITY_WARNING, warning.severity());
        Assert.assertEquals(MappingIssueCollector.CODE_INCOMPLETE, warning.code());

        final MappingIssueCollector.MappingIssue exception = collector.getIssues().get(1);
        Assert.assertEquals(MappingIssueCollector.SEVERITY_ERROR, exception.severity());
        Assert.assertEquals(MappingIssueCollector.CODE_EXCEPTION, exception.code());
        Assert.assertEquals(engineFault.getMessage(), exception.diagnostics());
        Assert.assertTrue(exception.diagnostics().contains("mapping 'weight' of model mapper 'Body weight'"));
        Assert.assertFalse(exception.diagnostics().contains("internal"));

        Assert.assertEquals(MappingIssueCollector.CODE_STRUCTURE, collector.getIssues().get(2).code());
        Assert.assertTrue(collector.getIssues().get(2).diagnostics().contains("bad date"));
        Assert.assertEquals(MappingIssueCollector.CODE_PROCESSING, collector.getIssues().get(3).code());
        Assert.assertTrue(collector.getIssues().get(3).diagnostics().contains("bad argument"));
    }

    @Test
    public void failFastCollectorThrowsTheFirstError() {
        final MappingIssueCollector collector = MappingIssueCollector.failFast();
        Assert.assertTrue(collector.isFailFast());

        collector.addWarning("warnings are still recorded");
        Assert.assertEquals(1, collector.getIssues().size());

        final MappingExecutionException failure = failure(new IllegalStateException("boom"));
        try {
            collector.addError(failure);
            Assert.fail("expected the error to be thrown");
        } catch (final MappingExecutionException e) {
            Assert.assertSame(failure, e);
        }
        Assert.assertFalse(collector.hasErrors());
        Assert.assertEquals(1, collector.getIssues().size());
    }

    @Test
    public void hasErrorsIgnoresWarnings() {
        final MappingIssueCollector collector = new MappingIssueCollector();
        collector.addWarning("w");
        collector.add(MappingIssueCollector.SEVERITY_WARNING, "multiple-matches", "m");
        Assert.assertFalse(collector.hasErrors());
        Assert.assertFalse(collector.isEmpty());
    }
}
