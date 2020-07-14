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
package org.revapi.reporter.file;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.revapi.AnalysisContext;
import org.revapi.Report;

public class AbstractFileReporterTest {

    @Test
    public void testStdOutIsDefaultOutput() {
        Reporter reporter = new Reporter();
        reporter.initialize(ctxWithOutput(null));

        assertSame(System.out, reporter.origStream);
    }

    @Test
    public void testStdOutAsOutput() {
        Reporter reporter = new Reporter();
        reporter.initialize(ctxWithOutput("out"));

        assertSame(System.out, reporter.origStream);
    }

    @Test
    public void testStdErrAsOutput() {
        Reporter reporter = new Reporter();
        reporter.initialize(ctxWithOutput("err"));

        assertSame(System.err, reporter.origStream);
    }

    @Test
    public void testExistingFileAsOutput() throws Exception {
        Path file = Files.createTempFile(null, null);
        try (Reporter reporter = new Reporter()) {
            reporter.initialize(ctxWithOutput(file.toString()));
        } finally {
            assertReporterWroteToPath(file);
            Files.delete(file);
        }
    }

    @Test
    public void testNonExistingFileAsOutput() throws Exception {
        Path file = Files.createTempDirectory(null);
        file = file.resolve("output.file");
        try (Reporter reporter = new Reporter()) {
            reporter.initialize(ctxWithOutput(file.toString()));
        } finally {
            assertReporterWroteToPath(file);
            Files.delete(file);
            Files.delete(file.getParent());
        }
    }

    @Test
    public void testNonExistingRelativeFileAsOutput() throws Exception {
        Path file = Paths.get("fqefacs" + new Random().nextInt());
        try (Reporter reporter = new Reporter()) {
            reporter.initialize(ctxWithOutput(file.toString()));
        } finally {
            assertReporterWroteToPath(file);
            Files.delete(file);
        }
    }

    @Test
    public void testNestedNonExistingFileAsOutput() throws Exception {
        Path file = Files.createTempDirectory(null);
        file = file.resolve("subdir" + File.separatorChar + "output.file");
        try (Reporter reporter = new Reporter()) {
            reporter.initialize(ctxWithOutput(file.toString()));
        } finally {
            assertReporterWroteToPath(file);
            Files.delete(file);
            Files.delete(file.getParent());
            Files.delete(file.getParent().getParent());
        }
    }
    @Test
    public void testNestedNonExistingRelativeFileAsOutput() throws Exception {
        Path file = Paths.get("subdir" + new Random().nextInt() + File.separatorChar + "output.file");
        try (Reporter reporter = new Reporter()) {
            reporter.initialize(ctxWithOutput(file.toString()));
        } finally {
            assertReporterWroteToPath(file);
            Files.delete(file);
            Files.delete(file.getParent());
        }
    }

    private void assertReporterWroteToPath(Path path) throws IOException {
        String line = Files.lines(path).toArray(String[]::new)[0];
        assertEquals("Test was here!", line);
    }

    private AnalysisContext ctxWithOutput(String output) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (output != null) {
            sb.append("\"output\": \"").append(output).append("\"");
        }
        sb.append("}");
        return AnalysisContext.builder()
                .build().copyWithConfiguration(ModelNode.fromJSONString(sb.toString()));
    }

    static final class Reporter extends AbstractFileReporter {
        OutputStream origStream;

        @Override
        protected void flushReports() throws IOException {
            output.print("Test was here!");
            output.flush();
        }

        @Override
        protected void doReport(Report report) {

        }

        @Override
        public String getExtensionId() {
            return "test-reporter";
        }

        @Nullable
        @Override
        public Reader getJSONSchema() {
            return new InputStreamReader(getClass().getResourceAsStream("default-schema.json"), StandardCharsets.UTF_8);
        }

        @Override
        protected PrintWriter createOutputWriter(OutputStream stream, AnalysisContext ctx) {
            this.origStream = stream;
            return super.createOutputWriter(stream, ctx);
        }
    }
}
