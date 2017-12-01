/*
 * Copyright 2014-2017 Lukas Krejci
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
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.simple.SimpleElement;
import org.revapi.simple.SimpleElementForest;

/**
 * @author Lukas Krejci
 * @since 0.3.3
 */
public class AnalysisTest {

    @Test
    public void testTransformCycleDetection() throws Exception {
        Revapi r = Revapi.builder().withAnalyzers(DummyAnalyzer.class)
                .withTransforms(CloningDifferenceTransform.class).withReporters(DummyReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(r).withNewAPI(API.of().build()).withOldAPI(API.of().build())
                .build();

        //should not throw exception
        try (AnalysisResult res = r.analyze(ctx)) {
            Assert.assertTrue(res.isSuccess());
        }
    }
    public static final class CloningDifferenceTransform implements DifferenceTransform<Element> {

        @Override
        public @Nonnull Pattern[] getDifferenceCodePatterns() {
            return new Pattern[]{Pattern.compile("code")};
        }

        @Override
        public @Nullable Difference transform(@Nullable Element oldElement, @Nullable Element newElement, @Nonnull Difference d) {
            return Difference.builder().withCode(d.code).withName(d.name).withDescription(d.description)
                    .addClassifications(d.classification).build();
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public @Nullable String getExtensionId() {
            return null;
        }

        @Override
        public @Nullable Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class DummyElement extends SimpleElement {

        private final API api;
        private final Archive archive;

        private DummyElement(API api, Archive archive) {
            this.api = api;
            this.archive = archive;
        }

        @Override
        public @Nonnull API getApi() {
            return api;
        }

        @Override
        public @Nullable Archive getArchive() {
            return archive;
        }

        @Override
        public int compareTo(Element o) {
            return 0;
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

    public static final class DummyAnalyzer implements ApiAnalyzer {

        private final BiFunction<Element, Element, Report> differenceAnalyzer = (o, n) ->
                Report.builder().withNew(n).withOld(o).addProblem().withCode("code").done().build();;

        @Override
        public @Nonnull CorrespondenceComparatorDeducer getCorrespondenceDeducer() {
            return CorrespondenceComparatorDeducer.naturalOrder();
        }

        @Override
        public @Nonnull ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
            return new DummyArchiveAnalyzer(api);
        }

        @Override
        public @Nonnull DifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer oldArchive,
                                                                 @Nonnull ArchiveAnalyzer newArchive) {
            return new DummyDifferenceAnalyzer(differenceAnalyzer);
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public @Nullable String getExtensionId() {
            return null;
        }

        @Override
        public @Nullable Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class DummyElementForest extends SimpleElementForest {

        DummyElementForest(@Nonnull API api) {
            super(api);
        }

        @SuppressWarnings("unchecked")
        @Override
        public @Nonnull SortedSet<DummyElement> getRoots() {
            return (SortedSet<DummyElement>) super.getRoots();
        }
    }

    public static final class DummyArchiveAnalyzer implements ArchiveAnalyzer {

        private final API api;

        private DummyArchiveAnalyzer(API api) {
            this.api = api;
        }

        @Override
        public @Nonnull ElementForest analyze(Filter filter) {
            DummyElementForest ret = new DummyElementForest(api);
            ret.getRoots().add(new DummyElement(api, new DummyArchive()));
            return ret;
        }

        @Override
        public void prune(ElementForest forest) {

        }
    }

    public static final class DummyDifferenceAnalyzer implements DifferenceAnalyzer {

        private final BiFunction<Element, Element, Report> reportingFunction;

        private DummyDifferenceAnalyzer(BiFunction<Element, Element, Report> reportingFunction) {
            this.reportingFunction = reportingFunction;
        }

        @Override
        public void open() {
        }

        @Override
        public void beginAnalysis(@Nullable Element oldElement, @Nullable Element newElement) {
        }

        @Override
        public Report endAnalysis(@Nullable Element oldElement, @Nullable Element newElement) {
            return reportingFunction.apply(oldElement, newElement);
        }

        @Override
        public void close() throws Exception {
        }
    }

    public static final class DummyReporter implements Reporter {

        @Override
        public void report(@Nonnull Report report) {
        }

        @Override
        public void close() throws Exception {
        }

        @Nullable
        @Override
        public String getExtensionId() {
            return null;
        }

        @Nullable
        @Override
        public Reader getJSONSchema() {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }
}
