/*
 * Copyright $year Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

    @Test
    public void testMethodRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/methods/Added.java", "v1/methods/Added.java");

        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.METHOD_REMOVED.code()));
    }

    @Test
    public void testDefaultValueChangedCheck() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/DefaultValue.java", "v2/methods/DefaultValue.java");

        Assert.assertEquals(6, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_VALUE_CHANGED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_VALUE_ADDED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_VALUE_REMOVED.code()));
    }


    @Test
    public void testAnnotationTypeAttributeAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/DefaultValue.java", "v2/methods/DefaultValue.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters()
            .get(Code.METHOD_ATTRIBUTE_WITH_DEFAULT_ADDED_TO_ANNOTATION_TYPE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters()
            .get(Code.METHOD_ATTRIBUTE_WITH_NO_DEFAULT_ADDED_TO_ANNOTATION_TYPE.code()));
        Assert.assertNull(reporter.getProblemCounters().get(Code.METHOD_ABSTRACT_METHOD_ADDED.code()));
    }

    @Test
    public void testMethodFinality() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Final.java", "v2/methods/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_FINAL.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NO_LONGER_FINAL.code()));
    }
}
