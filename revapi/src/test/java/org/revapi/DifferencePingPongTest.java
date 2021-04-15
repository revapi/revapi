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

import java.io.Reader;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.junit.Test;
import org.revapi.base.BaseElement;
import org.revapi.base.BaseElementForest;

public class DifferencePingPongTest {

    @Test
    public void testOrderApplied() throws Exception {
        Revapi revapi = Revapi.builder().withAnalyzers(DummyAnalyzer.class)
                .withTransforms(MakeBreaking.class, MakeNonBreaking.class).withReporters(DummyReporter.class)
                .addTransformationBlock(Arrays.asList("b", "nb")).build();

        try (AnalysisResult res = revapi.analyze(AnalysisContext.builder().withOldAPI(API.builder().build())
                .withNewAPI(API.builder().build()).build())) {

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

    public static class TransformOther implements DifferenceTransform {
        private final DifferenceSeverity targetSeverity;
        private final String extensionId;

        public TransformOther(DifferenceSeverity targetSeverity, String extensionId) {
            this.targetSeverity = targetSeverity;
            this.extensionId = extensionId;
        }

        @Nonnull
        @Override
        public Pattern[] getDifferenceCodePatterns() {
            return new Pattern[] { Pattern.compile("code") };
        }

        @Nullable
        @Override
        public Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
                @Nonnull Difference difference) {
            return Difference.builder().withCode(difference.code).withName(difference.name)
                    .withDescription(difference.description).addClassifications(difference.classification)
                    .addClassification(CompatibilityType.OTHER, targetSeverity).addAttachments(difference.attachments)
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

    public static final class DummyElement extends BaseElement<DummyElement> {

        protected DummyElement(API api) {
            super(api);
        }

        protected DummyElement(API api, @Nullable Archive archive) {
            super(api, archive);
        }

        @Override
        public int compareTo(DummyElement o) {
            return 0;
        }
    }

    public static final class DummyAnalyzer implements ApiAnalyzer<DummyElement> {

        @Nonnull
        @Override
        public ArchiveAnalyzer<DummyElement> getArchiveAnalyzer(@Nonnull API api) {
            return new ArchiveAnalyzer<DummyElement>() {
                @Override
                public ApiAnalyzer<DummyElement> getApiAnalyzer() {
                    return DummyAnalyzer.this;
                }

                @Override
                public API getApi() {
                    return api;
                }

                @Nonnull
                @Override
                public ElementForest<DummyElement> analyze(TreeFilter<DummyElement> filter) {
                    BaseElementForest<DummyElement> ret = new BaseElementForest<>(api);

                    Archive archive = api.getArchives().iterator().hasNext() ? api.getArchives().iterator().next()
                            : null;

                    Set<DummyElement> roots = ret.getRoots();
                    roots.add(new DummyElement(api, archive));

                    return ret;
                }

                @Override
                public void prune(ElementForest<DummyElement> forest) {

                }
            };
        }

        @Nonnull
        @Override
        public DifferenceAnalyzer<DummyElement> getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer<DummyElement> oldArchive,
                @Nonnull ArchiveAnalyzer<DummyElement> newArchive) {
            return new DifferenceAnalyzer<DummyElement>() {
                @Override
                public void open() {

                }

                @Override
                public void beginAnalysis(@Nullable DummyElement oldElement, @Nullable DummyElement newElement) {

                }

                @Override
                public boolean isDescendRequired(@Nullable DummyElement oldElement, @Nullable DummyElement newElement) {
                    return false;
                }

                @Override
                public Report endAnalysis(@Nullable DummyElement oldElement, @Nullable DummyElement newElement) {
                    return Report.builder().withOld(oldElement).withNew(newElement).addProblem().withCode("code")
                            .withName("name").addClassification(CompatibilityType.SOURCE, DifferenceSeverity.BREAKING)
                            .addClassification(CompatibilityType.BINARY, DifferenceSeverity.BREAKING).done().build();
                }

                @Override
                public void close() throws Exception {

                }
            };
        }

        @Nonnull
        @Override
        public CorrespondenceComparatorDeducer<DummyElement> getCorrespondenceDeducer() {
            return CorrespondenceComparatorDeducer.naturalOrder();
        }

        @Override
        public void close() throws Exception {

        }

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
        public void report(Report report) {

        }

        @Override
        public void close() throws Exception {

        }

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
