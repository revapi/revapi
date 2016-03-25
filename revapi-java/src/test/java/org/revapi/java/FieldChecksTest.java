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

import org.junit.Assert;
import org.junit.Test;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class FieldChecksTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testFieldAdded() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Added.java", "v2/fields/Added.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.FIELD_ADDED.code()));
    }

    @Test
    public void testFieldRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/fields/Added.java", "v1/fields/Added.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.FIELD_REMOVED.code()));
    }

    @Test
    public void testConstantValueChanged() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Constants.java", "v2/fields/Constants.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_CONSTANT_VALUE_CHANGED.code()));
    }

    @Test
    public void testNowConstant() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Constants.java", "v2/fields/Constants.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_NOW_CONSTANT.code()));
    }

    @Test
    public void testFieldWithConstantValueRemoved() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Constants.java", "v2/fields/Constants.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_CONSTANT_REMOVED.code()));
    }

    @Test
    public void testNoLongerConstant() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/fields/Constants.java", "v1/fields/Constants.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_NO_LONGER_CONSTANT.code()));
    }

    @Test
    public void testNowFinal() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Final.java", "v2/fields/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_NOW_FINAL.code()));
    }

    @Test
    public void testNoLongerFinal() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/fields/Final.java", "v1/fields/Final.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_NO_LONGER_FINAL.code()));
    }

    @Test
    public void testNowStatic() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Static.java", "v2/fields/Static.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_NOW_STATIC.code()));
    }

    @Test
    public void testNoLongerStatic() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/fields/Static.java", "v1/fields/Static.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_NO_LONGER_STATIC.code()));
    }

    @Test
    public void testTypeChanged() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Type.java", "v2/fields/Type.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.FIELD_TYPE_CHANGED.code()));
    }

    @Test
    public void testVisibilityReduced() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Visibility.java", "v2/fields/Visibility.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.FIELD_VISIBILITY_REDUCED.code()));
    }

    @Test
    public void testVisibilityIncreased() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v2/fields/Visibility.java", "v1/fields/Visibility.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.FIELD_VISIBILITY_INCREASED.code()));
    }

    @Test
    public void testSerializabilityChanges() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/Serial.java", "v2/fields/Serial.java");

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.FIELD_SERIAL_VERSION_UID_UNCHANGED.code()));
    }

    @Test
    public void testEnumConstantOrderChanges() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/EnumConstant.java", "v2/fields/EnumConstant.java");

        Assert.assertEquals(2, (int) reporter.getProblemCounters().get(Code.FIELD_ENUM_CONSTANT_ORDER_CHANGED.code()));
    }

    @Test
    public void testEnumConstantOrderUnchanged() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        runAnalysis(reporter, "v1/fields/EnumConstant.java", "v1/fields/EnumConstant.java");

        Assert.assertNull(reporter.getProblemCounters().get(Code.FIELD_ENUM_CONSTANT_ORDER_CHANGED.code()));
    }
}
