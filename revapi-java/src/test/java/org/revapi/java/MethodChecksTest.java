/*
 * Copyright 2015 Lukas Krejci
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

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Report;
import org.revapi.java.spi.Code;
import org.revapi.simple.SimpleReporter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class MethodChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testMethodAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Added.java", "v2/methods/Added.java");

        Assert.assertEquals(5, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_STATIC_METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ABSTRACT_METHOD_ADDED.code()));
    }

    @Test
    public void testMethodRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/methods/Added.java", "v1/methods/Added.java");

        Assert.assertEquals(1,
            (int) reporter.getProblemCounters().get(Code.METHOD_REPLACED_BY_ABSTRACT_METHOD_IN_SUPERCLASS.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters()
            .get(Code.METHOD_NON_FINAL_METHOD_REPLACED_BY_FINAL_IN_SUPERCLASS.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_OVERRIDING_METHOD_REMOVED.code()));
        Assert.assertEquals(5, (int) reporter.getProblemCounters().get(Code.METHOD_REMOVED.code()));
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
    public void testAnnotationTypeAttributeRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/methods/DefaultValue.java", "v1/methods/DefaultValue.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters()
            .get(Code.METHOD_ATTRIBUTE_REMOVED_FROM_ANNOTATION_TYPE.code()));
        Assert.assertNull(reporter.getProblemCounters().get(Code.METHOD_REMOVED.code()));
    }

    @Test
    public void testMethodFinality() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Final.java", "v2/methods/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_FINAL.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NO_LONGER_FINAL.code()));
        Assert.assertEquals(1,
            (int) reporter.getProblemCounters().get(Code.METHOD_FINAL_METHOD_ADDED_TO_NON_FINAL_CLASS.code()));
    }

    @Test
    public void testVisibilityChanges() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Visibility.java", "v2/methods/Visibility.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_VISIBILITY_INCREASED.code()));
        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_VISIBILITY_REDUCED.code()));
    }

    @Test
    public void testReturnTypeChanges() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/ReturnType.java", "v2/methods/ReturnType.java");

        Assert.assertEquals(3, (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_CHANGED.code()));
        Assert.assertEquals(3,
            (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED.code()));
    }

    @Test
    public void testNofParamsChanged() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/NofParams.java", "v2/methods/NofParams.java");

        Assert
            .assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED.code()));
    }

    @Test
    public void testParamTypeChanged() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/ParamType.java", "v2/methods/ParamType.java");

        Assert.assertEquals(3, (int) reporter.getProblemCounters().get(Code.METHOD_PARAMETER_TYPE_CHANGED.code()));
    }

    @Test
    public void testStaticMethod() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Static.java", "v2/methods/Static.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_STATIC.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NO_LONGER_STATIC.code()));
    }

    @Test
    public void testExceptionsThrown() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/Exceptions.java", "v2/methods/Exceptions.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_CHECKED_EXCEPTION_ADDED.code()));
        Assert.assertEquals(3, (int) reporter.getProblemCounters().get(Code.METHOD_CHECKED_EXCEPTION_REMOVED.code()));
        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_RUNTIME_EXCEPTION_ADDED.code()));
        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.METHOD_RUNTIME_EXCEPTION_REMOVED.code()));
    }

    @Test
    public void testDefaultMethod() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/DefaultMethod.java", "v2/methods/DefaultMethod.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_METHOD_ADDED_TO_INTERFACE
                .code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NO_LONGER_DEFAULT.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_DEFAULT.code()));
    }

    @Test
    public void testOverloadsResolution() throws Exception {
        List<Report> reports = new ArrayList<>();
        SimpleReporter reporter = new SimpleReporter() {
            @Override
            public void report(@Nonnull Report report) {
                reports.add(report);
            }

            @Override
            public String toString() {
                final StringBuilder sb = new StringBuilder("Reports");
                sb.append(reports);
                return sb.toString();
            }
        };
        runAnalysis(reporter, "v1/methods/Overloads.java", "v2/methods/Overloads.java");

        Assert.assertEquals(7, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method int Overloads::a(int)".equals(r.getOldElement().toString())
                                && "method double Overloads::a(int)".equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream()
                                .allMatch(d -> Code.METHOD_RETURN_TYPE_CHANGED.code().equals(d.code))
        ));

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method void Overloads::a()".equals(r.getOldElement().toString())
                                && "method double Overloads::a()".equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream()
                                .allMatch(d -> Code.METHOD_RETURN_TYPE_CHANGED.code().equals(d.code))
        ));

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method void Overloads::a(int, long)".equals(r.getOldElement().toString())
                                && "method void Overloads::a(int, long, float)".equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream()
                                .allMatch(d -> Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED.code().equals(d.code))
        ));

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method void Overloads::a(int, long, double, float)".equals(r.getOldElement().toString())
                                && "method void Overloads::a(long, int)".equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream()
                                .allMatch(d -> Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED.code().equals(d.code))
        ));

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method parameter void Overloads::b(===java.lang.Class<? extends java.lang.Integer>===, java.lang.Object)"
                                .equals(r.getOldElement().toString())
                                && "method parameter void Overloads::b(===java.lang.Class<?>===, java.lang.Object)"
                                .equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream().allMatch(d -> Code.METHOD_PARAMETER_TYPE_CHANGED.code().equals(d.code))
        ));

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method parameter void Overloads::c(java.lang.Class<java.lang.Long>, ===java.lang.Class<? extends java.lang.Integer>===, float)"
                                .equals(r.getOldElement().toString())
                                && "method parameter void Overloads::c(java.lang.Class<java.lang.Long>, ===int===, float)"
                                .equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream().allMatch(d -> Code.METHOD_PARAMETER_TYPE_CHANGED.code().equals(d.code))
        ));

        Assert.assertTrue(reports.stream().anyMatch(r ->
                        "method parameter void Overloads::c(java.lang.Class<? extends java.lang.Integer>, ===java.lang.Class<java.lang.Long>===, int)"
                                .equals(r.getOldElement().toString())
                                && "method parameter void Overloads::c(java.lang.Class<? extends java.lang.Integer>, ===java.lang.Class<?>===, int)"
                                .equals(r.getNewElement().toString())
                                && r.getDifferences().size() == 1
                                && r.getDifferences().stream().allMatch(d -> Code.METHOD_PARAMETER_TYPE_CHANGED.code().equals(d.code))
        ));
    }
}
