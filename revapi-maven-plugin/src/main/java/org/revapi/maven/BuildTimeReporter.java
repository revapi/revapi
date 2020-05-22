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
package org.revapi.maven;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * Build-time reporter is a {@link Reporter} extension for Revapi that is used by the {@link CheckMojo} to output the
 * found problems and also to provide the suggestions to the user how to ignore the problems if deemed ok.
 * <p>
 * Even though this is a normal Revapi extension, it doesn't use the normal configuration mechanisms provided by Revapi
 * (i.e. it doesn't define a configuration schema and doesn't accept any configuration from the configuration in the
 * analysis context during the {@link #initialize(AnalysisContext)} method). Instead, it is configured through the
 * {@link AnalysisContext#getData(String)}. This is to make it easier to pass complex objects to the reporter and also
 * to amplify the fact that this is no "normal" extension but is tightly bound to the {@link CheckMojo} and the Maven
 * build.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class BuildTimeReporter implements Reporter {
    public static final String BREAKING_SEVERITY_KEY = "org.revapi.maven.buildTimeBreakingSeverity";
    public static final String OUTPUT_NON_IDENTIFYING_ATTACHMENTS = "org.revapi.maven.outputNonIdentifyingAttachments";
    public static final String SUGGESTIONS_BUILDER_KEY = "org.revapi.maven.buildTimeSuggestionsBuilder";

    private DifferenceSeverity breakingSeverity;
    private List<Report> allProblems;
    private List<Archive> oldApi;
    private List<Archive> newApi;
    private boolean outputNonIdentifyingAttachments;
    private SuggestionsBuilder suggestionsBuilder;

    public boolean hasBreakingProblems() {
        return allProblems != null && !allProblems.isEmpty();
    }

    public String getAllProblemsMessage() {
        StringBuilder errors = new StringBuilder("The following API problems caused the build to fail:\n");
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
                }
            }
        }

        return errors.toString();
    }

    public String getIgnoreSuggestion() {
        if (allProblems.isEmpty()) {
            return null;
        }

        return suggestionsBuilder.build(allProblems, new SuggestionBuilderContext());
    }

    @Nullable @Override public String getExtensionId() {
        return "revapi.maven.internal.buildTimeReporter";
    }

    @Nullable @Override public Reader getJSONSchema() {
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
        this.breakingSeverity = (DifferenceSeverity) context.getData(BREAKING_SEVERITY_KEY);
        if (breakingSeverity == null) {
            throw new IllegalStateException("Breaking severity must be provided in the context data of the" +
                    " BuildTimeReporter. If you see this, you've come across a bug, please report it.");
        }

        Boolean outputNonIdentifyingAttachments = (Boolean) context.getData(OUTPUT_NON_IDENTIFYING_ATTACHMENTS);
        this.outputNonIdentifyingAttachments = outputNonIdentifyingAttachments == null
                ? true
                : outputNonIdentifyingAttachments;
        this.suggestionsBuilder = (SuggestionsBuilder) context.getData(SUGGESTIONS_BUILDER_KEY);

        if (suggestionsBuilder == null) {
            throw new IllegalStateException("SuggestionBuilder instance must be provided in the context data of the" +
                    " BuildTimeReporter. If you see this, you've come across a bug, please report it.");
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

    /**
     * A suggestion builder is an object that the {@link BuildTimeReporter} uses to render suggestions for ignoring
     * the found problems.
     */
    public interface SuggestionsBuilder {
        String build(List<Report> reports, SuggestionBuilderContext context);
    }

    /**
     * The context that can be used by the {@link SuggestionsBuilder} to get information about the differences
     * and the configured output options.
     */
    public final class SuggestionBuilderContext {
        boolean isReportable(Difference difference) {
            return BuildTimeReporter.this.isReportable(difference);
        }

        boolean isAttachmentsReported() {
            return outputNonIdentifyingAttachments;
        }
    }
}
