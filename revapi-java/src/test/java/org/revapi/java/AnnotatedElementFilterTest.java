/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.java.compilation.InclusionFilter;
import org.revapi.java.filters.AnnotatedElementFilter;
import org.revapi.java.model.JavaElementForest;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public class AnnotatedElementFilterTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testExcludeByAnnotationPresence() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"regex\": true, \"exclude\":" +
                "[\"@annotationfilter.NonPublic.*\"]}}}}}", results -> {

            Assert.assertEquals(73, results.size());
            assertNotContains(results.stream().map(Element::getFullHumanReadableString).collect(toList()),
                    "class annotationfilter.NonPublicClass", "field annotationfilter.NonPublicClass.f",
                    "method void annotationfilter.NonPublicClass::m()",
                    "method void annotationfilter.PublicClass::implDetail()",
                    "class annotationfilter.PublicClass.NonPublicInnerClass");
        });
    }

    @Test
    public void testExcludeByAnnotationWithAttributeValues() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"exclude\":" +
                "[\"@annotationfilter.NonPublic(since = \\\"2.0\\\")\"]}}}}}", results -> {

            Assert.assertEquals(112, results.size());
            assertNotContains(results.stream().map(Element::getFullHumanReadableString).collect(toList()),
                    "method void annotationfilter.PublicClass::implDetail()");
        });
    }

    @Test
    public void testIncludeByAnnotationPresence() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"include\":" +
                "[\"@annotationfilter.Public\"]}}}}}", results -> {

            Assert.assertEquals(55, results.size());
            assertNotContains(results.stream().map(Element::getFullHumanReadableString).collect(toList()),
                    "class annotationfilter.NonPublic", "method java.lang.String annotationfilter.NonPublic::since()",
                    "class annotationfilter.NonPublicClass", "field annotationfilter.NonPublicClass.f",
                    "class annotationfilter.Public", "class annotationfilter.UndecisiveClass",
                    "field annotationfilter.UndecisiveClass.f",
                    "method void annotationfilter.UndecisiveClass::m()");
        });
    }

    @Test
    public void testIncludeByAnnotationWithAttributeValues() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"include\":" +
                "[\"@annotationfilter.NonPublic(since = \\\"2.0\\\")\"]}}}}}", results -> {

            Assert.assertEquals(1, results.size());
            Assert.assertEquals("method void annotationfilter.PublicClass::implDetail()",
                    results.get(0).getFullHumanReadableString());
        });
    }

    @Test
    public void testIncludeAndExclude() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"regex\" : true, \"exclude\":" +
                "[\"@annotationfilter.NonPublic.*\"]," +
                "\"include\": [\"@annotationfilter.Public\"]}}}}}", results
                -> {

            Assert.assertEquals(37, results.size());
            assertNotContains(results.stream().map(Element::getFullHumanReadableString).collect(toList()),
                    "class annotationfilter.NonPublic",
                    "method java.lang.String annotationfilter.NonPublic::since()",
                    "class annotationfilter.NonPublicClass", "field annotationfilter.NonPublicClass.f",
                    "class annotationfilter.Public",
                    "method void annotationfilter.PublicClass::implDetail()",
                    "class annotationfilter.PublicClass.NonPublicInnerClass",
                    "class annotationfilter.UndecisiveClass", "field annotationfilter.UndecisiveClass.f",
                    "method void annotationfilter.UndecisiveClass::m()");

        });
    }

    private void testWith(String configJSON, Consumer<List<Element>> test) throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "annotationfilter/NonPublic.java",
                "annotationfilter/NonPublicClass.java", "annotationfilter/Public.java",
                "annotationfilter/PublicClass.java", "annotationfilter/UndecisiveClass.java");

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(
                    new API(Arrays.asList(new ShrinkwrapArchive(archive.archive)), null),
                    Executors.newSingleThreadExecutor(), null, false,
                    InclusionFilter.acceptAll());

            JavaElementForest forest = analyzer.analyze();

            AnnotatedElementFilter filter = new AnnotatedElementFilter();
            AnalysisContext ctx = AnalysisContext.builder().withConfigurationFromJSON(configJSON).build();
            filter.initialize(ctx);

            List<Element> results = forest.search(Element.class, true, filter, null);

            test.accept(results);
        } finally {
            deleteDir(archive.compilationPath);
        }
    }

    private <T> void assertNotContains(List<T> list, T... elements) {
        ArrayList<T> intersection = new ArrayList<>(list);
        intersection.retainAll(Arrays.asList(elements));

        if (!intersection.isEmpty()) {
            Assert.fail("List " + list + " shouldn't have contained any of the " + Arrays.asList(elements));
        }
    }
}
