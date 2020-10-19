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
package org.revapi.java;

import static java.util.stream.Collectors.toList;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.PipelineConfiguration;
import org.revapi.Report;
import org.revapi.Revapi;
import org.revapi.TreeFilter;
import org.revapi.basic.ConfigurableElementFilter;
import org.revapi.java.matcher.JavaElementMatcher;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public class AnnotatedElementFilterTest extends AbstractJavaElementAnalyzerTest {

    private static final int NUMBER_OF_ELEMENTS_ON_OBJECT;
    private static final int NUMBER_OF_ELEMENTS_ON_ANNOTATION;

    static {
        NUMBER_OF_ELEMENTS_ON_OBJECT = getNumberOfChildElements(Object.class);
        NUMBER_OF_ELEMENTS_ON_ANNOTATION = getNumberOfChildElements(Annotation.class);
    }

    @Test
    public void testExcludeByAnnotationPresence() throws Exception {
        testWith("{\"revapi\":{\"filter\":{\"elements\":{\"exclude\":" +
                "[{\"matcher\": \"matcher.java\", \"match\": \"@annotationfilter.NonPublic(**) *;\"}]}}}}",
                results -> {

                    int expectedCount = NUMBER_OF_ELEMENTS_ON_ANNOTATION + 4 //@NonPublic + since() + @Retention, @Target
                            + NUMBER_OF_ELEMENTS_ON_ANNOTATION + 3 //@Public + @Retention, @Target on it
                            + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //PublicClass, PublicClass(), @Public
                            + NUMBER_OF_ELEMENTS_ON_OBJECT + 2 //PublicClass.PublicInnerClass, PublicClass.PublicInnerClass()
                            + 1 //PublicClass.f
                            + 1 //PublicClass.m()
                            + NUMBER_OF_ELEMENTS_ON_OBJECT + 2 //UndecisiveClass, UndecisiveClass()
                            + 1 //UndecisiveClass.f
                            + 1 //UndecisiveClass.m()
                            ;

                    Assert.assertEquals(expectedCount, results.size());
                    assertNotContains(results.stream().map(Element::getFullHumanReadableString).collect(toList()),
                            "class annotationfilter.NonPublicClass", "field annotationfilter.NonPublicClass.f",
                            "method void annotationfilter.NonPublicClass::m()",
                            "method void annotationfilter.PublicClass::implDetail()",
                            "class annotationfilter.PublicClass.NonPublicInnerClass");
                });
    }

    @Test
   public void testExcludeByAnnotationWithAttributeValues() throws Exception {
        testWith("{\"revapi\":{\"filter\":{\"elements\":{\"exclude\":" +
                "[{\"matcher\": \"matcher.java\", \"match\": \"@annotationfilter.NonPublic(since = '2.0') *;\"}]}}}}", results -> {

            int expectedCount = NUMBER_OF_ELEMENTS_ON_ANNOTATION + 3 //@NonPublic, @Target, @Retention
                    + 1 //@NonPublic.since()
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //NonPublicClass, NonPublicClass(), @NonPublic
                    + 1 //NonPublicClass.f
                    + 2 //NonPublicClass.m(), @Public
                    + NUMBER_OF_ELEMENTS_ON_ANNOTATION + 3 //@Public, @Target, @Retention
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //PublicClass, PublicClass(), @Public
                    + 1 //PublicClass.f
                    + 1 //PublicClass.m()
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //PublicClass.NonPublicInnerClass, PublicClass.NonPublicInnerClass(), @NonPublic
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 2 //PublicClass.PublicInnerClass, PublicClass.PublicInnerClass()
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 2 //UndecisiveClass, UndecisiveClass()
                    + 1 //UndecisiveClass.f
                    + 1 //UndecisiveClass.m()
                    ;

            Assert.assertEquals(expectedCount, results.size());
            assertNotContains(results.stream().map(Element::getFullHumanReadableString).collect(toList()),
                    "method void annotationfilter.PublicClass::implDetail()");
        });
    }

    @Test
    public void testIncludeByAnnotationPresence() throws Exception {
        testWith("{\"revapi\":{\"filter\":{\"elements\":{\"include\":" +
                "[{\"matcher\": \"matcher.java\", \"match\": \"@annotationfilter.Public *;\"}]}}}}", results -> {

            int expectedCount = 2 //NonPublicClass.m(), @Public
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //PublicClass, PublicClass(), @Public
                    + 1 //PublicClass.f
                    + 1 //PublicClass.m()
                    + 2 //PublicClass.implDetail(), @NonPublic
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //PublicClass.NonPublicInnerClass, PublicClass.NonPublicInnerClass(), @NonPublic
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 2 //PublicClass.PublicInnerClass, PublicClass.PublicInnerClass()
                    ;

            Assert.assertEquals(expectedCount, results.size());
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
        testWith("{\"revapi\":{\"filter\":{\"elements\":{\"include\":" +
                "[{\"matcher\": \"matcher.java\", \"match\": \"@annotationfilter.NonPublic(since = /2\\.0/) *;\"}]}}}}", results -> {

            Assert.assertEquals(2, results.size());
            Assert.assertEquals("method void annotationfilter.PublicClass::implDetail()",
                    results.get(0).getFullHumanReadableString());
        });
    }

    @Test
    public void testIncludeAndExclude() throws Exception {
        testWith("{\"revapi\":{\"filter\":{\"elements\":{\"exclude\":" +
                "[{\"matcher\": \"matcher.java\", \"match\": \"@annotationfilter.NonPublic(**) *;\"}]," +
                "\"include\": [{\"matcher\": \"matcher.java\", \"match\": \"@annotationfilter.Public(**) *;\"}]}}}}", results
                -> {

            int expectedCount = 2 //NonPublicClass.m(), @Public
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 3 //PublicClass, PublicClass(), @Public
                    + NUMBER_OF_ELEMENTS_ON_OBJECT + 2 //PublicClass.PublicInnerClass, PublicClass.PublicInnerClass()
                    + 1 //PublicClass.f
                    + 1 //PublicClass.m()
                    ;

            Assert.assertEquals(expectedCount, results.size());
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

    @Test
    public void testChangesReportedOnAnnotationElements() throws Exception {
        CollectingReporter reporter = runAnalysis(CollectingReporter.class,
                "{\"revapi\": {\"filter\": {\"elements\": {\"include\":[" +
                        "{\"matcher\": \"matcher.java\", \"match\": \"@Attributes.Anno(**) ^*;\"}]}}}}",
                "v1/annotations/Attributes.java", "v2/annotations/Attributes.java");

        List<Report> reports = reporter.getReports();

        Assert.assertEquals(2, reports.size());
        Report parameterChange = reports.stream().filter(r -> r.getNewElement() instanceof MethodParameterElement)
                .findFirst().orElse(null);
        Report annotationChanges = reports.stream().filter(r -> r.getNewElement() instanceof MethodElement)
                .findFirst().orElse(null);

        Assert.assertEquals(1, parameterChange.getDifferences().size());
        Assert.assertEquals("java.method.parameterTypeChanged", parameterChange.getDifferences().get(0).code);

        Assert.assertEquals(3, annotationChanges.getDifferences().size());
        Assert.assertEquals(new HashSet<>(Arrays.asList("java.annotation.attributeValueChanged",
                "java.annotation.attributeAdded", "java.element.nowDeprecated")),
                annotationChanges.getDifferences().stream().map(d -> d.code).collect(Collectors.toSet()));
    }

    private void testWith(String configJSON, Consumer<List<Element>> test) throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "annotationfilter/NonPublic.java",
                "annotationfilter/NonPublicClass.java", "annotationfilter/Public.java",
                "annotationfilter/PublicClass.java", "annotationfilter/UndecisiveClass.java");

        try {
            JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(
                    new API(Arrays.asList(new ShrinkwrapArchive(archive.archive)), null),
                    Collections.emptyList(), Executors.newSingleThreadExecutor(), null, false
            );

            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());

            Revapi r = new Revapi(PipelineConfiguration.builder()
                    .withFilters(ConfigurableElementFilter.class)
                    .withMatchers(JavaElementMatcher.class)
                    .build());

            AnalysisContext ctx = AnalysisContext.builder(r).withConfigurationFromJSON(configJSON).build();
            AnalysisContext filterCtx =
                    r.prepareAnalysis(ctx).getFirstConfigurationOrNull(ConfigurableElementFilter.class);

            ConfigurableElementFilter filter = new ConfigurableElementFilter();
            filter.initialize(filterCtx);

            List<Element> results = forest.search(Element.class, true, filter.filterFor(analyzer), null);

            analyzer.getCompilationValve().removeCompiledResults();

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

    private static int getNumberOfAnnotationsOn(AnnotatedElement element) {
        return element.getDeclaredAnnotations().length;
    }

    private static int getNumberOfChildElements(Class<?> clazz) {
        Function<Integer, Boolean> accessibleElements = mods -> Modifier.isPublic(mods) || Modifier.isProtected(mods);

        int cnt = getNumberOfAnnotationsOn(clazz);
        cnt += Stream.of(clazz.getDeclaredClasses()).filter(c -> accessibleElements.apply(c.getModifiers()))
                .mapToInt(cl -> getNumberOfChildElements(cl) + 1).sum();

        Set<String> javaLangObjectMethods = new HashSet<>();

        ToIntFunction<Method> countMethod = m -> {
            int mcnt = getNumberOfAnnotationsOn(m);
            mcnt += m.getParameterCount();

            mcnt += Stream.of(m.getParameterAnnotations()).mapToInt(as -> as.length).sum();

            return mcnt + 1; //+1 for the method itself
        };

        if (!clazz.equals(Object.class)) {
            // add the methods from object to the number
            cnt += Stream.of(Object.class.getDeclaredMethods()).filter(c -> accessibleElements.apply(c.getModifiers()))
                    .peek(m -> javaLangObjectMethods.add(m.getName()))
                    .mapToInt(countMethod).sum();
        }

        cnt += Stream.of(clazz.getDeclaredMethods()).filter(c -> accessibleElements.apply(c.getModifiers()))
                .filter(m -> !javaLangObjectMethods.contains(m.getName()))
                .mapToInt(countMethod).sum();

        cnt += Stream.of(clazz.getDeclaredFields()).filter(c -> accessibleElements.apply(c.getModifiers()))
                .mapToInt(f -> getNumberOfAnnotationsOn(f) + 1).sum();

        return cnt;
    }
}
