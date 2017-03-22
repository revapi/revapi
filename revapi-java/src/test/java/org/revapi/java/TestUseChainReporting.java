package org.revapi.java;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Difference;

/**
 * @author Lukas Krejci
 * @since 0.12.0
 */
public class TestUseChainReporting extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testCustomReportedCodes() throws Exception {
        String config = "{\"revapi\": {\"java\": {\"reportUsesFor\": [\"java.method.addedToInterface\"]}}}";
        CollectingReporter rep = runAnalysis(CollectingReporter.class, config, new String[] {"v1/methods/Added.java"},
                new String[]{"v2/methods/Added.java"});

        List<Difference> diffs = rep.getReports().stream().flatMap(r -> r.getDifferences().stream())
                .filter(d -> "java.method.addedToInterface".equals(d.code))
                .collect(Collectors.toList());

        Assert.assertEquals(2, diffs.size());
        Assert.assertTrue(diffs.stream().allMatch(d -> d.attachments.get("exampleUseChainInNewApi") != null));
    }
}
