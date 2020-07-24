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
import static java.util.Arrays.asList;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;
import org.revapi.ElementForest;
import org.revapi.Report;
import org.revapi.simple.SimpleElementForest;

public class Analyzer implements ApiAnalyzer {

    @Nonnull
    @Override
    public ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
        return new ArchiveAnalyzer() {
            @Override
            @Nonnull
            public ElementForest analyze() {
                return new SimpleElementForest(api) {};
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

    @Override
    public String getExtensionId() {
        return "analyzer";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new java.io.StringReader("{\"type\": \"string\"}");
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        try {
            String path = analysisContext.getConfiguration().asString();
            Files.write(new File(path).toPath(), asList(""));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}