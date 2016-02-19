/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.maven;

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class BuildTimeReporter implements Reporter {

    private final DifferenceSeverity breakingSeverity;
    private StringBuilder allProblems;
    private Locale locale;

    public BuildTimeReporter(DifferenceSeverity breakingSeverity) {
        this.breakingSeverity = breakingSeverity;
        allProblems = new StringBuilder();
    }

    public boolean hasBreakingProblems() {
        return allProblems.length() > 0;
    }

    public String getAllProblemsMessage() {
        return "The following API problems caused the build to fail:" + allProblems.toString();
    }

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext properties) {
        locale = properties.getLocale();
    }

    @Override
    public void report(@Nonnull Report report) {
        Element element = report.getNewElement();
        if (element == null) {
            element = report.getOldElement();
        }

        if (element == null) {
            //wat? At least one of old and new should always be non-null
            return;
        }

        for (Difference d : report.getDifferences()) {
            DifferenceSeverity maxSeverity = DifferenceSeverity.NON_BREAKING;
            for (DifferenceSeverity s : d.classification.values()) {
                if (maxSeverity.compareTo(s) < 0) {
                    maxSeverity = s;
                }
            }

            if (maxSeverity.compareTo(breakingSeverity) >= 0) {
                String archive = element.getArchive() == null ? "<unknown-archive>" : element.getArchive().getName();
                allProblems.append("\n[").append(archive).append("] ").append(element.getFullHumanReadableString()).append(": ").append(d.code)
                        .append(": ").append(d.description);
                appendIgnoreRecipe(allProblems, report, d);
            }
        }
    }

    @Override
    public void close() throws IOException {
    }

    private void appendIgnoreRecipe(StringBuilder bld, Report report, Difference difference) {
        bld.append("\nIf you're using the semver-ignore extension, update your module's version to one compatible " +
                "with the current changes (e.g. mvn package revapi:update-versions). If you want to " +
                "explicitly ignore this change and provide a justification for it, add the following JSON snippet " +
                "to your Revapi configuration under \"revapi.ignore\" path:\n");
        bld.append("{\n");
        bld.append("  \"code\": \"").append(difference.code).append("\",\n");
        if (report.getOldElement() != null) {
            bld.append("  \"old\": \"").append(report.getOldElement()).append("\",\n");
        }
        if (report.getNewElement() != null) {
            bld.append("  \"new\": \"").append(report.getNewElement()).append("\",\n");
        }
        bld.append("  \"justification\": <<<<< ADD YOUR EXPLANATION FOR THE NECESSITY OF THIS CHANGE >>>>>\n");
        bld.append("}");
    }
}
