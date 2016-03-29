/*
 * Copyright 2016 Lukas Krejci
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
 *
 */

package org.revapi.reporter.text;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
 * @since 0.5.0
 */
public class TextReporterTest {

    @Test
    public void testDefaultTemplate() throws Exception {
        TextReporter reporter = new TextReporter();

        AnalysisContext ctx = AnalysisContext.builder().build();

        reporter.initialize(ctx);

        buildReports().forEach(reporter::report);

        StringWriter out = new StringWriter();
        PrintWriter wrt = new PrintWriter(out);

        reporter.setOutput(wrt);

        reporter.close();

        String expected = "old: old1\n" +
                "new: new1\n" +
                "code1: descr1\n" +
                "SOURCE: BREAKING\n" +
                "\n" +
                "old: old2\n" +
                "new: new2\n" +
                "code2: descr2\n" +
                "BINARY: BREAKING\n";

        Assert.assertEquals(expected, out.toString());
    }

    @Test
    public void testCustomTemplate() throws Exception {
        Path tempFile = Files.createTempFile(new File(".").toPath(), "text-report-test", ".ftl");
        try {
            Files.copy(getClass().getResourceAsStream("/custom-template.ftl"), tempFile,
                    StandardCopyOption.REPLACE_EXISTING);

            TextReporter reporter = new TextReporter();

            AnalysisContext ctx = AnalysisContext.builder()
                    .withConfigurationFromJSON("{\"revapi\": {\"reporter\": {\"text\": {\"template\": \""
                            + tempFile.toString() + "\"}}}}")
                    .build();

            reporter.initialize(ctx);

            buildReports().forEach(reporter::report);

            StringWriter out = new StringWriter();
            PrintWriter wrt = new PrintWriter(out);

            reporter.setOutput(wrt);

            reporter.close();

            String expected = "old1 VS new1\nold2 VS new2\n";

            Assert.assertEquals(expected, out.toString());
        } finally {
            Files.delete(tempFile);
        }
    }

    private List<Report> buildReports() {
        List<Report> ret = new ArrayList<>();

        Report report = Report.builder().withOld(new DummyElement("old2")).withNew(new DummyElement("new2"))
                .addProblem().withCode("code2").withDescription("descr2").withName("name2").addClassification(
                        CompatibilityType.BINARY, DifferenceSeverity.BREAKING).done().build();

        ret.add(report);

        report = Report.builder().withOld(new DummyElement("old1")).withNew(new DummyElement("new1"))
                .addProblem().withCode("code1").withDescription("descr1").withName("name1").addClassification(
                        CompatibilityType.SOURCE, DifferenceSeverity.BREAKING).done().build();

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
