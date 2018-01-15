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
package org.revapi.java;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementForest;
import org.revapi.FilterResult;
import org.revapi.Revapi;
import org.revapi.basic.ConfigurableElementFilter;
import org.revapi.java.matcher.JavaElementMatcher;
import org.revapi.simple.SimpleElementFilter;

/**
 * @author Lukas Krejci
 * @since 0.7.0
 */
public class ClassFilterTest extends AbstractJavaElementAnalyzerTest {
    @Test
    public void testSimpleFilterByName_exclude() throws Exception {
        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"exclude\": [{\"matcher\": \"matcher.java\", \"match\": \"'class classfilter.A'\"}]}}}}",
                Stream.of(
                        "class classfilter.B",
                        "field classfilter.B.field",
                        "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()",
                        "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()",
                        "class classfilter.B.BB",
                        "method void classfilter.B.BB::<init>()").collect(toSet()));
    }

    @Test
    public void testSimpleFilterByName_include() throws Exception {
        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"include\": [{\"matcher\": \"matcher.java\", \"match\": \"'class classfilter.A'\"}]}}}}",
                Stream.of(
                        "class classfilter.A",
                        "method void classfilter.A::m()",
                        "method void classfilter.A::<init>()",
                        "class classfilter.A.AA",
                        "method void classfilter.A.AA::<init>()",
                        "class classfilter.A.AA.AAA",
                        "method void classfilter.A.AA.AAA::<init>()",
                        "class classfilter.A.AB",
                        "method void classfilter.A.AB::<init>()").collect(toSet()));
    }

    @Test
    public void testInnerClassExclusionOverride() throws Exception {
        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"exclude\": [{\"matcher\": \"matcher.java\", \"match\": \"'class classfilter.A'\"}]," +
                " \"include\": [{\"matcher\": \"matcher.java\", \"match\": \"'class classfilter.A.AA.AAA' or 'class classfilter.B'\"}]}}}}",
                Stream.of(
                        "class classfilter.A.AA.AAA",
                        "method void classfilter.A.AA.AAA::<init>()",
                        "class classfilter.B",
                        "field classfilter.B.field",
                        "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()",
                        "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()",
                        "class classfilter.B.BB",
                        "method void classfilter.B.BB::<init>()").collect(toSet()));
    }

    @Test
    public void testRegexFilter() throws Exception {
        testWith("{\"revapi\": {\"filter\": {\"elements\": {\"exclude\": [{\"matcher\": \"matcher.java\", \"match\": \"/class classfilter\\.(A|B\\.BB)/\"}]}}}}",
                Stream.of(
                        "class classfilter.B",
                        "field classfilter.B.field",
                        "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()",
                        "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()").collect(toSet()));
    }


    private void testWith(String configJSON, Set<String> expectedResults) throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "classfilter/A.java", "classfilter/B.java");
        testWith(archive, configJSON, expectedResults);
    }

    static void testWith(ArchiveAndCompilationPath archive, String configJSON, Set<String> expectedResults)
            throws Exception {
        try {
            JavaApiAnalyzer apiAnalyzer = new JavaApiAnalyzer(Collections.emptyList());
            Revapi r = new Revapi(singleton(JavaApiAnalyzer.class), emptySet(), emptySet(),
                    singleton(ConfigurableElementFilter.class), singleton(JavaElementMatcher.class));

            AnalysisContext ctx = AnalysisContext.builder(r).withConfigurationFromJSON(configJSON).build();
            AnalysisResult.Extensions extensions = r.prepareAnalysis(ctx);

            AnalysisContext filterCtx = extensions.getFirstConfigurationOrNull(ConfigurableElementFilter.class);
            AnalysisContext analyzerCtx = extensions.getFirstConfigurationOrNull(JavaApiAnalyzer.class);

            ConfigurableElementFilter filter = new ConfigurableElementFilter();

            Assert.assertNotNull(analyzerCtx);
            Assert.assertNotNull(filterCtx);

            apiAnalyzer.initialize(analyzerCtx);
            filter.initialize(filterCtx);

            ArchiveAnalyzer archiveAnalyzer = apiAnalyzer.getArchiveAnalyzer(
                    new API(Collections.singletonList(new ShrinkwrapArchive(archive.archive)), null));

            ElementForest forest = archiveAnalyzer.analyze(e -> FilterResult.matchAndDescend());

            List<Element> results = forest.search(Element.class, true, filter.asFilter(), null);

            ((JavaArchiveAnalyzer) archiveAnalyzer).getCompilationValve().removeCompiledResults();

            List<String> expected = new ArrayList<>(expectedResults);
            List<String> actual = results.stream()
                    //don't include stuff from the system classpath, because that makes the results unnecessarily
                    //huge, while we don't actually need to check the system classpath element at all in the tests
                    .filter(e -> e.getArchive() != null && !e.getArchive().getName().equals("<system classpath>"))
                    .map(Element::getFullHumanReadableString).collect(toList());

            Collections.sort(expected);
            Collections.sort(actual);

            Assert.assertEquals(expected, actual);
        } finally {
            deleteDir(archive.compilationPath);
        }
    }

    private static class AcceptingFilter extends SimpleElementFilter {
        @Override
        public boolean applies(@Nullable Element element) {
            return true;
        }

        @Override
        public boolean shouldDescendInto(@Nullable Object element) {
            return true;
        }
    }
}
