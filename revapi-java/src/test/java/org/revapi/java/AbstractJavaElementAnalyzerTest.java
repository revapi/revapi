/*
 * Copyright 2014 Lukas Krejci
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
 */

package org.revapi.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.Archive;
import org.revapi.Configuration;
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class AbstractJavaElementAnalyzerTest {

    protected final static class ShrinkwrapArchive implements Archive {
        private final JavaArchive archive;

        protected ShrinkwrapArchive(JavaArchive archive) {
            this.archive = archive;
        }

        @Nonnull
        @Override
        public String getName() {
            return archive.getName();
        }

        @Nonnull
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
        public void initialize(@Nonnull Configuration properties) {
        }

        @Override
        public void report(@Nonnull Report report) {
            for (Difference d : report.getDifferences()) {
                Integer cnt = problemCounters.get(d.code);
                if (cnt == null) {
                    cnt = 1;
                } else {
                    cnt += 1;
                }
                problemCounters.put(d.code, cnt);

                Element e = report.getNewElement();
                if (e == null) {
                    e = report.getOldElement();
                }

                LOG.info(
                    (e == null ? "null" : e.getFullHumanReadableString()) + ": " + d.name + " (" + d.code + "): " +
                        d.classification + ", " +
                        d.description);
            }
        }

        @Override
        public void close() throws IOException {
        }

        public Map<String, Integer> getProblemCounters() {
            return problemCounters;
        }
    }

    protected final static class ArchiveAndCompilationPath {
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

    @SuppressWarnings("ConstantConditions")
    protected ArchiveAndCompilationPath createCompiledJar(String jarName, String... sourceFiles) throws Exception {
        File targetPath = Files.createTempDirectory("element-analyzer-test-" + jarName + ".jar-").toAbsolutePath()
            .toFile();

        List<String> options = Arrays.asList("-d", targetPath.getAbsolutePath());
        List<JavaFileObject> sources = new ArrayList<>();
        for (String source : sourceFiles) {
            sources.add(new SourceInClassLoader(source));
        }

        if (!ToolProvider.getSystemJavaCompiler().getTask(null, null, null, options, null, sources).call()) {
            throw new IllegalStateException("Failed to compile source files: " + Arrays.asList(sourceFiles));
        }

        JavaArchive archive = ShrinkWrap.create(JavaArchive.class, jarName + ".jar");
        for (File f : targetPath.listFiles()) {
            archive.addAsResource(f);
        }

        return new ArchiveAndCompilationPath(archive, targetPath.toPath());
    }

    protected Revapi createRevapi(Reporter testReporter) {
        return Revapi.builder().withAnalyzers(new JavaApiAnalyzer()).withReporters(testReporter)
            .withTransformsFromThreadContextClassLoader().withFiltersFromThreadContextClassLoader().build();
    }

    protected void runAnalysis(Reporter testReporter, String v1Source, String v2Source) throws Exception {
        runAnalysis(testReporter, new String[]{v1Source}, new String[]{v2Source});
    }

    protected void runAnalysis(Reporter testReporter, String[] v1Source, String[] v2Source) throws Exception {
        ArchiveAndCompilationPath v1Archive = createCompiledJar("v1", v1Source);
        ArchiveAndCompilationPath v2Archive = createCompiledJar("v2", v2Source);

        Revapi revapi = createRevapi(testReporter);

        revapi.analyze(Arrays.asList(new ShrinkwrapArchive(v1Archive.archive)), null,
            Arrays.asList(new ShrinkwrapArchive(v2Archive.archive)), null);

        deleteDir(v1Archive.compilationPath);
        deleteDir(v2Archive.compilationPath);
    }

    protected void deleteDir(final Path path) throws IOException {
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
