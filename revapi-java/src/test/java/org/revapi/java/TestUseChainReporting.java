package org.revapi.java;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Difference;
import org.revapi.Report;

/**
 * @author Lukas Krejci
 * @since 0.12.0
 */
public class TestUseChainReporting extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testCustomReportedCodes() throws Exception {
        String config = "{\"revapi\": {\"java\": {\"reportUsesFor\": [\"java.method.addedToInterface\"]}}}";
        List<Report> reports = new ArrayList<>();
        runAnalysis(new CollectingReporter(reports), config, new String[] {"v1/methods/Added.java"},
                new String[]{"v2/methods/Added.java"});

        List<Difference> diffs = reports.stream().flatMap(r -> r.getDifferences().stream())
                .filter(d -> "java.method.addedToInterface".equals(d.code))
                .filter(d -> d.description != null
                        && d.description.endsWith("Use chain of the type in the old API: <null>\nUse chain of the" +
                        " type in the new API: "))
                .collect(Collectors.toList());

        Assert.assertTrue(diffs.isEmpty());
    }
}
