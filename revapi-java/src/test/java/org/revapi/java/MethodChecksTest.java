/*
 * Copyright 2014-2025 Lukas Krejci
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

import static java.util.stream.Collectors.toMap;

import static org.revapi.java.ExpectedValues.dependingOnJavaVersion;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public class MethodChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testMethodAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/Added.java",
                "v2/methods/Added.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED.code()));
        Assert.assertEquals(4,
                (int) reporter.getProblemCounters().get(Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS.code()));
        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1,
                (int) reporter.getProblemCounters().get(Code.METHOD_STATIC_METHOD_ADDED_TO_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_ABSTRACT_METHOD_ADDED.code()));
    }

    @Test
    public void testMethodRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v2/methods/Added.java",
                "v1/methods/Added.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_ABSTRACT.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_FINAL.code()));
        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.METHOD_MOVED_TO_SUPERCLASS.code()));
        Assert.assertEquals(6, (int) reporter.getProblemCounters().get(Code.METHOD_REMOVED.code()));
    }

    @Test
    public void testObjectMethodsNotReportedDueToClassKindChange() throws Exception {
        List<Report> reports = runAnalysis(CollectingReporter.class, "v1/classes/KindChanged.java",
                "v2/classes/KindChanged.java").getReports();

        Function<String, Map<String, List<Difference>>> getDiffs = className -> reports.stream().map(r -> {
            String str = r.getOldElement() != null ? r.getOldElement().getFullHumanReadableString()
                    : r.getNewElement().getFullHumanReadableString();

            if (str.startsWith("method") && (str.contains(className + "::") || str.endsWith("@ " + className))) {
                return new SimpleImmutableEntry<String, List<Difference>>(str, r.getDifferences());
            } else {
                return null;
            }
        }).filter(Objects::nonNull).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

        // class -> interface
        Map<String, List<Difference>> diffsPerMethod = getDiffs.apply("KindChanged.Class");
        Assert.assertEquals(1, diffsPerMethod.size());
        List<Difference> diffs = diffsPerMethod.get("method void KindChanged.Class::<init>()");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);

        // interface -> enum
        diffsPerMethod = getDiffs.apply("KindChanged.Interface");
        Assert.assertEquals((int) dependingOnJavaVersion(8, 7, 12, 8), diffsPerMethod.size());
        diffs = diffsPerMethod.get(
                "method <T extends java.lang.Enum<T>> T java.lang.Enum<E extends java.lang.Enum<E>>::valueOf(java.lang.Class<T>, java.lang.String) @ KindChanged.Interface");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get("method KindChanged.Interface[] KindChanged.Interface::values()");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod
                .get("method int java.lang.Enum<E extends java.lang.Enum<E>>::ordinal() @ KindChanged.Interface");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get("method KindChanged.Interface KindChanged.Interface::valueOf(java.lang.String)");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get(
                "method java.lang.String java.lang.Enum<E extends java.lang.Enum<E>>::name() @ KindChanged.Interface");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get(
                "method java.lang.Class<E> java.lang.Enum<E extends java.lang.Enum<E>>::getDeclaringClass() @ KindChanged.Interface");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod
                .get("method int java.lang.Enum<E extends java.lang.Enum<E>>::compareTo(E) @ KindChanged.Interface");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);

        // enum -> annotation
        diffsPerMethod = getDiffs.apply("KindChanged.Enum");
        Assert.assertEquals((int) dependingOnJavaVersion(8, 8, 12, 9), diffsPerMethod.size());
        diffs = diffsPerMethod.get(
                "method <T extends java.lang.Enum<T>> T java.lang.Enum<E extends java.lang.Enum<E>>::valueOf(java.lang.Class<T>, java.lang.String) @ KindChanged.Enum");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get("method KindChanged.Enum[] KindChanged.Enum::values()");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod
                .get("method int java.lang.Enum<E extends java.lang.Enum<E>>::ordinal() @ KindChanged.Enum");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get("method KindChanged.Enum KindChanged.Enum::valueOf(java.lang.String)");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod
                .get("method java.lang.String java.lang.Enum<E extends java.lang.Enum<E>>::name() @ KindChanged.Enum");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get(
                "method java.lang.Class<E> java.lang.Enum<E extends java.lang.Enum<E>>::getDeclaringClass() @ KindChanged.Enum");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod
                .get("method int java.lang.Enum<E extends java.lang.Enum<E>>::compareTo(E) @ KindChanged.Enum");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get(
                "method java.lang.Class<? extends java.lang.annotation.Annotation> java.lang.annotation.Annotation::annotationType() @ KindChanged.Enum");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ABSTRACT_METHOD_ADDED.code(), diffs.get(0).code);

        // annotation -> class
        diffsPerMethod = getDiffs.apply("KindChanged.Annotation");
        Assert.assertEquals(2, diffsPerMethod.size());
        diffs = diffsPerMethod.get("method void KindChanged.Annotation::<init>()");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_ADDED.code(), diffs.get(0).code);
        diffs = diffsPerMethod.get(
                "method java.lang.Class<? extends java.lang.annotation.Annotation> java.lang.annotation.Annotation::annotationType() @ KindChanged.Annotation");
        Assert.assertNotNull(diffs);
        Assert.assertEquals(1, diffs.size());
        Assert.assertEquals(Code.METHOD_REMOVED.code(), diffs.get(0).code);

        Assert.assertEquals((long) dependingOnJavaVersion(8, 34L, 12, 37L),
                reports.stream().mapToLong(r -> r.getDifferences().size()).sum());
    }

    @Test
    public void testDefaultValueChangedCheck() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/methods/DefaultValue.java", "v2/methods/DefaultValue.java");

        Assert.assertEquals(6, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_VALUE_CHANGED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_VALUE_ADDED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_DEFAULT_VALUE_REMOVED.code()));
    }

    @Test
    public void testAnnotationTypeAttributeAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/methods/DefaultValue.java", "v2/methods/DefaultValue.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters()
                .get(Code.METHOD_ATTRIBUTE_WITH_DEFAULT_ADDED_TO_ANNOTATION_TYPE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters()
                .get(Code.METHOD_ATTRIBUTE_WITH_NO_DEFAULT_ADDED_TO_ANNOTATION_TYPE.code()));
        Assert.assertNull(reporter.getProblemCounters().get(Code.METHOD_ABSTRACT_METHOD_ADDED.code()));
    }

    @Test
    public void testAnnotationTypeAttributeRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/methods/DefaultValue.java", "v1/methods/DefaultValue.java");

        Assert.assertEquals(2,
                (int) reporter.getProblemCounters().get(Code.METHOD_ATTRIBUTE_REMOVED_FROM_ANNOTATION_TYPE.code()));
        Assert.assertNull(reporter.getProblemCounters().get(Code.METHOD_REMOVED.code()));
    }

    @Test
    public void testMethodFinality() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/Final.java",
                "v2/methods/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_FINAL.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NO_LONGER_FINAL.code()));
        Assert.assertEquals(1,
                (int) reporter.getProblemCounters().get(Code.METHOD_FINAL_METHOD_ADDED_TO_NON_FINAL_CLASS.code()));
    }

    @Test
    public void testVisibilityChanges() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/Visibility.java",
                "v2/methods/Visibility.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_VISIBILITY_INCREASED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_VISIBILITY_REDUCED.code()));
    }

    @Test
    public void testReturnTypeChanges() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/ReturnType.java",
                "v2/methods/ReturnType.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_CHANGED.code()));
        Assert.assertEquals(2,
                (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_CHANGED_COVARIANTLY.code()));
        Assert.assertEquals(3,
                (int) reporter.getProblemCounters().get(Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED.code()));
    }

    @Test
    public void testNofParamsChanged() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/NofParams.java",
                "v2/methods/NofParams.java");

        Assert.assertEquals(2,
                (int) reporter.getProblemCounters().get(Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED.code()));
    }

    @Test
    public void testParamTypeChanged() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/ParamType.java",
                "v2/methods/ParamType.java");

        Assert.assertEquals(3, (int) reporter.getProblemCounters().get(Code.METHOD_PARAMETER_TYPE_CHANGED.code()));
    }

    @Test
    public void testParamTypeChangedIgnore() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "[{" + "\"extension\": \"revapi.differences\"," + "\"configuration\": {" + "\"ignore\": true,"
                        + "\"differences\": [" + "{" + "\"code\": \"" + Code.METHOD_PARAMETER_TYPE_CHANGED.code()
                        + "\"," + "\"old\": {" + "\"matcher\": \"java\","
                        + "\"match\": \"type ParamType { ^method1(int, **); }\"" + "}," + "\"parameterIndex\": 2" + "}"
                        + "]}}]",
                "v1/methods/ParamType.java", "v2/methods/ParamType.java");

        Assert.assertEquals(2,
                (int) reporter.getProblemCounters().getOrDefault(Code.METHOD_PARAMETER_TYPE_CHANGED.code(), 0));
    }

    @Test
    public void testStaticMethod() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/Static.java",
                "v2/methods/Static.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NOW_STATIC.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_NO_LONGER_STATIC.code()));
    }

    @Test
    public void testExceptionsThrown() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "v1/methods/Exceptions.java",
                "v2/methods/Exceptions.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.METHOD_CHECKED_EXCEPTION_ADDED.code()));
        Assert.assertEquals(3, (int) reporter.getProblemCounters().get(Code.METHOD_CHECKED_EXCEPTION_REMOVED.code()));
        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.METHOD_RUNTIME_EXCEPTION_ADDED.code()));
        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.METHOD_RUNTIME_EXCEPTION_REMOVED.code()));
    }

    @Test
    public void testDefaultMethod() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/methods/DefaultMethod.java",
                "v2/methods/DefaultMethod.java");
        List<Report> reports = reporter.getReports();

        Assert.assertEquals(5, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void DefaultMethod::a()",
                "method void DefaultMethod::a()", Code.METHOD_NO_LONGER_DEFAULT, Code.METHOD_NOW_ABSTRACT)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void DefaultMethod::a() @ DefaultMethod.Test",
                "method void DefaultMethod.Test::a()", Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method int DefaultMethod::b()",
                "method int DefaultMethod::b()", Code.METHOD_NOW_DEFAULT, Code.METHOD_NO_LONGER_ABSTRACT)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method int DefaultMethod.Test::b()",
                "method int DefaultMethod::b() @ DefaultMethod.Test", Code.METHOD_MOVED_TO_SUPERCLASS)));

        Assert.assertTrue(reports.stream().anyMatch(
                reportCheck(null, "method void DefaultMethod::c()", Code.METHOD_DEFAULT_METHOD_ADDED_TO_INTERFACE)));
    }

    @Test
    public void testOverloadsResolution() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/methods/Overloads.java",
                "v2/methods/Overloads.java");
        List<Report> reports = reporter.getReports();

        Assert.assertEquals(9, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method int Overloads::a(int)",
                "method double Overloads::a(int)", Code.METHOD_RETURN_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void Overloads::a()",
                "method double Overloads::a()", Code.METHOD_RETURN_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("parameter void Overloads::a(===int===, long)",
                "parameter void Overloads::a(===long===, int)", Code.METHOD_PARAMETER_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("parameter void Overloads::a(int, ===long===)",
                "parameter void Overloads::a(long, ===int===)", Code.METHOD_PARAMETER_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void Overloads::a(int, long, double, float)",
                "method void Overloads::a(int, long, float)", Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "parameter void Overloads::b(===java.lang.Class<? extends java.lang.Integer>===, java.lang.Object)",
                "parameter void Overloads::b(===java.lang.Class<?>===, java.lang.Object)",
                Code.METHOD_PARAMETER_TYPE_PARAMETER_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "parameter void Overloads::c(java.lang.Class<java.lang.Long>, ===java.lang.Class<? extends java.lang.Integer>===, float)",
                "parameter void Overloads::c(java.lang.Class<java.lang.Long>, ===int===, float)",
                Code.METHOD_PARAMETER_TYPE_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "parameter void Overloads::c(java.lang.Class<? extends java.lang.Integer>, ===java.lang.Class<java.lang.Long>===, int)",
                "parameter void Overloads::c(java.lang.Class<? extends java.lang.Integer>, ===java.lang.Class<?>===, int)",
                Code.METHOD_PARAMETER_TYPE_PARAMETER_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void Overloads::d(int)",
                "method java.lang.String Overloads::d(int)", Code.METHOD_RETURN_TYPE_CHANGED)));
    }

    @Test
    public void testAbstractMethod() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/methods/Abstract.java",
                "v2/methods/Abstract.java");
        List<Report> reports = reporter.getReports();

        Assert.assertEquals(6, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("class Abstract.PubliclyUsedPrivateSuperClass",
                "class Abstract.PubliclyUsedPrivateSuperClass", Code.CLASS_NON_PUBLIC_PART_OF_API)));

        Assert.assertTrue(
                reports.stream().anyMatch(reportCheck(null, "method void Abstract.A::method()", Code.METHOD_ADDED)));

        Assert.assertTrue(
                reports.stream().anyMatch(reportCheck(null, "method void Abstract.B::method()", Code.METHOD_ADDED)));

        Assert.assertTrue(
                reports.stream().anyMatch(reportCheck(null, "method void Abstract.C::method()", Code.METHOD_ADDED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void Abstract::abstractMethod()",
                "method void Abstract::abstractMethod()", Code.METHOD_NO_LONGER_ABSTRACT)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck("method void Abstract::concreteMethod()",
                "method void Abstract::concreteMethod()", Code.METHOD_NOW_ABSTRACT)));
    }

    @Test
    public void testInheritedMethodsWithExceptions() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/methods/ExceptionsAndInheritance.java",
                "v2/methods/ExceptionsAndInheritance.java");
        List<Report> reports = reporter.getReports();

        Assert.assertEquals(5, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::abstractUnchecked() throws java.lang.IllegalArgumentException",
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::abstractUnchecked()",
                Code.METHOD_RUNTIME_EXCEPTION_REMOVED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.Base::concreteChecked() throws java.io.IOException @ ExceptionsAndInheritance.ChildWithNoExceptions",
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::concreteChecked()",
                Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS, Code.METHOD_CHECKED_EXCEPTION_REMOVED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.Base::concreteUnchecked() throws java.lang.IllegalArgumentException @ ExceptionsAndInheritance.ChildWithNoExceptions",
                "method void ExceptionsAndInheritance.ChildWithNoExceptions::concreteUnchecked() throws java.lang.IllegalArgumentException",
                Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.ChildWithSpecializedExceptions::abstractUnchecked()",
                "method void ExceptionsAndInheritance.ChildWithSpecializedExceptions::abstractUnchecked() throws java.lang.IllegalStateException",
                Code.METHOD_RUNTIME_EXCEPTION_ADDED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method void ExceptionsAndInheritance.Base::concreteUnchecked() throws java.lang.IllegalArgumentException @ ExceptionsAndInheritance.ChildWithSpecializedExceptions",
                "method void ExceptionsAndInheritance.Base::concreteUnchecked() @ ExceptionsAndInheritance.ChildWithSpecializedExceptions",
                Code.METHOD_RUNTIME_EXCEPTION_REMOVED)));
    }

    @Test
    public void testInheritedMethodsWithCovariantReturnTypes() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class,
                "v1/methods/CovariantReturnTypeAndInheritance.java",
                "v2/methods/CovariantReturnTypeAndInheritance.java");
        List<Report> reports = reporter.getReports();

        Assert.assertEquals(5, reports.size());

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method E CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>::genericMethod()",
                "method T CovariantReturnTypeAndInheritance.Base<T>::genericMethod() @ CovariantReturnTypeAndInheritance.Class",
                Code.METHOD_RETURN_TYPE_CHANGED, Code.METHOD_MOVED_TO_SUPERCLASS)));

        Assert.assertTrue(reports.stream()
                .anyMatch(reportCheck(null,
                        "method java.lang.Number CovariantReturnTypeAndInheritance.Class::genericMethod(int)",
                        Code.METHOD_ADDED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method E CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>::nonGenericMethod()",
                "method java.lang.Number CovariantReturnTypeAndInheritance.Base<T>::nonGenericMethod() @ CovariantReturnTypeAndInheritance.Class",
                Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED, Code.METHOD_MOVED_TO_SUPERCLASS)));

        Assert.assertTrue(reports.stream()
                .anyMatch(reportCheck("class CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>",
                        "class CovariantReturnTypeAndInheritance.Class", Code.GENERICS_FORMAL_TYPE_PARAMETER_REMOVED,
                        Code.CLASS_SUPER_TYPE_TYPE_PARAMETERS_CHANGED)));

        Assert.assertTrue(reports.stream().anyMatch(reportCheck(
                "method java.lang.Object CovariantReturnTypeAndInheritance.Base<T>::method() @ CovariantReturnTypeAndInheritance.Class<E extends java.lang.Number>",
                "method java.lang.String CovariantReturnTypeAndInheritance.Class::method()",
                Code.METHOD_RETURN_TYPE_CHANGED_COVARIANTLY, Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS)));
    }

    @Test
    public void testInheritedMethodsNotReportedRepeatedly() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class,
                "v1/methods/DryReportingWithInheritance.java", "v2/methods/DryReportingWithInheritance.java");

        Assert.assertEquals(2, reporter.getReports().size());
    }

    @Test
    public void testReplacementByGenericSuperClassMethod() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class,
                "v1/methods/ReplacedByGenericVariantInSuperClass.java",
                "v2/methods/ReplacedByGenericVariantInSuperClass.java");

        Assert.assertEquals(3, reporter.getReports().size());

        Assert.assertTrue(reporter.getReports().stream().anyMatch(reportCheck(
                "method java.lang.String ReplacedByGenericVariantInSuperClass.Test::method()",
                "method T ReplacedByGenericVariantInSuperClass.Base<T>::method() @ ReplacedByGenericVariantInSuperClass.Test",
                Code.METHOD_MOVED_TO_SUPERCLASS, Code.METHOD_RETURN_TYPE_ERASURE_CHANGED)));
        Assert.assertTrue(reporter.getReports().stream().anyMatch(reportCheck(
                "parameter void ReplacedByGenericVariantInSuperClass.Test::method(===java.lang.String===)",
                "parameter void ReplacedByGenericVariantInSuperClass.Base<T>::method(===T===) @ ReplacedByGenericVariantInSuperClass.Test",
                Code.METHOD_PARAMETER_TYPE_ERASURE_CHANGED)));
        Assert.assertTrue(reporter.getReports().stream().anyMatch(reportCheck(
                "method void ReplacedByGenericVariantInSuperClass.Test::method(java.lang.String)",
                "method void ReplacedByGenericVariantInSuperClass.Base<T>::method(T) @ ReplacedByGenericVariantInSuperClass.Test",
                Code.METHOD_MOVED_TO_SUPERCLASS)));
    }

    @Test
    public void testMethodOverloadsOnlyDifferInVarargs() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class, "v1/methods/Varargs.java",
                "v2/methods/Varargs.java");

        Assert.assertThat(reporter.getReports(),
                new ReportMatcher("method void Varargs::varargs(int, float, int[])",
                        "method void Varargs::varargs(int, float, int[])", null,
                        Code.METHOD_VARARG_OVERLOADS_ONLY_DIFFER_IN_VARARG_PARAMETER));
        Assert.assertThat(reporter.getReports(),
                new ReportMatcher("method void Varargs::varargs(int, float)",
                        "method void Base::varargs(int, float, java.lang.Object[]) @ Varargs", null,
                        Code.METHOD_VARARG_OVERLOADS_ONLY_DIFFER_IN_VARARG_PARAMETER,
                        Code.METHOD_NUMBER_OF_PARAMETERS_CHANGED));

        // check that the non-varargs methods don't show up in the vararg differences...
        Assert.assertFalse(reporter.getReports().stream()
                .anyMatch(reportCheck("method void Varargs::varargs(int, float, int[])",
                        "method void Varargs::varargs(int, float, int[])",
                        Pattern.compile(".*method void Varargs::varargs\\(int, float\\).*"),
                        Code.METHOD_VARARG_OVERLOADS_ONLY_DIFFER_IN_VARARG_PARAMETER)));
    }

    private static Predicate<Report> reportCheck(String expectedOld, String expectedNew, Code... expectedCodes) {
        return r -> Objects.toString(expectedOld).equals(asReadable(r.getOldElement()))
                && Objects.toString(expectedNew).equals(asReadable(r.getNewElement()))
                && r.getDifferences().size() == expectedCodes.length
                && Stream.of(expectedCodes).map(c -> r.getDifferences().stream().anyMatch(d -> d.code.equals(c.code())))
                        .reduce(true, Boolean::logicalAnd);
    }

    private static Predicate<Report> reportCheck(String expectedOld, String expectedNew, Pattern descriptionRegex,
            Code... expectedCodes) {
        return r -> Objects.toString(expectedOld).equals(asReadable(r.getOldElement()))
                && Objects.toString(expectedNew).equals(asReadable(r.getNewElement()))
                && r.getDifferences().size() == expectedCodes.length
                && r.getDifferences().stream()
                        .anyMatch(d -> descriptionRegex == null || descriptionRegex.matcher(d.description).matches())
                && Stream.of(expectedCodes).map(c -> r.getDifferences().stream().anyMatch(d -> d.code.equals(c.code())))
                        .reduce(true, Boolean::logicalAnd);
    }

    private static String asReadable(Element<?> el) {
        return el == null ? String.valueOf((Object) null) : el.getFullHumanReadableString();
    }

    private static final class ReportMatcher extends BaseMatcher<List<Report>> {
        private final @Nullable String expectedOld;
        private final @Nullable String expectedNew;
        private final @Nullable Code[] expectedCodes;
        private final @Nullable Pattern descriptionRegex;

        public ReportMatcher(String expectedOld, String expectedNew, Pattern descriptionRegex, Code... expectedCodes) {
            this.expectedOld = expectedOld;
            this.expectedNew = expectedNew;
            this.expectedCodes = expectedCodes;
            this.descriptionRegex = descriptionRegex;
        }

        @Override
        public boolean matches(Object item) {
            if (!(item instanceof List)) {
                return false;
            }
            List<?> list = (List<?>) item;
            return list.stream().filter(i -> i instanceof Report).map(i -> (Report) i)
                    .anyMatch(reportCheck(expectedOld, expectedNew, descriptionRegex, expectedCodes));
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("report matcher expecting old: ").appendText(expectedOld).appendText(", new: ")
                    .appendText(expectedNew).appendText(", descriptionRegex: ")
                    .appendText(descriptionRegex == null ? "null" : descriptionRegex.pattern())
                    .appendValueList(", codes", ", ", ".", Arrays.asList(expectedCodes));
        }

        @Override
        public void describeMismatch(Object item, Description description) {
            description.appendText("none of the reports in ").appendValue(item).appendText(" matches");
        }
    }
}
