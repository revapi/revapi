package org.revapi.java;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class MethodChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testMethodAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Added.java", "v2/methods/Added.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED_TO_FINAL_CLASS.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ABSTRACT_METHOD_ADDED.code()));
    }
}
