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
import org.revapi.java.filters.ClassFilter;
import org.revapi.java.model.JavaElementForest;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public class ClassFilterTest extends AbstractJavaElementAnalyzerTest {
    @Test
    public void testSimpleFilterByName() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"exclude\": [\"classfilter.A\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class classfilter.B",
                            "field classfilter.B.field",
                            "method void classfilter.B::m()",
                            "method void classfilter.B::<init>()",
                            "class classfilter.B.BA",
                            "method void classfilter.B.BA::<init>()",
                            "class classfilter.B.BB",
                            "method void classfilter.B.BB::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });

        testWith("{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"include\": [\"classfilter.A\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class classfilter.A",
                            "method void classfilter.A::m()",
                            "method void classfilter.A::<init>()",
                            "class classfilter.A.AA",
                            "method void classfilter.A.AA::<init>()",
                            "class classfilter.A.AA.AAA",
                            "method void classfilter.A.AA.AAA::<init>()",
                            "class classfilter.A.AB",
                            "method void classfilter.A.AB::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });
    }

    @Test
    public void testInnerClassExclusionOverride() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"exclude\": [\"classfilter.A\"]," +
                " \"include\": [\"classfilter.A.AA.AAA\", \"classfilter.B\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class classfilter.A.AA.AAA",
                            "method void classfilter.A.AA.AAA::<init>()",
                            "class classfilter.B",
                            "field classfilter.B.field",
                            "method void classfilter.B::m()",
                            "method void classfilter.B::<init>()",
                            "class classfilter.B.BA",
                            "method void classfilter.B.BA::<init>()",
                            "class classfilter.B.BB",
                            "method void classfilter.B.BB::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });

    }

    @Test
    public void testRegexFilter() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"regex\": true," +
                " \"exclude\": [\"classfilter\\.(A|B\\.BB)\"]}}}}}",
                elements -> {
                    Set<String> expected = Stream.of(
                            "class classfilter.B",
                            "field classfilter.B.field",
                            "method void classfilter.B::m()",
                            "method void classfilter.B::<init>()",
                            "class classfilter.B.BA",
                            "method void classfilter.B.BA::<init>()"
                    ).collect(toSet());

                    Assert.assertEquals(expected, elements.stream().map(Element::getFullHumanReadableString)
                            .collect(toSet()));
                });
    }


    private void testWith(String configJSON, Consumer<List<Element>> test) throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "classfilter/A.java", "classfilter/B.java");

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(
                    new API(Collections.singletonList(new ShrinkwrapArchive(archive.archive)), null),
                    Executors.newSingleThreadExecutor(), null, false, false, Collections.<File>emptySet());

            JavaElementForest forest = analyzer.analyze();

            ClassFilter filter = new ClassFilter();
            AnalysisContext ctx = AnalysisContext.builder().withConfigurationFromJSON(configJSON).build();
            filter.initialize(ctx);

            List<Element> results = forest.search(Element.class, true, filter, null);

            test.accept(results);
        } finally {
            deleteDir(archive.compilationPath);
        }
    }
}
