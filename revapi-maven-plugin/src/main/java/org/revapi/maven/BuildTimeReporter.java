/*
 * Copyright 2014-2018 Lukas Krejci
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
 * @author Lukas Krejci
 * @since 0.1
 */
public final class BuildTimeReporter implements Reporter {
    static final String BREAKING_SEVERITY_KEY = "org.revapi.maven.buildTimeBreakingSeverity";
    static final String OUTPUT_NON_IDENTIFYING_ATTACHMENTS = "org.revapi.maven.outputNonIdentifyingAttachments";
    private DifferenceSeverity breakingSeverity;
    private List<Report> allProblems;
    private List<Archive> oldApi;
    private List<Archive> newApi;
    private boolean outputNonIdentifyingAttachments;

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

        StringBuilder ignores = new StringBuilder();

        for (Report r : allProblems) {
            for (Difference d : r.getDifferences()) {
                if (!isReportable(d)) {
                    continue;
                }

                ignores.append("{\n");
                ignores.append("  \"code\": \"").append(escape(d.code)).append("\",\n");
                if (r.getOldElement() != null) {
                    ignores.append("  \"old\": \"").append(escape(r.getOldElement())).append("\",\n");
                }
                if (r.getNewElement() != null) {
                    ignores.append("  \"new\": \"").append(escape(r.getNewElement())).append("\",\n");
                }

                boolean hasOptionalAttachments = false;
                for (Map.Entry<String, String> e : d.attachments.entrySet()) {
                    if (d.isIdentifyingAttachment(e.getKey())) {
                        ignores.append("  \"").append(escape(e.getKey())).append("\": \"").append(escape(e.getValue()))
                                .append("\",\n");
                    } else {
                        hasOptionalAttachments = true;
                    }
                }

                ignores.append("  \"justification\": <<<<< ADD YOUR EXPLANATION FOR THE NECESSITY OF THIS CHANGE" +
                        " >>>>>\n");

                if (outputNonIdentifyingAttachments && hasOptionalAttachments) {
                    ignores.append("  /*\n  Additionally, the following attachments can be used to further identify the difference:\n\n");
                    for (Map.Entry<String, String> e : d.attachments.entrySet()) {
                        if (!d.isIdentifyingAttachment(e.getKey())) {
                            ignores.append("  \"").append(escape(e.getKey())).append("\": \"").append(escape(e.getValue()))
                                    .append("\",\n");
                        }
                    }
                    ignores.append("  */\n");
                }

                ignores.append("},\n");

            }
        }

        return ignores.toString();
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
        Boolean outputNonIdentifyingAttachments = (Boolean) context.getData(OUTPUT_NON_IDENTIFYING_ATTACHMENTS);
        this.outputNonIdentifyingAttachments = outputNonIdentifyingAttachments == null
                ? true
                : outputNonIdentifyingAttachments;
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

    private static String escape(Object obj) {
        if (obj == null) {
            return "null";
        }

        String string = obj.toString();

        char c;
        int i;
        int len = string.length();
        StringBuilder sb = new StringBuilder(len);
        String t;

        for (i = 0; i < len; i += 1) {
            c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '/':
                    sb.append('\\');
                    sb.append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
