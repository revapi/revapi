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
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class AnnotationChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testAnnotationAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, new String[]{"v1/annotations/Added.java", "v1/annotations/InheritedAnnotation.java"},
            new String[]{"v2/annotations/Added.java", "v2/annotations/InheritedAnnotation.java"});

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ADDED.code()));
    }

    @Test
    public void testAnnotationRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, new String[]{"v2/annotations/Added.java", "v2/annotations/InheritedAnnotation.java"},
            new String[]{"v1/annotations/Added.java", "v1/annotations/InheritedAnnotation.java"});

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_REMOVED.code()));
    }

    @Test
    public void testAnnotationNewlyInherited() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/annotations/InheritedAnnotation.java", "v2/annotations/InheritedAnnotation.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_NOW_INHERITED.code()));
    }

    @Test
    public void testAnnotationNoLongerInherited() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/annotations/InheritedAnnotation.java", "v1/annotations/InheritedAnnotation.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_NO_LONGER_INHERITED.code()));
    }

    @Test
    public void testAnnotationAttributeAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ATTRIBUTE_ADDED.code()));
    }

    @Test
    public void testAnnotationAttributeRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/annotations/Attributes.java", "v1/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ATTRIBUTE_REMOVED.code()));
    }

    @Test
    public void testAnnotationAttributeChanged() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ATTRIBUTE_VALUE_CHANGED.code()));
    }

    @Test
    public void testElementDeprecated() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ELEMENT_NOW_DEPRECATED.code()));
    }

    @Test
    public void testElementNoLongerDeprecated() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/annotations/Attributes.java", "v1/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ELEMENT_NO_LONGER_DEPRECATED.code()));
    }

    //TODO also check for situation where the annotation used is not on the classpath - wonder how that behaves
}
