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
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import javax.lang.model.element.NestingKind;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.ApiAnalyzer;
import org.revapi.Revapi;
import org.revapi.Archive;
import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.MatchReport;
import org.revapi.ProblemTransform;
import org.revapi.Reporter;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class AbstractJavaElementAnalyzerTest {
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

    protected static class ProblemOccurrenceReporter implements Reporter {
        private static final Logger LOG = LoggerFactory.getLogger("Problem");

        private final Map<String, Integer> problemCounters;

        public ProblemOccurrenceReporter() {
            this.problemCounters = new HashMap<>();
        }

        @Override
        public void initialize(Configuration properties) {
        }

        @Override
        public void report(MatchReport matchReport) {
            for (MatchReport.Problem p : matchReport.getProblems()) {
                Integer cnt = problemCounters.get(p.code);
                if (cnt == null) {
                    cnt = 1;
                } else {
                    cnt += 1;
                }
                problemCounters.put(p.code, cnt);

                Element e = matchReport.getNewElement();
                if (e == null) {
                    e = matchReport.getOldElement();
                }

                LOG.info(e + ": " + p.name + " (" + p.code + "): " + p.classification + ", " + p.description);
            }
        }

        public Map<String, Integer> getProblemCounters() {
            return problemCounters;
        }
    }

    private static class ArchiveAndCompilationPath {
        final JavaArchive archive;
        final Path compilationPath;

        private ArchiveAndCompilationPath(JavaArchive archive, Path compilationPath) {
            this.archive = archive;
            this.compilationPath = compilationPath;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger("TestWatch");

    @Rule
    public final TestRule watcher = new TestWatcher() {
        @Override
        protected void starting(Description description) {
            LOG.info(description.getDisplayName() + " starts");
        }

        @Override
        protected void finished(Description description) {
            LOG.info(description.getDisplayName() + " finished");
        }
    };

    private ArchiveAndCompilationPath createCompiledJar(String jarName, String... sourceFiles) throws Exception {
        File targetPath = Files.createTempDirectory("element-analyzer-test-" + jarName + ".jar-").toAbsolutePath()
            .toFile();

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

        return new ArchiveAndCompilationPath(archive, targetPath.toPath());
    }

    protected void runAnalysis(Reporter testReporter, String v1Source, String v2Source) throws Exception {
        runAnalysis(testReporter, new String[]{v1Source}, new String[]{v2Source});
    }

    protected void runAnalysis(Reporter testReporter, String[] v1Source, String[] v2Source) throws Exception {
        ArchiveAndCompilationPath v1Archive = createCompiledJar("v1", v1Source);
        ArchiveAndCompilationPath v2Archive = createCompiledJar("v2", v2Source);

        Set<ProblemTransform> transforms = new HashSet<>();
        for (ProblemTransform pt : ServiceLoader.load(ProblemTransform.class)) {
            transforms.add(pt);
        }

        Revapi revapi =
            new Revapi(Collections.<ApiAnalyzer>singleton(new JavaApiAnalyzer()),
                Collections.singleton(testReporter),
                transforms,
                Locale.getDefault(),
                Collections.<String, String>emptyMap()
            );

        revapi.analyze(Arrays.asList(new ShrinkwrapArchive(v1Archive.archive)), null,
            Arrays.asList(new ShrinkwrapArchive(v2Archive.archive)), null);

        deleteDir(v1Archive.compilationPath);
        deleteDir(v2Archive.compilationPath);
    }

    private void deleteDir(final Path path) throws IOException {
        try {
            Files.walkFileTree(path, new FileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                    if (path.equals(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    throw new IOException("Failed to delete file '" + file + "'.", exc);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to remove compiled results", e);
        }
    }
}
