/*
 * Copyright 2016 Lukas Krejci
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
 *
 */
package org.revapi.java;

import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.java.filters.PackageFilter;
import org.revapi.java.model.JavaElementForest;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public class PackageFilterTest extends AbstractJavaElementAnalyzerTest {
    @Test
    public void testSimpleExclude() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"packages\": {\"exclude\": [\"packagefilter.a.a.a\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class packagefilter.b.a.A",
                            "method void packagefilter.b.a.A::<init>()",
                            "class packagefilter.b.b.B",
                            "method void packagefilter.b.b.B::<init>()",
                            "class packagefilter.a.b.B",
                            "method void packagefilter.a.b.B::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });

        testWith("{\"revapi\": {\"java\": {\"filter\": {\"packages\": {\"include\": [\"packagefilter.a.a.a\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class packagefilter.a.a.a.A",
                            "method void packagefilter.a.a.a.A::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });
    }

    @Test
    public void testSimpleInclude() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"packages\": {\"include\": [\"packagefilter.a.a.a\"," +
                " \"packagefilter.b.a\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class packagefilter.a.a.a.A",
                            "method void packagefilter.a.a.a.A::<init>()",
                            "class packagefilter.b.a.A",
                            "method void packagefilter.b.a.A::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });

    }

    @Test
    public void testRegexFilter() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"packages\": {\"regex\": true," +
                        " \"exclude\": [\"packagefilter\\.a\\..*\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class packagefilter.b.a.A",
                            "method void packagefilter.b.a.A::<init>()",
                            "class packagefilter.b.b.B",
                            "method void packagefilter.b.b.B::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });

        testWith("{\"revapi\": {\"java\": {\"filter\": {\"packages\": {\"regex\": true," +
                        " \"include\": [\"packagefilter\\.a\\..*\"], \"exclude\": [\"packagefilter\\.a\\.a\\.a\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class packagefilter.a.b.B",
                            "method void packagefilter.a.b.B::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });
    }


    private void testWith(String configJSON, Consumer<List<Element>> test) throws Exception {
        AbstractJavaElementAnalyzerTest.ArchiveAndCompilationPath archive = createCompiledJar("test.jar",
                "packagefilter/a/a/a/A.java", "packagefilter/a/b/B.java", "packagefilter/b/a/A.java",
                "packagefilter/b/b/B.java");

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(
                    new API(Collections.singletonList(new AbstractJavaElementAnalyzerTest.ShrinkwrapArchive(archive.archive)), null),
                    Executors.newSingleThreadExecutor(), null, false, false, Collections.<File>emptySet());

            JavaElementForest forest = analyzer.analyze();

            PackageFilter filter = new PackageFilter();
            AnalysisContext ctx = AnalysisContext.builder().withConfigurationFromJSON(configJSON).build();
            filter.initialize(ctx);

            List<Element> results = forest.search(Element.class, true, filter, null);

            test.accept(results);
        } finally {
            deleteDir(archive.compilationPath);
        }
    }
}
