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
package org.revapi.reporter.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.PipelineConfiguration;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.simple.FileArchive;
import org.revapi.simple.SimpleElement;

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

            reporter.initialize(AnalysisContext.builder()
                    .build().copyWithConfiguration(ModelNode.fromJSONString(buf.toString())));
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
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build())
                .build();

        AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(JsonReporter.class);

        reporter.initialize(reporterCtx);

        buildReports().forEach(reporter::report);

        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);

        reporter.setOutput(wrt);

        reporter.close();

        JsonReader reader = Json.createReader(new StringReader(out.toString()));
        JsonArray diffs = reader.readArray();

        assertEquals(2, diffs.size());
        JsonObject diff = diffs.getJsonObject(0);
        assertEquals("code1", diff.getString("code"));
        assertEquals("old1", diff.getString("old"));
        assertEquals("new1", diff.getString("new"));
        JsonArray classifications = diff.getJsonArray("classification");
        assertEquals(1, classifications.size());
        JsonObject classification = classifications.getJsonObject(0);
        assertEquals("SOURCE", classification.getString("compatibility"));
        assertEquals("BREAKING", classification.getString("severity"));
        JsonArray attachments = diff.getJsonArray("attachments");
        JsonObject attachment = attachments.getJsonObject(0);
        assertEquals("at1", attachment.getString("name"));
        assertEquals("at1val", attachment.getString("value"));

        diff = diffs.getJsonObject(1);
        assertEquals("code2", diff.getString("code"));
        assertEquals("old2", diff.getString("old"));
        assertEquals("new2", diff.getString("new"));
        classifications = diff.getJsonArray("classification");
        assertEquals(1, classifications.size());
        classification = classifications.getJsonObject(0);
        assertEquals("BINARY", classification.getString("compatibility"));
        assertEquals("BREAKING", classification.getString("severity"));
        attachments = diff.getJsonArray("attachments");
        attachment = attachments.getJsonObject(0);
        assertEquals("at2", attachment.getString("name"));
        assertEquals("at2val", attachment.getString("value"));
    }

    @Test
    public void testIndentationApplied() throws Exception {
        JsonReporter reporter = new JsonReporter();

        Revapi r = new Revapi(PipelineConfiguration.builder().withReporters(JsonReporter.class).build());

        AnalysisContext ctx = AnalysisContext.builder(r)
                .withOldAPI(API.of(new FileArchive(new File("old-dummy.archive"))).build())
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build())
                .build();

        AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(JsonReporter.class);
        reporterCtx.getConfiguration().get("indent").set(true);

        reporter.initialize(reporterCtx);

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
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build())
                .build();

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
                .addProblem().withCode("code2").withDescription("descr2").withName("name2")
                .addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                .addAttachment("at2", "at2val").done().build();

        ret.add(report);

        report = Report.builder().withOld(new DummyElement("old1")).withNew(new DummyElement("new1")).addProblem()
                .withCode("code1").withDescription("descr1").withName("name1")
                .addClassification(CompatibilityType.SOURCE, DifferenceSeverity.BREAKING)
                .addAttachment("at1", "at1val").done().build();

        ret.add(report);

        return ret;
    }

    private static final class DummyElement extends SimpleElement {

        private final String name;

        private DummyElement(String name) {
            this.name = name;
        }

        @SuppressWarnings("ConstantConditions")
        @Nonnull
        @Override
        public API getApi() {
            return null;
        }

        @Nullable
        @Override
        public Archive getArchive() {
            return null;
        }

        @Override
        public int compareTo(Element o) {
            return name.compareTo(((DummyElement) o).name);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
