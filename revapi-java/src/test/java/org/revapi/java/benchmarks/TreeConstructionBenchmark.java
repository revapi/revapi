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
package org.revapi.java.benchmarks;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.TreeFilter;
import org.revapi.base.FileArchive;
import org.revapi.java.JavaApiAnalyzer;
import org.revapi.java.JavaArchiveAnalyzer;
import org.revapi.java.model.JavaElementForest;

@State(Scope.Benchmark)
public class TreeConstructionBenchmark {

    private JavaArchiveAnalyzer archiveAnalyzer;

    @Setup
    public void prepareAnalyzer() throws IOException {
        Properties jarLocations = new Properties();
        jarLocations.load(TreeConstructionBenchmark.class
                .getResourceAsStream("/benchmarks/tree-construction-archives.properties"));

        API.Builder apiBld = API.builder();

        for (String key : jarLocations.stringPropertyNames()) {
            String value = jarLocations.getProperty(key);
            if (value.contains("${settings.localRepository}")) {
                // we're running outside of the test phase, so try to use the default local repository
                String localRepo = System.getProperty("user.home") + "/.m2/repository";
                value = value.replace("${settings.localRepository}", localRepo);
            }
            if (key.startsWith("main")) {
                apiBld.addArchive(new FileArchive(new File(value)));
            } else if (key.startsWith("dep")) {
                apiBld.addSupportArchive(new FileArchive(new File(value)));
            }
        }

        API api = apiBld.build();
        JavaApiAnalyzer apiAnalyzer = new JavaApiAnalyzer();
        apiAnalyzer.initialize(AnalysisContext.builder().withOldAPI(api).withNewAPI(api).build());
        archiveAnalyzer = apiAnalyzer.getArchiveAnalyzer(api);
    }

    @Benchmark
    public void constructTree(Blackhole hole) {
        JavaElementForest forest = archiveAnalyzer.analyze(TreeFilter.matchAndDescend());
        archiveAnalyzer.prune(forest);
        hole.consume(forest);
    }

    @Test
    public void testConstructTree() throws IOException {
        prepareAnalyzer();
        JavaElementForest forest = archiveAnalyzer.analyze(TreeFilter.matchAndDescend());
        archiveAnalyzer.prune(forest);
    }
}
