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
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Archive;
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
    private List<Report> allProblems;
    private List<Archive> oldApi;
    private List<Archive> newApi;

    public BuildTimeReporter(DifferenceSeverity breakingSeverity) {
        this.breakingSeverity = breakingSeverity;
    }

    public boolean hasBreakingProblems() {
        return allProblems != null && !allProblems.isEmpty();
    }

    public String getAllProblemsMessage() {
        StringBuilder errors = new StringBuilder("The following API problems caused the build to fail:\n");
        StringBuilder ignores = new StringBuilder();
        for (Report r : allProblems) {
            Element element = r.getNewElement();
            Archive archive;
            if (element == null) {
                element = r.getOldElement();
                assert element != null;
                archive = shouldOutputArchive(oldApi, element.getArchive()) ? element.getArchive() : null;
            } else {
                archive = shouldOutputArchive(newApi, element.getArchive()) ? element.getArchive() : null;
            }

            for (Difference d : r.getDifferences()) {
                if (isReportable(d)) {
                    errors.append(d.code).append(": ").append(element.getFullHumanReadableString()).append(": ")
                            .append(d.description);
                    if (archive != null) {
                        errors.append(" [").append(archive.getName()).append("]");
                    }
                    errors.append("\n");

                    ignores.append("{\n");
                    ignores.append("  \"code\": \"").append(d.code).append("\",\n");
                    if (r.getOldElement() != null) {
                        ignores.append("  \"old\": \"").append(r.getOldElement()).append("\",\n");
                    }
                    if (r.getNewElement() != null) {
                        ignores.append("  \"new\": \"").append(r.getNewElement()).append("\",\n");
                    }
                    ignores.append("  \"justification\": <<<<< ADD YOUR EXPLANATION FOR THE NECESSITY OF THIS CHANGE" +
                            " >>>>>\n");
                    ignores.append("},\n");
                }
            }
        }

        if (errors.length() == 0) {
            return null;
        } else {
            ignores.replace(ignores.length() - 2, ignores.length(), "");
            return errors.toString() +
                    "\nIf you're using the semver-ignore extension, update your module's version to one compatible " +
                    "with the current changes (e.g. mvn package revapi:update-versions). If you want to " +
                    "explicitly ignore this change and provide a justification for it, add the following JSON snippet " +
                    "to your Revapi configuration under \"revapi.ignore\" path:\n" + ignores.toString();
        }
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
    public void initialize(@Nonnull AnalysisContext context) {
        allProblems = new ArrayList<>();
        oldApi = new ArrayList<>();
        for (Archive a : context.getOldApi().getArchives()) {
            oldApi.add(a);
        }
        newApi = new ArrayList<>();
        for (Archive a : context.getNewApi().getArchives()) {
            newApi.add(a);
        }
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
            if (isReportable(d)) {
                allProblems.add(report);
                break;
            }
        }
    }

    private boolean isReportable(Difference d) {
        DifferenceSeverity maxSeverity = DifferenceSeverity.NON_BREAKING;
        for (DifferenceSeverity s : d.classification.values()) {
            if (maxSeverity.compareTo(s) < 0) {
                maxSeverity = s;
            }
        }

        return maxSeverity.compareTo(breakingSeverity) >= 0;
    }

    private boolean shouldOutputArchive(List<Archive> primaryApi, Archive archive) {
        return !primaryApi.contains(archive) || primaryApi.size() > 1;
    }

    @Override
    public void close() throws IOException {
    }
}
