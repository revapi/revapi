/*
 * Copyright 2014-2020 Lukas Krejci
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
import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.Difference;
import org.revapi.Report;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ClassChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testClassVisibilityReduced() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/ClassVisibilityReduced.java", "v2/classes/ClassVisibilityReduced.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_VISIBILITY_REDUCED.code()));
    }

    @Test
    public void testClassVisibilityIncreased() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/classes/ClassVisibilityReduced.java", "v1/classes/ClassVisibilityReduced.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_VISIBILITY_INCREASED.code()));
    }

    @Test
    public void testClassAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, new String[]{"v1/classes/ClassVisibilityReduced.java"},
            new String[]{"v2/classes/ClassVisibilityReduced.java", "v2/classes/ClassAdded.java"});

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_ADDED.code()));
    }

    @Test
    public void testClassRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                new String[]{"v1/classes/ClassVisibilityReduced.java", "v2/classes/ClassAdded.java"},
                new String[]{"v1/classes/ClassVisibilityReduced.java"});

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_REMOVED.code()));
    }

    @Test
    public void testNewSuperTypes() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/NewSuperTypes.java", "v2/classes/NewSuperTypes.java");

        Assert.assertEquals(3,
            (int) reporter.getProblemCounters().get(Code.CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS.code()));
        Assert.assertEquals(1,
            (int) reporter.getProblemCounters().get(Code.CLASS_NOW_CHECKED_EXCEPTION.code()));
    }

    @Test
    public void testRemovedSuperTypes() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/classes/NewSuperTypes.java", "v1/classes/NewSuperTypes.java");
        Assert
            .assertEquals(3, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_INHERITS_FROM_CLASS.code()));
    }

    @Test
    public void testChangedSuperTypes() throws Exception {

        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/ChangedSuperTypes.java", "v2/classes/ChangedSuperTypes.java");

        Assert
            .assertEquals(2, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_INHERITS_FROM_CLASS.code()));
        Assert.assertEquals(2,
            (int) reporter.getProblemCounters().get(Code.CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NOW_CHECKED_EXCEPTION.code()));
    }

    @Test
    public void testKindChanged() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/KindChanged.java", "v2/classes/KindChanged.java");

        Assert.assertEquals(4, (int) reporter.getProblemCounters().get(Code.CLASS_KIND_CHANGED.code()));
        Assert.assertEquals(1,
            (int) reporter.getProblemCounters().get(Code.CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS.code()));
        Assert
            .assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_INHERITS_FROM_CLASS.code()));
        Assert.assertEquals(3,
            (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_IMPLEMENTS_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NOW_IMPLEMENTS_INTERFACE.code()));
    }

    @Test
    public void testNewImplementedInterfaces() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/ImplementedInterfaces.java", "v2/classes/ImplementedInterfaces.java");

        Assert
            .assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_IMPLEMENTS_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NOW_IMPLEMENTS_INTERFACE.code()));
    }

    @Test
    public void testRemovedImplementedInterfaces() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/classes/ImplementedInterfaces.java", "v1/classes/ImplementedInterfaces.java");

        Assert
            .assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_IMPLEMENTS_INTERFACE.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NOW_IMPLEMENTS_INTERFACE.code()));
    }

    @Test
    public void testFinalAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/Final.java", "v2/classes/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NOW_FINAL.code()));
    }

    @Test
    public void testFinalRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/classes/Final.java", "v1/classes/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_FINAL.code()));
    }


    @Test
    public void testAbstractAdded() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/Abstract.java", "v2/classes/Abstract.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NOW_ABSTRACT.code()));
    }

    @Test
    public void testAbstractRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v2/classes/Abstract.java", "v1/classes/Abstract.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_NO_LONGER_ABSTRACT.code()));
    }

    @Test
    public void testFormalTypeParametersChanged() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/TypeParams.java", "v2/classes/TypeParams.java");

        Assert.assertEquals(2,
            (int) reporter.getProblemCounters().get(Code.GENERICS_FORMAL_TYPE_PARAMETER_CHANGED.code()));
        Assert
            .assertEquals(1, (int) reporter.getProblemCounters().get(Code.GENERICS_FORMAL_TYPE_PARAMETER_ADDED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(
            Code.GENERICS_FORMAL_TYPE_PARAMETER_REMOVED.code()));
        Assert.assertEquals(1,
            (int) reporter.getProblemCounters().get(Code.CLASS_SUPER_TYPE_TYPE_PARAMETERS_CHANGED.code()));
    }

    @Test
    public void testPublicInNonPublicNotInApi() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/HiddenPublic.java", "v2/classes/HiddenPublic.java");

        Assert.assertNull(reporter.getProblemCounters().get(Code.CLASS_NON_PUBLIC_PART_OF_API.code()));
    }

    @Test
    public void testUnmatchedNonPublicMembersReported() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                null, "misc/NonPublicDescendsDownChildren.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.CLASS_NON_PUBLIC_PART_OF_API.code()));
    }

    @Test
    public void testMatchingNonPublicMembersNotReportedIfConfigured() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "[{" +
                        "\"extension\": \"revapi.java\"," +
                        "\"configuration\": {" +
                        "\"checks\": {" +
                        "\"nonPublicPartOfAPI\": {" +
                        "\"reportUnchanged\": false" +
                        "}}}}]",
                "misc/NonPublicDescendsDownChildren.java", "misc/NonPublicDescendsDownChildren.java");

        Assert.assertNull(reporter.getProblemCounters().get(Code.CLASS_NON_PUBLIC_PART_OF_API.code()));
    }

    @Test
    public void testMatchingNonPublicMembersReportedIfConfigured() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class, "[{" +
                        "\"extension\": \"revapi.java\"," +
                        "\"configuration\": {" +
                        "\"reportUnchanged\": true" +
                        "}}]",
                null, "misc/NonPublicDescendsDownChildren.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.CLASS_NON_PUBLIC_PART_OF_API.code()));
    }

    @Test
    public void testDefaultSerializationDetectionChanges() throws Exception {
        ProblemOccurrenceReporter reporter = runAnalysis(ProblemOccurrenceReporter.class,
                "v1/classes/Serialization.java", "v2/classes/Serialization.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.CLASS_DEFAULT_SERIALIZATION_CHANGED.code()));
        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_SERIAL_VERSION_UID_CHANGED.code()));
    }

    @Test
    public void testExceptionKindChanges() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class,
                "v1/classes/Exceptions.java", "v2/classes/Exceptions.java");

        List<Report> reports = reporter.getReports();

        Assert.assertEquals(4, reports.size());

        Report report = findChangesInClass("Exceptions.ExtendedThrowable", reports);
        Assert.assertNotNull(report);
        Assert.assertEquals(1, report.getDifferences().size());
        assertContainsDifference("java.class.finalClassInheritsFromNewClass", report);

        report = findChangesInClass("Exceptions.ExtendedError", reports);
        Assert.assertNotNull(report);
        Assert.assertEquals(3, report.getDifferences().size());
        assertContainsDifference("java.class.noLongerInheritsFromClass", report);
        assertContainsDifference("java.class.finalClassInheritsFromNewClass", report);
        assertContainsDifference("java.class.nowCheckedException", report);

        report = findChangesInClass("Exceptions.ExtendedUncheckedException", reports);
        Assert.assertNotNull(report);
        Assert.assertEquals(2, report.getDifferences().size());
        assertContainsDifference("java.class.noLongerInheritsFromClass", report);
        assertContainsDifference("java.class.nowCheckedException", report);

        report = findChangesInClass("Exceptions.ExtendedError", reports);
        Assert.assertNotNull(report);
        Assert.assertEquals(3, report.getDifferences().size());
        assertContainsDifference("java.class.noLongerInheritsFromClass", report);
        assertContainsDifference("java.class.finalClassInheritsFromNewClass", report);
        assertContainsDifference("java.class.nowCheckedException", report);
    }

    private static Report findChangesInClass(String clsName, Iterable<Report> reports) {
        String el = "class " + clsName;
        return findReportFor(el, el, reports);
    }

    private static Report findReportFor(String oldElement, String newElement, Iterable<Report> reports) {
        for (Report r : reports) {
            String oldEl = r.getOldElement() == null ? null : r.getOldElement().getFullHumanReadableString();
            String newEl = r.getNewElement() == null ? null : r.getNewElement().getFullHumanReadableString();

            if (Objects.equals(oldEl, oldElement) && Objects.equals(newEl, newElement)) {
                return r;
            }
        }

        return null;
    }

    private static void assertContainsDifference(String differenceCode, Report report) {
        for (Difference d : report.getDifferences()) {
            if (differenceCode.equals(d.code)) {
                return;
            }
        }

        Assert.fail("Expected difference with code " + differenceCode + " in report " + report);
    }
}
