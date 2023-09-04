/*
 * Copyright 2014-2023 Lukas Krejci
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

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.AnalysisResult;
import org.revapi.Element;
import org.revapi.PipelineConfiguration;
import org.revapi.Revapi;
import org.revapi.TreeFilter;
import org.revapi.basic.ConfigurableElementFilter;
import org.revapi.java.matcher.JavaElementMatcher;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 *
 * @since 0.7.0
 */
public class ClassFilterTest extends AbstractJavaElementAnalyzerTest {
    @Test
    public void testFilterByName_exclude() throws Exception {
        testWith(
                "{\"revapi\": {\"filter\": {\"elements\": {\"exclude\": [{\"matcher\": \"java\", \"match\": \"class classfilter.A {}\"}]}}}}",
                Stream.of("class classfilter.B", "field classfilter.B.field", "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()", "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()", "class classfilter.B.BB",
                        "method void classfilter.B.BB::<init>()").collect(toSet()));
    }

    @Test
    public void testDeprecatedFilterByName_exclude() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"exclude\": [\"classfilter.A\"]}}}}}",
                Stream.of("class classfilter.B", "field classfilter.B.field", "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()", "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()", "class classfilter.B.BB",
                        "method void classfilter.B.BB::<init>()").collect(toSet()));
    }

    @Test
    public void testFilterByName_include() throws Exception {
        testWith(
                "{\"revapi\": {\"filter\": {\"elements\": {\"include\": [{\"matcher\": \"java\", \"match\": \"class classfilter.A {}\"}]}}}}",
                Stream.of("class classfilter.A", "method void classfilter.A::m()",
                        "method void classfilter.A::<init>()", "class classfilter.A.AA",
                        "method void classfilter.A.AA::<init>()", "class classfilter.A.AA.AAA",
                        "method void classfilter.A.AA.AAA::<init>()", "class classfilter.A.AB",
                        "method void classfilter.A.AB::<init>()").collect(toSet()));
    }

    @Test
    public void testDeprecatedFilterByName_include() throws Exception {
        testWith("{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"include\": [\"classfilter.A\"]}}}}}",
                Stream.of("class classfilter.A", "method void classfilter.A::m()",
                        "method void classfilter.A::<init>()", "class classfilter.A.AA",
                        "method void classfilter.A.AA::<init>()", "class classfilter.A.AA.AAA",
                        "method void classfilter.A.AA.AAA::<init>()", "class classfilter.A.AB",
                        "method void classfilter.A.AB::<init>()").collect(toSet()));
    }

    @Test
    public void testInnerClassExclusionOverride() throws Exception {
        testWith(
                "{\"revapi\": {\"filter\": {\"elements\": {\"exclude\": [{\"matcher\": \"java\", \"match\": \"class classfilter.A {}\"}],"
                        + " \"include\": [{\"matcher\": \"java\", \"match\": \"match %a|%b; class %a=classfilter.A.AA.AAA {} class %b=classfilter.B {}\"}]}}}}",
                Stream.of("class classfilter.A.AA.AAA", "method void classfilter.A.AA.AAA::<init>()",
                        "class classfilter.B", "field classfilter.B.field", "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()", "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()", "class classfilter.B.BB",
                        "method void classfilter.B.BB::<init>()").collect(toSet()));
    }

    @Test
    public void testDeprecatedInnerClassExclusionOverride() throws Exception {
        testWith(
                "{\"revapi\": {\"java\": {\"filter\": {\"classes\": {\"exclude\": [\"classfilter.A\"],"
                        + " \"include\": [\"classfilter.A.AA.AAA\", \"classfilter.B\"]}}}}}",
                Stream.of("class classfilter.A.AA.AAA", "method void classfilter.A.AA.AAA::<init>()",
                        "class classfilter.B", "field classfilter.B.field", "method void classfilter.B::m()",
                        "method void classfilter.B::<init>()", "class classfilter.B.BA",
                        "method void classfilter.B.BA::<init>()", "class classfilter.B.BB",
                        "method void classfilter.B.BB::<init>()").collect(toSet()));
    }

    private void testWith(String configJSON, Set<String> expectedResults) throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "classfilter/A.java", "classfilter/B.java");
        testWith(archive, configJSON, expectedResults);
    }

    static void testWith(ArchiveAndCompilationPath archive, String configJSON, Set<String> expectedResults)
            throws Exception {
        try {
            JavaApiAnalyzer apiAnalyzer = new JavaApiAnalyzer(Collections.emptyList(), Collections.emptyList());
            Revapi r = new Revapi(PipelineConfiguration.builder().withAnalyzers(JavaApiAnalyzer.class)
                    .withFilters(ConfigurableElementFilter.class).withMatchers(JavaElementMatcher.class).build());
            AnalysisContext ctx = AnalysisContext.builder(r).withConfigurationFromJSON(configJSON).build();
            AnalysisResult.Extensions extensions = r.prepareAnalysis(ctx);

            AnalysisContext filterCtx = extensions.getFirstConfigurationOrNull(ConfigurableElementFilter.class);
            AnalysisContext analyzerCtx = extensions.getFirstConfigurationOrNull(JavaApiAnalyzer.class);

            ConfigurableElementFilter filter = new ConfigurableElementFilter();

            Assert.assertNotNull(analyzerCtx);
            Assert.assertNotNull(filterCtx);

            apiAnalyzer.initialize(analyzerCtx);
            filter.initialize(filterCtx);

            JavaArchiveAnalyzer archiveAnalyzer = apiAnalyzer.getArchiveAnalyzer(
                    new API(Collections.singletonList(new ShrinkwrapArchive(archive.archive)), null));

            JavaElementForest forest = archiveAnalyzer
                    .analyze(filter.filterFor(archiveAnalyzer).orElse(TreeFilter.matchAndDescend()));
            archiveAnalyzer.prune(forest);

            List<JavaElement> results = forest.stream(JavaElement.class, true, null).collect(toList());

            archiveAnalyzer.getCompilationValve().removeCompiledResults();

            List<String> expected = new ArrayList<>(expectedResults);
            List<String> actual = results.stream()
                    // don't include inherited stuff and annotations. We don't work with inherited classes or
                    // annotations
                    // here and we're actually not interested in seeing stuff from java.lang.Object in the tests,
                    // because
                    // that is just unnecessarily verbose for the purpose of the tests we're doing here.
                    .filter(e -> {
                        if (e.getArchive() == null) {
                            return false;
                        }

                        if (!(e instanceof JavaModelElement)) {
                            // exclude annotations
                            return false;
                        }

                        JavaModelElement el = (JavaModelElement) e;
                        return !el.isInherited();
                    }).map(Element::getFullHumanReadableString).collect(toList());

            Collections.sort(expected);
            Collections.sort(actual);

            Assert.assertEquals(expected, actual);
        } finally {
            deleteDir(archive.compilationPath);
        }
    }
}
