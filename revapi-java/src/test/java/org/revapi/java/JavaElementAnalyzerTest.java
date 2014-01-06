/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.Assert;
import org.junit.Test;

import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.revapi.Configuration;
import org.revapi.MatchReport;
import org.revapi.ProblemTransform;
import org.revapi.Reporter;
import org.revapi.core.Revapi;
import org.revapi.java.checks.Code;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class JavaElementAnalyzerTest {
    private static class SourceInClassLoader extends SimpleJavaFileObject {
        URL url;

        private SourceInClassLoader(String path) {
            super(getName(path), Kind.SOURCE);
            url = getClass().getClassLoader().getResource(path);
        }

        private static URI getName(String path) {
            return URI.create(path.substring(path.lastIndexOf('/') + 1));
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            StringBuilder bld = new StringBuilder();

            Reader rdr = openReader(ignoreEncodingErrors);
            char[] buffer = new char[512]; //our source files are small

            for (int cnt; (cnt = rdr.read(buffer)) != -1; ) {
                bld.append(buffer, 0, cnt);
            }

            rdr.close();

            return bld;
        }

        @Override
        public NestingKind getNestingKind() {
            return NestingKind.TOP_LEVEL;
        }

        @Override
        public InputStream openInputStream() throws IOException {
            return url.openStream();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return new InputStreamReader(openInputStream(), "UTF-8");
        }
    }

    private static class ShrinkwrapArchive implements Archive {
        private final JavaArchive archive;

        private ShrinkwrapArchive(JavaArchive archive) {
            this.archive = archive;
        }

        @Override
        public String getName() {
            return archive.getName();
        }

        @Override
        public InputStream openStream() throws IOException {
            return archive.as(ZipExporter.class).exportAsInputStream();
        }
    }

    private static class ProblemOccurrenceReporter implements Reporter {
        private final Map<String, Integer> problemCounters;

        private ProblemOccurrenceReporter() {
            this.problemCounters = new HashMap<>();
        }

        @Override
        public void initialize(Configuration properties) {
        }

        @Override
        public void report(MatchReport matchReport, PrintStream output) {
            for (MatchReport.Problem p : matchReport.getProblems()) {
                Integer cnt = problemCounters.get(p.code);
                if (cnt == null) {
                    cnt = 1;
                } else {
                    cnt += 1;
                }
                problemCounters.put(p.code, cnt);
            }
        }

        public Map<String, Integer> getProblemCounters() {
            return problemCounters;
        }
    }

    private JavaArchive createCompiledJar(String jarName, String... sourceFiles) throws Exception {
        File targetPath = Files.createTempDirectory("element-analyzer-test").toAbsolutePath().toFile();

        List<String> options = Arrays.asList("-d", targetPath.getAbsolutePath());
        List<JavaFileObject> sources = new ArrayList<>();
        for (String source : sourceFiles) {
            sources.add(new SourceInClassLoader(source));
        }

        if (!ToolProvider.getSystemJavaCompiler().getTask(null, null, null, options, null, sources).call()) {
            throw new IllegalStateException("Failed to compile source files: " + sourceFiles);
        }

        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, jarName + ".jar");
        for (File f : targetPath.listFiles()) {
            archive.addAsResource(f);
        }

        return archive;
    }

    private Revapi getRevapi(Reporter testReporter, String v1Source, String v2Source) throws Exception {
        JavaArchive v1Archive = createCompiledJar("v1", v1Source);
        JavaArchive v2Archive = createCompiledJar("v2", v2Source);
        return new Revapi(Collections.<ApiAnalyzer>singleton(new JavaApiAnalyzer()),
            Collections.singleton(testReporter),
            Collections.<ProblemTransform>emptySet(),
            Locale.getDefault(),
            Collections.<String, String>emptyMap(),
            new PrintStream(System.err),
            Arrays.<Archive>asList(new ShrinkwrapArchive(v1Archive)),
            Arrays.<Archive>asList(new ShrinkwrapArchive(v2Archive))
        );
    }

    @Test
    public void testVisibilityReduced() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        getRevapi(reporter, "v1/VisibilityReduced.java", "v2/VisibilityReduced.java").analyze();

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_VISIBILITY_REDUCED.code()));
    }

    @Test
    public void testVisibilityIncreased() throws Exception {
        ProblemOccurrenceReporter reporter = new ProblemOccurrenceReporter();
        getRevapi(reporter, "v2/VisibilityReduced.java", "v1/VisibilityReduced.java").analyze();

        Assert.assertEquals(1, (int) reporter.getProblemCounters().get(Code.CLASS_VISIBILITY_INCREASED.code()));
    }

    //TODO add tests for the rest of the checks
}
