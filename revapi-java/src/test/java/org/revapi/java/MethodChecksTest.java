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
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Element;
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

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED.code()));
        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS.code()));
        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_STATIC_METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ABSTRACT_METHOD_ADDED.code()));
    }

    @Test
    public void testMethodRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/methods/Added.java", "v1/methods/Added.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_ABSTRACT.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_FINAL.code()));
        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.METHOD_MOVED_TO_SUPERCLASS.code()));
        Assert.assertEquals(6, (int) reporter.getProblemCounters().get(Code.METHOD_REMOVED.code()));
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
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_VISIBILITY_REDUCED.code()));
    }

    @Test
    public void testReturnTypeChanges() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/methods/ReturnType.java", "v2/methods/ReturnType.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_CHANGED.code()));
        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_CHANGED_COVARIANTLY.code()));
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

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method int Overloads::a(int)",
                "method double Overloads::a(int)",
                Code.METHOD_RETURN_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void Overloads::a()",
                "method double Overloads::a()",
                Code.METHOD_RETURN_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void Overloads::a(int, long)",
                "method void Overloads::a(int, long, float)",
                Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void Overloads::a(int, long, double, float)",
                "method void Overloads::a(long, int)",
                Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method parameter void Overloads::b(===java.lang.Class<? extends java.lang.Integer>===, java.lang.Object)",
                "method parameter void Overloads::b(===java.lang.Class<?>===, java.lang.Object)",
                Code.METHOD_PARAMETER_TYPE_PARAMETER_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method parameter void Overloads::c(java.lang.Class<java.lang.Long>, ===java.lang.Class<? extends java.lang.Integer>===, float)",
                "method parameter void Overloads::c(java.lang.Class<java.lang.Long>, ===int===, float)",
                Code.METHOD_PARAMETER_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method parameter void Overloads::c(java.lang.Class<? extends java.lang.Integer>, ===java.lang.Class<java.lang.Long>===, int)",
                "method parameter void Overloads::c(java.lang.Class<? extends java.lang.Integer>, ===java.lang.Class<?>===, int)",
                Code.METHOD_PARAMETER_TYPE_PARAMETER_CHANGED)));
    }

    @Test
    public void testAbstractMethod() throws Exception {
        ArrayList<Report> reports = new ArrayList<>();
        CollectingReporter reporter = new CollectingReporter(reports);
        runAnalysis(reporter, "v1/methods/Abstract.java", "v2/methods/Abstract.java");

        Assert.assertEquals(6, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "class Abstract.PubliclyUsedPrivateSuperClass",
                "class Abstract.PubliclyUsedPrivateSuperClass",
                Code.CLASS_NON_PUBLIC_PART_OF_API)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                null,
                "method void Abstract.A::method()",
                Code.METHOD_ADDED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                null,
                "method void Abstract.B::method()",
                Code.METHOD_ADDED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                null,
                "method void Abstract.C::method()",
                Code.METHOD_ADDED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void Abstract::abstractMethod()",
                "method void Abstract::abstractMethod()",
                Code.METHOD_NO_LONGER_ABSTRACT)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void Abstract::concreteMethod()",
                "method void Abstract::concreteMethod()",
                Code.METHOD_NOW_ABSTRACT)));
    }

    @Test
    public void testInheritedMethodsWithExceptions() throws Exception {
        ArrayList<Report> reports = new ArrayList<>();
         CollectingReporter reporter = new CollectingReporter(reports);
        runAnalysis(reporter, "v1/methods/ExceptionsAndInheritance.java", "v2/methods/ExceptionsAndInheritance.java");

        Assert.assertEquals(5, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::abstractUnchecked() throws java.lang.IllegalArgumentException",
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::abstractUnchecked()",
                Code.METHOD_RUNTIME_EXCEPTION_REMOVED
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.Base::concreteChecked() throws java.io.IOException @ ExceptionsAndInheritance.ChildWithNoExceptions",
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::concreteChecked()",
                Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS,
                Code.METHOD_CHECKED_EXCEPTION_REMOVED
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.Base::concreteUnchecked() throws java.lang.IllegalArgumentException @ ExceptionsAndInheritance.ChildWithNoExceptions",
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::concreteUnchecked() throws java.lang.IllegalArgumentException",
                Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.ChildWithSpecializedExceptions::abstractUnchecked()",
                "method void ExceptionsAndInheritance.ChildWithSpecializedExceptions::abstractUnchecked() throws java.lang.IllegalStateException",
                Code.METHOD_RUNTIME_EXCEPTION_ADDED
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.Base::concreteUnchecked() throws java.lang.IllegalArgumentException @ ExceptionsAndInheritance.ChildWithSpecializedExceptions",
                "method void ExceptionsAndInheritance.Base::concreteUnchecked() @ ExceptionsAndInheritance.ChildWithSpecializedExceptions",
                Code.METHOD_RUNTIME_EXCEPTION_REMOVED
        )));
    }

    @Test
    public void testInheritedMethodsWithCovariantReturnTypes() throws Exception {
        ArrayList<Report> reports = new ArrayList<>();
        CollectingReporter reporter = new CollectingReporter(reports);
        runAnalysis(reporter, "v1/methods/CovariantReturnTypeAndInheritance.java", "v2/methods/CovariantReturnTypeAndInheritance.java");

        Assert.assertEquals(5, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method E CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>::genericMethod()",
                "method T CovariantReturnTypeAndInheritance.Base<T>::genericMethod() @ CovariantReturnTypeAndInheritance.Class",
                Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED,
                Code.METHOD_MOVED_TO_SUPERCLASS
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                null,
                "method java.lang.Number CovariantReturnTypeAndInheritance.Class::genericMethod(int)",
                Code.METHOD_ADDED
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method E CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>::nonGenericMethod()",
                "method java.lang.Number CovariantReturnTypeAndInheritance.Base<T>::nonGenericMethod() @ CovariantReturnTypeAndInheritance.Class",
                Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED,
                Code.METHOD_MOVED_TO_SUPERCLASS
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "class CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>",
                "class CovariantReturnTypeAndInheritance.Class",
                Code.GENERICS_FORMAL_TYPE_PARAMETER_REMOVED,
                Code.CLASS_SUPER_TYPE_TYPE_PARAMETERS_CHANGED
        )));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method java.lang.Object CovariantReturnTypeAndInheritance.Base<T>::method() @ CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>",
                "method java.lang.String CovariantReturnTypeAndInheritance.Class::method()",
                Code.METHOD_RETURN_TYPE_CHANGED_COVARIANTLY,
                Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS
        )));
    }

    private Predicate<Report> reportCheck(String expectedOld, String expectedNew, Code... expectedCodes) {
        return r -> Objects.toString(expectedOld).equals(asReadable(r.getOldElement()))
                && Objects.toString(expectedNew).equals(asReadable(r.getNewElement()))
                && r.getDifferences().size() == expectedCodes.length
                && Stream.of(expectedCodes).map(c -> r.getDifferences().stream().anyMatch(d -> d.code.equals(c.code())))
                .reduce(true, Boolean::logicalAnd);
    }

    private static String asReadable(Element el) {
        return el == null ? String.valueOf((Object) null) : el.getFullHumanReadableString();
    }
}
