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

import java.util.Collections;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Criticality;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.base.BaseElement;

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
                .withData(BuildTimeReporter.BREAKING_CRITICALITY_KEY, Criticality.ALLOWED)
                .withData(BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, new JsonSuggestionsBuilder())
                .build();

        reporter.initialize(ctx);

        DummyElement oldEl = new DummyElement(oldApi);
        DummyElement newEl = new DummyElement(newApi);

        Report report = Report.builder()
                .withNew(newEl)
                .withOld(oldEl)
                .addProblem()
                /**/.withCode("diffs\\myDiff")
                /**/.withDescription("the problem")
                /**/.addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                /**/.addAttachment("shouldBeEscaped", "{\"a\", \"b\"}")
                /**/.withCriticality(Criticality.ERROR)
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
                .withData(BuildTimeReporter.BREAKING_CRITICALITY_KEY, Criticality.ALLOWED)
                .withData(BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, new JsonSuggestionsBuilder())
                .build();

        reporter.initialize(ctx);

        DummyElement oldEl = new DummyElement(oldApi);
        DummyElement newEl = new DummyElement(newApi);

        Report report = Report.builder()
                .withNew(newEl)
                .withOld(oldEl)
                .addProblem()
                /**/.withCode("diffs\\myDiff")
                /**/.withDescription("the problem")
                /**/.addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                /**/.withCriticality(Criticality.ERROR)
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
                .withData(BuildTimeReporter.BREAKING_CRITICALITY_KEY, Criticality.ALLOWED)
                .withData(BuildTimeReporter.OUTPUT_NON_IDENTIFYING_ATTACHMENTS, false)
                .withData(BuildTimeReporter.SUGGESTIONS_BUILDER_KEY, new JsonSuggestionsBuilder())
                .build();

        reporter.initialize(ctx);

        DummyElement oldEl = new DummyElement(oldApi);
        DummyElement newEl = new DummyElement(newApi);

        Report report = Report.builder()
                .withNew(newEl)
                .withOld(oldEl)
                .addProblem()
                /**/.withCode("diffs\\myDiff")
                /**/.withDescription("the problem")
                /**/.addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                /**/.addAttachment("nonIdentifying", "{\"a\", \"b\"}")
                /**/.withCriticality(Criticality.ERROR)
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

    public static final class DummyElement extends BaseElement<DummyElement> {

        public DummyElement(API api) {
            super(api);
        }

        @Override
        public int compareTo(DummyElement o) {
            return 0;
        }
    }
}
