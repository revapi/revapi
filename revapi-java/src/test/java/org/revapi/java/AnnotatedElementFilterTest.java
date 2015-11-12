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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.java.filters.AnnotatedElementFilter;
import org.revapi.java.model.JavaElementForest;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public class AnnotatedElementFilterTest extends AbstractJavaElementAnalyzerTest {

    @Test
    public void testExcludeByAnnotationPresence() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"exclude\":" +
                "[\"@java.lang.Deprecated\"]}}}}}", results -> {

            Assert.assertEquals(5, results.size());
            Assert.assertEquals("class misc.AnnotationFilter", results.get(0).getFullHumanReadableString());
            Assert.assertEquals("class misc.AnnotationFilter.A", results.get(1).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                    results.get(2).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})",
                    results.get(3).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.Deprecated", results.get(4).getFullHumanReadableString());
        });
    }

    @Test
    public void testExcludeByAnnotationWithAttributeValues() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"exclude\":" +
                "[\"@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\"]}}}}}", results -> {

            Assert.assertEquals(5, results.size());
            Assert.assertEquals("class misc.AnnotationFilter", results.get(0).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                    results.get(1).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})",
                    results.get(2).getFullHumanReadableString());
            Assert.assertEquals("method void misc.AnnotationFilter::a()", results.get(3).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.Deprecated", results.get(4).getFullHumanReadableString());
        });

        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"exclude\":" +
                "[\"@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.SOURCE)\"]}}}}}", results -> {

            Assert.assertEquals(6, results.size());
            Assert.assertEquals("class misc.AnnotationFilter", results.get(0).getFullHumanReadableString());
            Assert.assertEquals("class misc.AnnotationFilter.A", results.get(1).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                    results.get(2).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})",
                    results.get(3).getFullHumanReadableString());
            Assert.assertEquals("method void misc.AnnotationFilter::a()", results.get(4).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.Deprecated", results.get(5).getFullHumanReadableString());
        });
    }

    @Test
    public void testIncludeByAnnotationPresence() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"include\":" +
                "[\"@java.lang.Deprecated\"]}}}}}", results -> {

            Assert.assertEquals(4, results.size());
            Assert.assertEquals("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                    results.get(0).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})",
                    results.get(1).getFullHumanReadableString());
            Assert.assertEquals("method void misc.AnnotationFilter::a()", results.get(2).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.Deprecated", results.get(3).getFullHumanReadableString());
        });
    }

    @Test
    public void testIncludeByAnnotationWithAttributeValues() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"include\":" +
                "[\"@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\"]}}}}}", results -> {

            Assert.assertEquals(4, results.size());
            Assert.assertEquals("class misc.AnnotationFilter.A", results.get(0).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                    results.get(1).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})",
                    results.get(2).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.Deprecated", results.get(3).getFullHumanReadableString());
        });
    }

    @Test
    public void testIncludeAndExclude() throws Exception {
        testWith("{\"revapi\":{\"java\":{\"filter\":{\"annotated\":{\"exclude\":" +
                "[\"@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\"]," +
                "\"include\": [\"@java.lang.Deprecated\"]}}}}}", results
                -> {

            Assert.assertEquals(4, results.size());
            Assert.assertEquals("@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)",
                    results.get(0).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.annotation.Target({java.lang.annotation.ElementType.TYPE})",
                    results.get(1).getFullHumanReadableString());
            Assert.assertEquals("method void misc.AnnotationFilter::a()", results.get(2).getFullHumanReadableString());
            Assert.assertEquals("@java.lang.Deprecated", results.get(3).getFullHumanReadableString());
        });
    }

    private void testWith(String configJSON, Consumer<List<Element>> test) throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "misc/AnnotationFilter.java");

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(new API(
                    Arrays.asList(new ShrinkwrapArchive(archive.archive)),
                    null), Executors.newSingleThreadExecutor(), null, false, false, Collections.<File>emptySet());

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
}
