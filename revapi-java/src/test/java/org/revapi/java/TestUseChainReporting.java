/*
 * Copyright 2014-2023 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Difference;

/**
 * @author Lukas Krejci
 *
 * @since 0.12.0
 */
public class TestUseChainReporting extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testCustomReportedCodes() throws Exception {
        String config = "{\"revapi\": {\"java\": {\"reportUsesFor\": [\"java.method.addedToInterface\"]}}}";
        CollectingReporter rep = runAnalysis(CollectingReporter.class, config, new String[] { "v1/methods/Added.java" },
                new String[] { "v2/methods/Added.java" });

        List<Difference> diffs = rep.getReports().stream().flatMap(r -> r.getDifferences().stream())
                .filter(d -> "java.method.addedToInterface".equals(d.code)).collect(Collectors.toList());

        Assert.assertEquals(2, diffs.size());
        Assert.assertTrue(diffs.stream().allMatch(d -> d.attachments.get("exampleUseChainInNewApi") != null));
    }
}
