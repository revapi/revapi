/*
 * Copyright 2014-2021 Lukas Krejci
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

import java.util.List;
import java.util.Map;

import org.revapi.Difference;
import org.revapi.Report;

/**
 * A base class to {@link org.revapi.maven.BuildTimeReporter.SuggestionsBuilder}s that abstracts the walking of the
 * differences and lets the subclasses only implement simple methods to output the suggestions in the given format.
 */
public abstract class AbstractSuggestionsBuilder implements BuildTimeReporter.SuggestionsBuilder {

    protected abstract void appendDifferenceField(StringBuilder sb, String key, Object value);

    protected abstract void appendDifferenceFieldSeparator(StringBuilder sb);

    protected abstract void prologue(StringBuilder sb);

    protected abstract void startDifference(StringBuilder sb);

    protected abstract void endDifference(StringBuilder sb);

    protected abstract void startOptionalAttachmentsInComment(StringBuilder sb, String text);

    protected abstract void endOptionalAttachmentsInComment(StringBuilder sb);

    protected abstract void epilogue(StringBuilder sb);

    @Override
    public String build(List<Report> allProblems, BuildTimeReporter.SuggestionBuilderContext context) {
        StringBuilder sb = new StringBuilder();

        prologue(sb);

        for (Report r : allProblems) {
            for (Difference d : r.getDifferences()) {
                if (!context.isReportable(d)) {
                    continue;
                }

                startDifference(sb);

                appendDifferenceField(sb, "ignore", true);
                appendDifferenceFieldSeparator(sb);
                appendDifferenceField(sb, "code", d.code);
                appendDifferenceFieldSeparator(sb);

                if (r.getOldElement() != null) {
                    appendDifferenceField(sb, "old", r.getOldElement().getFullHumanReadableString());
                    appendDifferenceFieldSeparator(sb);
                }
                if (r.getNewElement() != null) {
                    appendDifferenceField(sb, "new", r.getNewElement().getFullHumanReadableString());
                    appendDifferenceFieldSeparator(sb);
                }

                boolean hasOptionalAttachments = false;
                for (Map.Entry<String, String> e : d.attachments.entrySet()) {
                    if (d.isIdentifyingAttachment(e.getKey())) {
                        appendDifferenceField(sb, e.getKey(), e.getValue());
                        appendDifferenceFieldSeparator(sb);
                    } else {
                        hasOptionalAttachments = true;
                    }
                }

                appendDifferenceField(sb, "justification", "ADD YOUR EXPLANATION FOR THE NECESSITY OF THIS CHANGE");

                if (context.isAttachmentsReported() && hasOptionalAttachments) {
                    startOptionalAttachmentsInComment(sb, "\n  Additionally, the following attachments can be used to further identify the difference:\n\n");
                    for (Map.Entry<String, String> e : d.attachments.entrySet()) {
                        if (!d.isIdentifyingAttachment(e.getKey())) {
                            appendDifferenceField(sb, e.getKey(), e.getValue());
                            appendDifferenceFieldSeparator(sb);
                        }
                    }
                    endOptionalAttachmentsInComment(sb);
                }

                endDifference(sb);
            }
        }

        epilogue(sb);

        return sb.toString();
    }
}
