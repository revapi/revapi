/*
 * Copyright 2014-2021 Lukas Krejci
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
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public class AnnotationChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testAnnotationAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                new String[] { "v1/annotations/Added.java", "v1/annotations/InheritedAnnotation.java" },
                new String[] { "v2/annotations/Added.java", "v2/annotations/InheritedAnnotation.java" });

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ADDED.code()));
    }

    @Test
    public void testAnnotationRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                new String[] { "v2/annotations/Added.java", "v2/annotations/InheritedAnnotation.java" },
                new String[] { "v1/annotations/Added.java", "v1/annotations/InheritedAnnotation.java" });

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_REMOVED.code()));
    }

    @Test
    public void testAnnotationNewlyInherited() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/annotations/InheritedAnnotation.java", "v2/annotations/InheritedAnnotation.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_NOW_INHERITED.code()));
    }

    @Test
    public void testAnnotationNoLongerInherited() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/annotations/InheritedAnnotation.java", "v1/annotations/InheritedAnnotation.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_NO_LONGER_INHERITED.code()));
    }

    @Test
    public void testAnnotationAttributeAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ATTRIBUTE_ADDED.code()));
    }

    @Test
    public void testAnnotationAttributeRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/annotations/Attributes.java", "v1/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ATTRIBUTE_REMOVED.code()));
    }

    @Test
    public void testAnnotationAttributeChanged() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ANNOTATION_ATTRIBUTE_VALUE_CHANGED.code()));
    }

    @Test
    public void testElementDeprecated() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ELEMENT_NOW_DEPRECATED.code()));
    }

    @Test
    public void testElementNoLongerDeprecated() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/annotations/Attributes.java", "v1/annotations/Attributes.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.ELEMENT_NO_LONGER_DEPRECATED.code()));
    }

    @Test
    public void testDownplayedJREAnnotations() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/annotations/Downplayed.java",
                "v2/annotations/Downplayed.java");
        List<Report> reports = reporter.getReports();

        Assert.assertEquals(2, reports.size());
        Assert.assertTrue(reports.stream().flatMap(r -> r.getDifferences().stream())
                .flatMap(d -> d.classification.values().stream()).allMatch(ds -> ds == DifferenceSeverity.EQUIVALENT));
        Assert.assertArrayEquals(new String[] { "java.annotation.added" }, reports.stream()
                .flatMap(r -> r.getDifferences().stream()).map(d -> d.code).distinct().toArray(String[]::new));
    }

    @Test
    public void testAnnotationsCapturedOnAllLocations() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/annotations/Elements.java",
                "v2/annotations/Elements.java");
        List<Report> reports = reporter.getReports();

        Function<Class<?>, Stream<Difference>> diffsOn = cls -> reports.stream()
                .filter(r -> (r.getNewElement() != null && cls.isAssignableFrom(r.getNewElement().getClass()))
                        || (r.getOldElement() != null && cls.isAssignableFrom(r.getOldElement().getClass())))
                .flatMap(r -> r.getDifferences().stream());

        Assert.assertEquals(4, reports.size());

        Assert.assertEquals(2L, diffsOn.apply(MethodParameterElement.class).count());
        Assert.assertTrue(
                diffsOn.apply(MethodParameterElement.class).allMatch(d -> d.code.equals(Code.ANNOTATION_ADDED.code())));

        Assert.assertEquals(1L, diffsOn.apply(TypeElement.class).count());
        Assert.assertTrue(diffsOn.apply(TypeElement.class).allMatch(d -> d.code.equals(Code.ANNOTATION_ADDED.code())));

        Assert.assertEquals(1L, diffsOn.apply(MethodElement.class).count());
        Assert.assertTrue(
                diffsOn.apply(MethodElement.class).allMatch(d -> d.code.equals(Code.ANNOTATION_ADDED.code())));

        // the annotations are present on the type uses in the test class, too, but Revapi currently doesn't handle
        // those...
    }

    @Test
    public void testAttachmentsOfTheAnnotatedElementPresent() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/annotations/AttachmentsPresence.java",
                "v2/annotations/AttachmentsPresence.java");
        List<Report> reports = reporter.getReports();

        Assert.assertTrue(reports.stream().flatMap(r -> r.getDifferences().stream()).map(d -> d.attachments)
                .allMatch(ats -> !"annotation".equals(ats.get("elementKind")) && ats.containsKey("package")
                        && ats.containsKey("classSimpleName")));
    }

    // TODO also check for situation where the annotation used is not on the classpath - wonder how that behaves
}
