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
package org.revapi;

import java.io.Reader;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.Test;
import org.revapi.simple.SimpleElement;
import org.revapi.simple.SimpleElementForest;

public class DifferencePingPongTest {

    @Test
    public void testOrderApplied() throws Exception {
        Revapi revapi = Revapi.builder()
                .withAnalyzers(DummyAnalyzer.class)
                .withTransforms(MakeBreaking.class, MakeNonBreaking.class)
                .withReporters(DummyReporter.class)
                .addTransformationBlock(Arrays.asList("b", "nb"))
                .build();

        try (AnalysisResult res = revapi.analyze(AnalysisContext.builder()
                .withOldAPI(API.builder().build()).withNewAPI(API.builder().build())
                .build())) {

            res.throwIfFailed();
        }
    }

    public static final class MakeBreaking extends TransformOther {
        public MakeBreaking() {
            super(DifferenceSeverity.BREAKING, "b");
        }
    }

    public static final class MakeNonBreaking extends TransformOther {
        public MakeNonBreaking() {
            super(DifferenceSeverity.NON_BREAKING, "nb");
        }
    }

    public static class TransformOther implements DifferenceTransform<Element> {
        private final DifferenceSeverity targetSeverity;
        private final String extensionId;
        public TransformOther(DifferenceSeverity targetSeverity, String extensionId) {
            this.targetSeverity = targetSeverity;
            this.extensionId = extensionId;
        }

        @Nonnull
        @Override
        public Pattern[] getDifferenceCodePatterns() {
            return new Pattern[]{Pattern.compile("code")};
        }

        @Nullable
        @Override
        public Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
                @Nonnull Difference difference) {
            return Difference.builder()
                    .withCode(difference.code)
                    .withName(difference.name)
                    .withDescription(difference.description)
                    .addClassifications(difference.classification)
                    .addClassification(CompatibilityType.OTHER, targetSeverity)
                    .addAttachments(difference.attachments)
                    .withIdentifyingAttachments(difference.attachments.keySet().stream()
                            .filter(difference::isIdentifyingAttachment).collect(Collectors.toList()))
                    .build();
        }

        @Override
        public void close() throws Exception {

        }

        @Override
        public String getExtensionId() {
            return extensionId;
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

    public static final class DummyAnalyzer implements ApiAnalyzer {

        @Nonnull
        @Override
        public ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
            return new ArchiveAnalyzer() {
                @Nonnull
                @Override
                public ElementForest analyze() {
                    ElementForest ret = new SimpleElementForest(api) {
                    };
                    @SuppressWarnings("unchecked") Set<Element> roots = (Set) ret.getRoots();
                    roots.add(new SimpleElement() {
                        @Nonnull
                        @Override
                        public API getApi() {
                            return api;
                        }

                        @Nullable
                        @Override
                        public Archive getArchive() {
                            Iterator<? extends Archive> it = api.getArchives().iterator();
                            return it.hasNext() ? it.next() : null;
                        }

                        @Override
                        public int compareTo(Element o) {
                            return 0;
                        }
                    });

                    return ret;
                }
            };
        }

        @Nonnull
        @Override
        public DifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer oldArchive,
                @Nonnull ArchiveAnalyzer newArchive) {
            return new DifferenceAnalyzer() {
                @Override
                public void open() {

                }

                @Override
                public void beginAnalysis(@Nullable Element oldElement, @Nullable Element newElement) {

                }

                @Override
                public boolean isDescendRequired(@Nullable Element oldElement, @Nullable Element newElement) {
                    return false;
                }

                @Override
                public Report endAnalysis(@Nullable Element oldElement, @Nullable Element newElement) {
                    return Report.builder()
                            .withOld(oldElement)
                            .withNew(newElement)
                            .addProblem()
                            .withCode("code")
                            .addClassification(CompatibilityType.SOURCE, DifferenceSeverity.BREAKING)
                            .addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING)
                            .done()
                            .build();
                }

                @Override
                public void close() throws Exception {

                }
            };
        }

        @Nonnull
        @Override
        public CorrespondenceComparatorDeducer getCorrespondenceDeducer() {
            return CorrespondenceComparatorDeducer.naturalOrder();
        }

        @Override
        public void close() throws Exception {

        }

        @Nullable
        @Override
        public String getExtensionId() {
            return "dummy-analyzer";
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
            return "dummy-reporter";
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
