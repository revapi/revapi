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
package org.revapi;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.base.BaseApiAnalyzer;
import org.revapi.base.BaseArchiveAnalyzer;
import org.revapi.base.BaseDifferenceAnalyzer;
import org.revapi.base.BaseElement;
import org.revapi.base.BaseElementForest;
import org.revapi.base.BaseReporter;

/**
 * @author Lukas Krejci
 * 
 * @since 0.3.3
 */
public class AnalysisTest {

    @Test
    public void testTransformCycleDetection() throws Exception {
        Revapi r = Revapi.builder().withAnalyzers(DummyAnalyzer.class).withTransforms(CloningDifferenceTransform.class)
                .withReporters(DummyReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(r).withNewAPI(API.of().build()).withOldAPI(API.of().build())
                .build();

        // should not throw exception
        try (AnalysisResult res = r.analyze(ctx)) {
            Assert.assertTrue(res.isSuccess());
        }
    }

    @Test
    public void testMultipleTransformersProcessDifferences() throws Exception {
        Revapi r = Revapi.builder().withAnalyzers(DummyAnalyzer.class)
                .withTransforms(SemVerImitationDifferenceTransform.class)
                .withTransforms(BreakingDifferenceTransform.class).withReporters(FailingReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(r).withNewAPI(API.of().build()).withOldAPI(API.of().build())
                .build();

        // should throw exception
        try (AnalysisResult res = r.analyze(ctx)) {
            Assert.assertFalse(res.isSuccess());
        }
    }

    public static final class DummyElement extends BaseElement<DummyElement> {
        public DummyElement(API api, @Nullable Archive archive) {
            super(api, archive);
        }

        @Override
        public int compareTo(DummyElement o) {
            return 0;
        }
    }

    public static final class SemVerImitationDifferenceTransform implements DifferenceTransform<DummyElement> {

        @Override
        public @Nonnull Pattern[] getDifferenceCodePatterns() {
            return new Pattern[] { Pattern.compile("code") };
        }

        @Override
        public @Nullable Difference transform(@Nullable DummyElement oldElement, @Nullable DummyElement newElement,
                @Nonnull Difference d) {
            if (d.classification.get(CompatibilityType.BINARY) != null) {
                return d;
            }
            return null;
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public String getExtensionId() {
            return "fake-semver";
        }

        @Override
        public @Nullable Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class BreakingDifferenceTransform implements DifferenceTransform<DummyElement> {

        @Override
        public @Nonnull Pattern[] getDifferenceCodePatterns() {
            return new Pattern[] { Pattern.compile("code") };
        }

        @Override
        public @Nullable Difference transform(@Nullable DummyElement oldElement, @Nullable DummyElement newElement,
                @Nonnull Difference d) {
            return Difference.builder().withCode(d.code).withName(d.name).withDescription(d.description)
                    .addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING).build();
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public String getExtensionId() {
            return "breaking-transform";
        }

        @Override
        public @Nullable Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class CloningDifferenceTransform implements DifferenceTransform<DummyElement> {

        @Override
        public @Nonnull Pattern[] getDifferenceCodePatterns() {
            return new Pattern[] { Pattern.compile("code") };
        }

        @Override
        public @Nullable Difference transform(@Nullable DummyElement oldElement, @Nullable DummyElement newElement,
                @Nonnull Difference d) {
            return Difference.builder().withCode(d.code).withName(d.name).withDescription(d.description)
                    .addClassifications(d.classification).build();
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public String getExtensionId() {
            return "cloning-transform";
        }

        @Override
        public @Nullable Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class DummyArchive implements Archive {

        @Override
        public @Nonnull String getName() {
            return "Dummy Archive";
        }

        @Override
        public @Nonnull InputStream openStream() throws IOException {
            return new ByteArrayInputStream(new byte[0]);
        }
    }

    public static final class DummyAnalyzer extends BaseApiAnalyzer<DummyElement> {

        private final BiFunction<DummyElement, DummyElement, Report> differenceAnalyzer = (o, n) -> Report.builder()
                .withNew(n).withOld(o).addProblem().withCode("code").withName("name").done().build();;

        @Override
        public @Nonnull ArchiveAnalyzer<DummyElement> getArchiveAnalyzer(@Nonnull API api) {
            return new DummyArchiveAnalyzer(api, this);
        }

        @Override
        public DummyDifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer<DummyElement> oldArchive,
                @Nonnull ArchiveAnalyzer<DummyElement> newArchive) {
            return new DummyDifferenceAnalyzer(differenceAnalyzer);
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public String getExtensionId() {
            return "dummy-analyzer";
        }
    }

    public static final class DummyElementForest extends BaseElementForest<DummyElement> {

        DummyElementForest(@Nonnull API api) {
            super(api);
        }
    }

    public static final class DummyArchiveAnalyzer extends BaseArchiveAnalyzer<DummyElementForest, DummyElement> {
        private DummyArchiveAnalyzer(API api, ApiAnalyzer apiAnalyzer) {
            super(apiAnalyzer, api);
        }

        @Override
        protected DummyElementForest newElementForest() {
            return new DummyElementForest(getApi());
        }

        @Override
        protected Stream<DummyElement> discoverRoots(Object ctx) {
            return Stream.of(new DummyElement(getApi(), new DummyArchive()));
        }

        @Override
        protected Stream<DummyElement> discoverElements(Object ctx, DummyElement parent) {
            return Stream.empty();
        }
    }

    public static final class DummyDifferenceAnalyzer extends BaseDifferenceAnalyzer<DummyElement> {

        private final BiFunction<DummyElement, DummyElement, Report> reportingFunction;

        private DummyDifferenceAnalyzer(BiFunction<DummyElement, DummyElement, Report> reportingFunction) {
            this.reportingFunction = reportingFunction;
        }

        @Override
        public void beginAnalysis(@Nullable DummyElement oldElement, @Nullable DummyElement newElement) {
        }

        @Override
        public Report endAnalysis(@Nullable DummyElement oldElement, @Nullable DummyElement newElement) {
            return reportingFunction.apply(oldElement, newElement);
        }
    }

    public static final class DummyReporter extends BaseReporter {

        @Override
        public void report(@Nonnull Report report) {
        }

        @Override
        public String getExtensionId() {
            return "dummy-reporter";
        }
    }

    public static final class FailingReporter extends BaseReporter {

        @Override
        public void report(@Nonnull Report report) {
            throw new RuntimeException("Report difference.");
        }

        @Override
        public String getExtensionId() {
            return "failing-reporter";
        }
    }
}
