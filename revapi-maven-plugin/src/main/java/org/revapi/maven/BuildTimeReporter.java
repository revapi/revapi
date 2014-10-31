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
    }

    @Override
    public void report(@Nonnull Report report) {
        Element element = report.getOldElement();
        if (element == null) {
            element = report.getNewElement();
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
                allProblems.append("\n").append(element.getFullHumanReadableString()).append(": ").append(d.code)
                    .append(": ").append(d.description);
            }
        }
    }

    @Override
    public void close() throws IOException {
    }
}
