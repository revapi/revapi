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

import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.9.0
 */
public class BuildTimeReporterTest {

    @Test
    public void testJSONEscapedInIgnoreHint() {
        BuildTimeReporter reporter = new BuildTimeReporter();

        API oldApi = API.builder().build();
        API newApi = API.builder().build();

        AnalysisContext ctx = AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi)
                .withData(BuildTimeReporter.BREAKING_SEVERITY_KEY, DifferenceSeverity.EQUIVALENT)
                .withData(BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, new JsonSuggestionsBuilder())
                .build();

        reporter.initialize(ctx);

        Element oldEl = new SimpleElement() {
            @Nonnull @Override public API getApi() {
                return oldApi;
            }

            @Nullable @Override public Archive getArchive() {
                return null;
            }

            @Override public int compareTo(Element o) {
                return 0;
            }

            @Override public String toString() {
                return "old element";
            }
        };

        Element newEl = new SimpleElement() {
            @Nonnull @Override public API getApi() {
                return newApi;
            }

            @Nullable @Override public Archive getArchive() {
                return null;
            }

            @Override public int compareTo(Element o) {
                return 0;
            }

            @Override public String toString() {
                return "new element";
            }
        };

        Report report = Report.builder()
                .withNew(newEl)
                .withOld(oldEl)
                .addProblem()
                /**/.withCode("diffs\\myDiff")
                /**/.withDescription("the problem")
                /**/.addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                /**/.addAttachment("shouldBeEscaped", "{\"a\", \"b\"}")
                /**/.withIdentifyingAttachments(Collections.singletonList("shouldBeEscaped"))
                .done()
                .build();

        reporter.report(report);

        String resultMessage = reporter.getIgnoreSuggestion();
        Assert.assertNotNull(resultMessage);

        int start = resultMessage.indexOf('{');
        int end = resultMessage.lastIndexOf('}');

        String json = resultMessage.substring(start, end + 1);
        json = json.replace("<<<<< ADD YOUR EXPLANATION FOR THE NECESSITY OF THIS CHANGE >>>>>", "");

        ModelNode parsed = ModelNode.fromJSONString(json);

        Assert.assertEquals("diffs\\myDiff", parsed.get("code").asString());
        Assert.assertEquals("{\"a\", \"b\"}", parsed.get("shouldBeEscaped").asString());
    }

    @Test
    public void testNonIdentifyingAttachmentsHiddenInsideComment() {
        BuildTimeReporter reporter = new BuildTimeReporter();

        API oldApi = API.builder().build();
        API newApi = API.builder().build();

        AnalysisContext ctx = AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi)
                .withData(BuildTimeReporter.BREAKING_SEVERITY_KEY, DifferenceSeverity.EQUIVALENT)
                .withData(BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, new JsonSuggestionsBuilder())
                .build();

        reporter.initialize(ctx);

        Element oldEl = new SimpleElement() {
            @Nonnull @Override public API getApi() {
                return oldApi;
            }

            @Nullable @Override public Archive getArchive() {
                return null;
            }

            @Override public int compareTo(Element o) {
                return 0;
            }

            @Override public String toString() {
                return "old element";
            }
        };

        Element newEl = new SimpleElement() {
            @Nonnull @Override public API getApi() {
                return newApi;
            }

            @Nullable @Override public Archive getArchive() {
                return null;
            }

            @Override public int compareTo(Element o) {
                return 0;
            }

            @Override public String toString() {
                return "new element";
            }
        };

        Report report = Report.builder()
                .withNew(newEl)
                .withOld(oldEl)
                .addProblem()
                /**/.withCode("diffs\\myDiff")
                /**/.withDescription("the problem")
                /**/.addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                /**/.addAttachment("nonIdentifying", "{\"a\", \"b\"}")
                .done()
                .build();

        reporter.report(report);

        String resultMessage = reporter.getIgnoreSuggestion();
        Assert.assertNotNull(resultMessage);

        int commentStart = resultMessage.indexOf("/*");
        int commentEnd = resultMessage.indexOf("*/");
        int nonIdentifyingIndex = resultMessage.indexOf("nonIdentifying");

        Assert.assertTrue(commentStart < nonIdentifyingIndex);
        Assert.assertTrue(commentEnd > nonIdentifyingIndex);
    }

    @Test
    public void testNoNonIdentifyingAttachmentsOutputIfConfigured() {
        BuildTimeReporter reporter = new BuildTimeReporter();

        API oldApi = API.builder().build();
        API newApi = API.builder().build();

        AnalysisContext ctx = AnalysisContext.builder()
                .withOldAPI(oldApi)
                .withNewAPI(newApi)
                .withData(BuildTimeReporter.BREAKING_SEVERITY_KEY, DifferenceSeverity.EQUIVALENT)
                .withData(BuildTimeReporter.OUTPUT_NON_IDENTIFYING_ATTACHMENTS, false)
                .withData(BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, new JsonSuggestionsBuilder())
                .build();

        reporter.initialize(ctx);

        Element oldEl = new SimpleElement() {
            @Nonnull @Override public API getApi() {
                return oldApi;
            }

            @Nullable @Override public Archive getArchive() {
                return null;
            }

            @Override public int compareTo(Element o) {
                return 0;
            }

            @Override public String toString() {
                return "old element";
            }
        };

        Element newEl = new SimpleElement() {
            @Nonnull @Override public API getApi() {
                return newApi;
            }

            @Nullable @Override public Archive getArchive() {
                return null;
            }

            @Override public int compareTo(Element o) {
                return 0;
            }

            @Override public String toString() {
                return "new element";
            }
        };

        Report report = Report.builder()
                .withNew(newEl)
                .withOld(oldEl)
                .addProblem()
                /**/.withCode("diffs\\myDiff")
                /**/.withDescription("the problem")
                /**/.addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                /**/.addAttachment("nonIdentifying", "{\"a\", \"b\"}")
                .done()
                .build();

        reporter.report(report);

        String resultMessage = reporter.getIgnoreSuggestion();
        Assert.assertNotNull(resultMessage);

        int commentStart = resultMessage.indexOf("/*");
        int commentEnd = resultMessage.indexOf("*/");
        int nonIdentifyingIndex = resultMessage.indexOf("nonIdentifying");

        Assert.assertEquals(-1, commentStart);
        Assert.assertEquals(-1, commentEnd);
        Assert.assertEquals(-1, nonIdentifyingIndex);
    }
}
