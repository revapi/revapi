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
package org.revapi.maven;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;

public class ApiBreakingHintageReporterTest {

    @Test
    public void testNoDifferences() throws Exception {
        Report report = reportWithDifferencesClassifications();

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.NO_CHANGE, reporter.getChangeLevel());
        }
    }

    @Test
    public void testBreaking() throws Exception {
        Report report = reportWithDifferencesClassifications(DifferenceSeverity.BREAKING);

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.BREAKING_CHANGES, reporter.getChangeLevel());
        }
    }

    @Test
    public void testPotentiallyBreaking() throws Exception {
        Report report = reportWithDifferencesClassifications(DifferenceSeverity.POTENTIALLY_BREAKING);

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.NON_BREAKING_CHANGES, reporter.getChangeLevel());
        }
    }

    @Test
    public void testNonBreaking() throws Exception {
        Report report = reportWithDifferencesClassifications(DifferenceSeverity.NON_BREAKING);

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.NON_BREAKING_CHANGES, reporter.getChangeLevel());
        }
    }

    @Test
    public void testEquivalent() throws Exception {
        Report report = reportWithDifferencesClassifications(DifferenceSeverity.EQUIVALENT);

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.NO_CHANGE, reporter.getChangeLevel());
        }
    }

    @Test
    public void testBreakingTakesPriority() throws Exception {
        Report report = reportWithDifferencesClassifications(EnumSet.allOf(DifferenceSeverity.class));

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.BREAKING_CHANGES, reporter.getChangeLevel());
        }
    }

    @Test
    public void testNonBreakingTakesPriority() throws Exception {
        Report report = reportWithDifferencesClassifications(
                EnumSet.complementOf(EnumSet.of(DifferenceSeverity.BREAKING)));

        try (ApiBreakageHintingReporter reporter = new ApiBreakageHintingReporter()) {

            reporter.report(report);

            assertEquals(ApiChangeLevel.NON_BREAKING_CHANGES, reporter.getChangeLevel());
        }
    }

    private Report reportWithDifferencesClassifications(DifferenceSeverity... severities) {
        return reportWithDifferencesClassifications(
                severities.length == 0 ? Collections.emptySet() : EnumSet.of(severities[0], severities));
    }

    private Report reportWithDifferencesClassifications(Collection<DifferenceSeverity> severities) {
        Report.Builder reportBuilder = Report.builder();
        for (DifferenceSeverity severity : severities) {
            reportBuilder.addDifference().withCode("only-for-test")
                    .addClassifications(ImmutableMap.of(CompatibilityType.OTHER, severity)).done();
        }
        return reportBuilder.build();
    }

}
