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
package org.revapi.reporter.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.PipelineConfiguration;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.base.BaseElement;
import org.revapi.base.FileArchive;
import org.revapi.configuration.JSONUtil;

public class JsonReporterTest {

    @Test
    public void testKeepEmptyFile() throws Exception {
        Path file = Files.createTempFile(null, null);
        try (Reporter reporter = new JsonReporter()) {
            StringBuffer buf = new StringBuffer();
            buf.append("{");
            buf.append("\"output\": \"").append(file.toString()).append("\"");
            buf.append(",");
            buf.append("\"keepEmptyFile\": \"").append("false").append("\"");
            buf.append("}");

            reporter.initialize(
                    AnalysisContext.builder().build().copyWithConfiguration(JSONUtil.parse(buf.toString())));
            reporter.report(Report.builder().build());

        } finally {
            assertFalse(file.toFile().exists());
        }
    }

    @Test
    public void testReportsWritten() throws Exception {
        JsonReporter reporter = new JsonReporter();

        Revapi r = new Revapi(PipelineConfiguration.builder().withReporters(JsonReporter.class).build());

        AnalysisContext ctx = AnalysisContext.builder(r)
                .withOldAPI(API.of(new FileArchive(new File("old-dummy.archive"))).build())
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build()).build();

        AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(JsonReporter.class);

        reporter.initialize(reporterCtx);

        buildReports().forEach(reporter::report);

        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);

        reporter.setOutput(wrt);

        reporter.close();

        JsonNode diffs = JSONUtil.parse(out.toString());

        assertEquals(2, diffs.size());
        JsonNode diff = diffs.get(0);
        assertEquals("code1", diff.get("code").asText());
        assertEquals("old1", diff.get("old").asText());
        assertEquals("new1", diff.get("new").asText());
        JsonNode classifications = diff.get("classification");
        assertEquals(1, classifications.size());
        JsonNode classification = classifications.get(0);
        assertEquals("SOURCE", classification.get("compatibility").asText());
        assertEquals("BREAKING", classification.get("severity").asText());
        JsonNode attachments = diff.get("attachments");
        JsonNode attachment = attachments.get(0);
        assertEquals("at1", attachment.get("name").asText());
        assertEquals("at1val", attachment.get("value").asText());

        diff = diffs.get(1);
        assertEquals("code2", diff.get("code").asText());
        assertEquals("old2", diff.get("old").asText());
        assertEquals("new2", diff.get("new").asText());
        classifications = diff.get("classification");
        assertEquals(1, classifications.size());
        classification = classifications.get(0);
        assertEquals("BINARY", classification.get("compatibility").asText());
        assertEquals("BREAKING", classification.get("severity").asText());
        attachments = diff.get("attachments");
        attachment = attachments.get(0);
        assertEquals("at2", attachment.get("name").asText());
        assertEquals("at2val", attachment.get("value").asText());
    }

    @Test
    public void testIndentationApplied() throws Exception {
        JsonReporter reporter = new JsonReporter();

        Revapi r = new Revapi(PipelineConfiguration.builder().withReporters(JsonReporter.class).build());

        AnalysisContext ctx = AnalysisContext.builder(r)
                .withOldAPI(API.of(new FileArchive(new File("old-dummy.archive"))).build())
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build()).build();

        AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(JsonReporter.class);

        reporter.initialize(reporterCtx.copyWithConfiguration(JSONUtil.parse("{\"indent\": true}")));

        buildReports().forEach(reporter::report);

        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);

        reporter.setOutput(wrt);

        reporter.close();

        assertNotEquals(-1, out.toString().indexOf('\n'));
    }

    @Test
    public void testIndentationNotAppliedByDefault() throws Exception {
        JsonReporter reporter = new JsonReporter();

        Revapi r = new Revapi(PipelineConfiguration.builder().withReporters(JsonReporter.class).build());

        AnalysisContext ctx = AnalysisContext.builder(r)
                .withOldAPI(API.of(new FileArchive(new File("old-dummy.archive"))).build())
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build()).build();

        AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(JsonReporter.class);

        reporter.initialize(reporterCtx);

        buildReports().forEach(reporter::report);

        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);

        reporter.setOutput(wrt);

        reporter.close();

        assertEquals(-1, out.toString().indexOf('\n'));
    }

    private List<Report> buildReports() {
        List<Report> ret = new ArrayList<>();

        Report report = Report.builder().withOld(new DummyElement("old2")).withNew(new DummyElement("new2"))
                .addDifference().withCode("code2").withDescription("descr2").withName("name2")
                .addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING).addAttachment("at2", "at2val")
                .done().build();

        ret.add(report);

        report = Report.builder().withOld(new DummyElement("old1")).withNew(new DummyElement("new1")).addDifference()
                .withCode("code1").withDescription("descr1").withName("name1")
                .addClassification(CompatibilityType.SOURCE, DifferenceSeverity.BREAKING).addAttachment("at1", "at1val")
                .done().build();

        ret.add(report);

        return ret;
    }

    private static final class DummyElement extends BaseElement<DummyElement> {

        private final String name;

        private DummyElement(String name) {
            super(null, null);
            this.name = name;
        }

        @Override
        public int compareTo(DummyElement o) {
            return name.compareTo(o.name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
