/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class AnnotationChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testAnnotationAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/annotations/Added.java", "v2/annotations/Added.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ADDED.code()));
    }

    @Test
    public void testAnnotationRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/annotations/Added.java", "v1/annotations/Added.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_REMOVED.code()));
    }
}
