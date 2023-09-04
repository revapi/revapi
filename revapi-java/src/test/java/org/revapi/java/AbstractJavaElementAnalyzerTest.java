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
package org.revapi.java;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
import javax.annotation.Nullable;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Archive;
import org.revapi.Difference;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public abstract class AbstractJavaElementAnalyzerTest {

    protected boolean containsDifference(List<Report> problems, String oldElement, String newElement,
            String differenceCode) {
        for (Report r : problems) {
            boolean oldTypeMatches = oldElement == null ? r.getOldElement() == null
                    : r.getOldElement() != null && oldElement.equals(r.getOldElement().getFullHumanReadableString());

            boolean newTypeMatches = newElement == null ? r.getNewElement() == null
                    : r.getNewElement() != null && newElement.equals(r.getNewElement().getFullHumanReadableString());

            boolean problemMatches = false;

            for (Difference p : r.getDifferences()) {
                if (differenceCode.equals(p.code)) {
                    problemMatches = true;
                    break;
                }
            }

            if (oldTypeMatches && newTypeMatches && problemMatches) {
                return true;
            }
        }

        return false;
    }

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

    public static class ProblemOccurrenceReporter implements Reporter {
        private static final Logger LOG = LoggerFactory.getLogger("Problem");

        private final Map<String, Integer> problemCounters;

        public ProblemOccurrenceReporter() {
            this.problemCounters = new HashMap<>();
        }

        @Nullable
        @Override
        public String getExtensionId() {
            return "problemOccurrenceReporter";
        }

        @Nullable
        @Override
        public Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext properties) {
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

                String oldE = report.getOldElement() == null ? "<none>"
                        : report.getOldElement().getFullHumanReadableString();

                String newE = report.getNewElement() == null ? "<none>"
                        : report.getNewElement().getFullHumanReadableString();

                LOG.info("[" + d.code + "] old: " + oldE + ", new: " + newE + ", " + d.classification + ", "
                        + d.description);
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

    protected Revapi createRevapi(Class<? extends Reporter> reporterType) {
        return Revapi.builder().withAnalyzers(JavaApiAnalyzer.class).withReporters(reporterType)
                .withTransformsFromThreadContextClassLoader().withFiltersFromThreadContextClassLoader()
                .withMatchersFromThreadContextClassLoader().build();
    }

    protected <R extends Reporter> R runAnalysis(Class<R> reporterType, String v1Source, String v2Source)
            throws Exception {
        return runAnalysis(reporterType, null, v1Source, v2Source);
    }

    protected <R extends Reporter> R runAnalysis(Class<R> reporterType, String configJSON, String v1Source,
            String v2Source) throws Exception {
        String[] v1 = v1Source == null ? new String[0] : new String[] { v1Source };
        String[] v2 = v2Source == null ? new String[0] : new String[] { v2Source };
        return runAnalysis(reporterType, configJSON, v1, v2);
    }

    protected <R extends Reporter> R runAnalysis(Class<R> reporterType, String[] v1Source, String[] v2Source)
            throws Exception {
        return runAnalysis(reporterType, null, v1Source, v2Source);
    }

    protected <R extends Reporter> R runAnalysis(Class<R> reporterType, String configurationJSON, String[] v1Source,
            String[] v2Source) throws Exception {
        boolean doV1 = v1Source != null && v1Source.length > 0;
        boolean doV2 = v2Source != null && v2Source.length > 0;

        ArchiveAndCompilationPath v1Archive = doV1 ? createCompiledJar("v1", v1Source)
                : new ArchiveAndCompilationPath(null, null);

        ArchiveAndCompilationPath v2Archive = doV2 ? createCompiledJar("v2", v2Source)
                : new ArchiveAndCompilationPath(null, null);

        Revapi revapi = createRevapi(reporterType);

        AnalysisContext.Builder bld = AnalysisContext.builder(revapi)
                .withOldAPI(doV1 ? API.of(new ShrinkwrapArchive(v1Archive.archive)).build() : API.builder().build())
                .withNewAPI(doV2 ? API.of(new ShrinkwrapArchive(v2Archive.archive)).build() : API.builder().build());

        if (configurationJSON != null) {
            bld.withConfigurationFromJSON(configurationJSON);
        }

        AnalysisContext ctx = bld.build();

        revapi.validateConfiguration(ctx);

        try (AnalysisResult result = revapi.analyze(ctx)) {
            result.throwIfFailed();
            return result.getExtensions().getFirstExtension(reporterType, null);
        } finally {
            if (doV1) {
                deleteDir(v1Archive.compilationPath);
            }

            if (doV2) {
                deleteDir(v2Archive.compilationPath);
            }
        }
    }

    protected static void deleteDir(final Path path) throws IOException {
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

    public static class CollectingReporter implements Reporter {
        private final List<Report> reports = new ArrayList<>();

        public List<Report> getReports() {
            return reports;
        }

        @Nullable
        @Override
        public String getExtensionId() {
            return "collectingReporter";
        }

        @Nullable
        @Override
        public Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext properties) {
        }

        @Override
        public void report(@Nonnull Report report) {
            if (!report.getDifferences().isEmpty()) {
                reports.add(report);
            }
        }

        @Override
        public void close() throws IOException {
        }
    }
}
