/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.reporter.text;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.PipelineConfiguration;
import org.revapi.Report;
import org.revapi.Revapi;
import org.revapi.base.BaseElement;
import org.revapi.base.FileArchive;

/**
 * @author Lukas Krejci
 *
 * @since 0.5.0
 */
public class TextReporterTest {

    @Test
    public void testDefaultTemplate() throws Exception {
        TextReporter reporter = new TextReporter();

        Revapi r = new Revapi(PipelineConfiguration.builder().withReporters(TextReporter.class).build());

        AnalysisContext ctx = AnalysisContext.builder(r)
                .withOldAPI(API.of(new FileArchive(new File("old-dummy.archive"))).build())
                .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build()).build();

        AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(TextReporter.class);
        assertNotNull(reporterCtx);

        reporter.initialize(reporterCtx);

        buildReports().forEach(reporter::report);

        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);

        reporter.setOutput(wrt);

        reporter.close();

        String expected = "Analysis results\n" + "----------------\n" + "\n" + "Old API: old-dummy.archive\n"
                + "New API: new-dummy.archive\n" + "old: old1\n" + "new: new1\n" + "code1: descr1\n"
                + "SOURCE: BREAKING\n" + "\n" + "old: old2\n" + "new: new2\n" + "code2\n" + "justified\n"
                + "BINARY: BREAKING\n\n";

        assertEquals(expected, out.toString());
    }

    @Test
    public void testCustomTemplate() throws Exception {
        Path tempFile = Files.createTempFile(new File(".").toPath(), "text-report-test", ".ftl");
        try {
            Files.copy(getClass().getResourceAsStream("/custom-template.ftl"), tempFile,
                    StandardCopyOption.REPLACE_EXISTING);

            TextReporter reporter = new TextReporter();

            Revapi r = new Revapi(PipelineConfiguration.builder().withReporters(TextReporter.class).build());

            AnalysisContext ctx = AnalysisContext.builder(r)
                    .withConfigurationFromJSON(
                            "{\"revapi\": {\"reporter\": {\"text\": {\"template\": \"" + tempFile.toString() + "\"}}}}")
                    .withOldAPI(API.of(new FileArchive(new File("old-dummy.archive"))).build())
                    .withNewAPI(API.of(new FileArchive(new File("new-dummy.archive"))).build()).build();

            AnalysisContext reporterCtx = r.prepareAnalysis(ctx).getFirstConfigurationOrNull(TextReporter.class);
            assertNotNull(reporterCtx);

            reporter.initialize(reporterCtx);

            buildReports().forEach(reporter::report);

            StringWriter out = new StringWriter();
            PrintWriter wrt = new PrintWriter(out);

            reporter.setOutput(wrt);

            reporter.close();

            String expected = "old1 VS new1\nold2 VS new2\n";

            assertEquals(expected, out.toString());
        } finally {
            Files.delete(tempFile);
        }
    }

    private List<Report> buildReports() {
        List<Report> ret = new ArrayList<>();

        Report report = Report.builder().withOld(new DummyElement("old2")).withNew(new DummyElement("new2"))
                .addDifference().withCode("code2").withName("name2").withJustification("justified")
                .addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING).done().build();

        ret.add(report);

        report = Report.builder().withOld(new DummyElement("old1")).withNew(new DummyElement("new1")).addDifference()
                .withCode("code1").withDescription("descr1").withName("name1")
                .addClassification(CompatibilityType.SOURCE, DifferenceSeverity.BREAKING).done().build();

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
