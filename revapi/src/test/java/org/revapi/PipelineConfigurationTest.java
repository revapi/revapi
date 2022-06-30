/*
 * Copyright 2014-2022 Lukas Krejci
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

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.Test;
import org.revapi.AnalysisResult.ExtensionInstance;
import org.revapi.configuration.JSONUtil;

public class PipelineConfigurationTest {

    private final ByteBuddy byteBuddy = new ByteBuddy();

    @SuppressWarnings("unchecked")
    private final Class<? extends ApiAnalyzer<?>> analyzerType = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Analyzer").implement(ApiAnalyzer.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("a")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends Reporter> reporterType = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Reporter").implement(Reporter.class).method(ElementMatchers.named("getExtensionId"))
            .intercept(FixedValue.value("r")).make().load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends ElementFilter> filter1Type = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Filter1").implement(ElementFilter.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("f1")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends ElementFilter> filter2Type = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Filter2").implement(ElementFilter.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("f2")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends ElementFilter> filter3Type = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Filter3").implement(ElementFilter.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("f3")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends DifferenceTransform<?>> transform1Type = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Transform1").implement(DifferenceTransform.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("t1")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends DifferenceTransform<?>> transform2Type = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Transform2").implement(DifferenceTransform.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("t2")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @SuppressWarnings("unchecked")
    private final Class<? extends DifferenceTransform<?>> transform3Type = (Class) byteBuddy.subclass(Object.class)
            .name("test.extensions.Transform3").implement(DifferenceTransform.class)
            .method(ElementMatchers.named("getExtensionId")).intercept(FixedValue.value("t3")).make()
            .load(getClass().getClassLoader()).getLoaded();

    @Test
    public void testEmptyConfigIncludesAllExtensions() {
        PipelineConfiguration cfg = configWithAllExtensions("{}");

        Revapi revapi = new Revapi(cfg);

        AnalysisResult.Extensions exts = revapi.prepareAnalysis(AnalysisContext.builder().build());

        assertContainsExactlyInstances(fromExtension(exts.getAnalyzers()), analyzerType);
        assertContainsExactlyInstances(fromExtension(exts.getFilters()), filter1Type, filter2Type, filter3Type);
        assertContainsExactlyInstances(fromExtension(exts.getTransforms()), transform1Type, transform2Type,
                transform3Type);
        assertContainsExactlyInstances(fromExtension(exts.getReporters()), reporterType);
        assertTrue(cfg.getTransformationBlocks().isEmpty());
    }

    @Test
    public void testOnlyExplicitlyIncludedExtensionsAreConfigured() {
        PipelineConfiguration cfg = configWithAllExtensions("{\"filters\": {\"include\": [\"f1\"]}}");

        Revapi revapi = new Revapi(cfg);

        AnalysisResult.Extensions exts = revapi.prepareAnalysis(AnalysisContext.builder().build());

        assertContainsExactlyInstances(fromExtension(exts.getAnalyzers()), analyzerType);
        assertContainsExactlyInstances(fromExtension(exts.getFilters()), filter1Type);
        assertContainsExactlyInstances(fromExtension(exts.getTransforms()), transform1Type, transform2Type,
                transform3Type);
        assertContainsExactlyInstances(fromExtension(exts.getReporters()), reporterType);
        assertTrue(cfg.getTransformationBlocks().isEmpty());
    }

    @Test
    public void testExplicitlyExcludedExtensionsNotConfigured() {
        PipelineConfiguration cfg = configWithAllExtensions("{\"filters\": {\"exclude\": [\"f1\"]}}");

        Revapi revapi = new Revapi(cfg);

        AnalysisResult.Extensions exts = revapi.prepareAnalysis(AnalysisContext.builder().build());

        assertContainsExactlyInstances(fromExtension(exts.getAnalyzers()), analyzerType);
        assertContainsExactlyInstances(fromExtension(exts.getFilters()), filter2Type, filter3Type);
        assertContainsExactlyInstances(fromExtension(exts.getTransforms()), transform1Type, transform2Type,
                transform3Type);
        assertContainsExactlyInstances(fromExtension(exts.getReporters()), reporterType);
        assertTrue(cfg.getTransformationBlocks().isEmpty());
    }

    @Test
    public void testExcludeRemovesFromInclude() {
        PipelineConfiguration cfg = configWithAllExtensions(
                "{\"transforms\": {\"include\": [\"t1\", \"t2\"], \"exclude\": [\"t1\"]}}");

        Revapi revapi = new Revapi(cfg);

        AnalysisResult.Extensions exts = revapi.prepareAnalysis(AnalysisContext.builder().build());

        assertContainsExactlyInstances(fromExtension(exts.getAnalyzers()), analyzerType);
        assertContainsExactlyInstances(fromExtension(exts.getFilters()), filter1Type, filter2Type, filter3Type);
        assertContainsExactlyInstances(fromExtension(exts.getTransforms()), transform2Type);
        assertContainsExactlyInstances(fromExtension(exts.getReporters()), reporterType);
        assertTrue(cfg.getTransformationBlocks().isEmpty());
    }

    @Test
    public void testTransformationBlocksAsAllDescendsOfATree() {
        PipelineConfiguration cfg = configWithAllExtensions(
                "{\"transformBlocks\": [[\"t1\", \"t2\", \"t2\"], [\"e3\", \"e1\"]]}");

        Revapi revapi = new Revapi(cfg);

        AnalysisResult.Extensions exts = revapi.prepareAnalysis(AnalysisContext.builder()
                .withConfigurationFromJSON("[" + "{\"extension\": \"t1\", \"id\": \"e1\", \"configuration\": null},"
                        + "{\"extension\": \"t2\", \"id\": \"e2\", \"configuration\": null},"
                        + "{\"extension\": \"t2\", \"id\": \"e3\", \"configuration\": null}" + "]")
                .build());

        Set<List<DifferenceTransform<?>>> blocks = Revapi.groupTransformsToBlocks(exts, cfg);
        DifferenceTransform<?> e1 = exts.getTransforms().keySet().stream().filter(i -> "e1".equals(i.getId()))
                .map(ExtensionInstance::getInstance).findFirst().get();
        DifferenceTransform<?> e2 = exts.getTransforms().keySet().stream().filter(i -> "e2".equals(i.getId()))
                .map(ExtensionInstance::getInstance).findFirst().get();
        DifferenceTransform<?> e3 = exts.getTransforms().keySet().stream().filter(i -> "e3".equals(i.getId()))
                .map(ExtensionInstance::getInstance).findFirst().get();
        DifferenceTransform<?> t3 = exts.getFirstExtension(transform3Type, null);

        assertEquals(6, blocks.size());
        assertTrue(blocks.stream().anyMatch(b -> containsInstancesInOrder(b, e1, e2, e2)));
        assertTrue(blocks.stream().anyMatch(b -> containsInstancesInOrder(b, e1, e2, e3)));
        assertTrue(blocks.stream().anyMatch(b -> containsInstancesInOrder(b, e1, e3, e2)));
        assertTrue(blocks.stream().anyMatch(b -> containsInstancesInOrder(b, e1, e3, e3)));
        assertTrue(blocks.stream().anyMatch(b -> containsInstancesInOrder(b, e3, e1)));
        assertTrue(blocks.stream().anyMatch(b -> containsInstancesInOrder(b, t3)));
    }

    private PipelineConfiguration configWithAllExtensions(String configJson) {
        return PipelineConfiguration.parse(JSONUtil.parse(configJson), singleton(analyzerType),
                asList(filter1Type, filter2Type, filter3Type), asList(transform1Type, transform2Type, transform3Type),
                singleton(reporterType), emptySet());
    }

    private <T> Collection<T> fromExtension(Map<ExtensionInstance<T>, ?> exts) {
        return exts.keySet().stream().map(ExtensionInstance::getInstance).collect(Collectors.toList());
    }

    @SafeVarargs
    private final <T> void assertContainsExactlyInstances(Collection<T> all, Class<? extends T>... expected) {
        assertEquals(expected.length, all.size());
        for (Class<?> c : expected) {
            assertContainsInstance(c, all);
        }
    }

    @SafeVarargs
    private final <T> boolean containsInstancesInOrder(Collection<T> all, T... expected) {
        if (expected.length != all.size()) {
            return false;
        }
        Iterator<T> allIt = all.iterator();
        for (T i : expected) {
            if (allIt.next() != i) {
                return false;
            }
        }

        return true;
    }

    private void assertContainsInstance(Class<?> c, Collection<?> xs) {
        assertTrue(xs.stream().anyMatch(t -> c.isAssignableFrom(t.getClass())));
    }
}
