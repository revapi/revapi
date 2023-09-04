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
package org.revapi.basic;

import static java.util.Collections.emptySet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.ElementMatcher;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.base.BaseElement;
import org.revapi.configuration.ConfigurationValidator;
import org.revapi.configuration.ValidationResult;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public class ConfigurableElementFilterTest {

    private DummyElement el1;
    private DummyElement el2;

    @Before
    public void setupElements() {
        Archive ar1 = new DummyArchive("archive");
        Archive ar2 = new DummyArchive("brchive");
        el1 = new DummyElement("el1", ar1);
        el2 = new DummyElement("el2", ar2);
    }

    @Test
    public void testInvalidConfig_noFilters() throws Exception {
        ConfigurationValidator validator = new ConfigurationValidator();

        String json = "[{\"extension\": \"revapi.filter\", \"configuration\" : { }}]";

        ValidationResult result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());

        Assert.assertFalse(result.isSuccessful());
    }

    @Test
    public void testInvalidConfig_noDefsForFilter() throws Exception {
        ConfigurationValidator validator = new ConfigurationValidator();

        String json = "[{\"extension\": \"revapi.filter\", \"configuration\" : { \"elements\" : { \"include\" : [] }}}]";
        ValidationResult result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());
        Assert.assertFalse(result.isSuccessful());

        json = "[{\"extension\": \"revapi.filter\", \"configuration\": { \"elements\" : { \"exclude\" : [] }}}]";
        result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());
        Assert.assertFalse(result.isSuccessful());

        json = "[{\"extension\": \"revapi.filter\", \"configuration\": { \"archives\" : { \"exclude\" : {} }}}]";
        result = validator.validate(ModelNode.fromJSONString(json), new ConfigurableElementFilter());
        Assert.assertFalse(result.isSuccessful());
    }

    @Test
    public void testInclusionByArchive() {
        testWithConfig("{\"archives\": {" + "\"include\": [\"archive\"]" + "}}", results -> {
            // undecided because that's the default include - to include an archive doesn't mean to automatically
            // include everything inside it
            assertEquals(Ternary.UNDECIDED, results.get(el1));
            assertEquals(Ternary.FALSE, results.get(el2));
        });
    }

    @Test
    public void testExclusionByArchive() {
        testWithConfig("{\"archives\": {" + "\"exclude\": [\"archive\"]" + "}}", results -> {
            assertEquals(Ternary.FALSE, results.get(el1));
            assertEquals(Ternary.UNDECIDED, results.get(el2));
        });
    }

    @Test
    public void testInclusionByElementRegex() {
        testWithConfig("{\"elements\": {" + "\"include\": [\"el1\"]" + "}}", results -> {
            assertEquals(Ternary.TRUE, results.get(el1));
            assertEquals(Ternary.FALSE, results.get(el2));
        });
    }

    @Test
    public void testExclusionByElementRegex() {
        testWithConfig("{\"elements\": {" + "\"exclude\": [\"el1\"]" + "}}", results -> {
            assertEquals(Ternary.FALSE, results.get(el1));
            assertEquals(Ternary.TRUE, results.get(el2));
        });
    }

    @Test
    public void testInclusionByMatch() {
        testWithConfig("{\"elements\": {" + "\"include\": [{\"matcher\": \"exact\", \"match\": \"el1\"}]" + "}}",
                setOf(new ExactElementMatcher()), results -> {
                    assertEquals(Ternary.TRUE, results.get(el1));
                    assertEquals(Ternary.FALSE, results.get(el2));
                });
    }

    @Test
    public void testExclusionByMatch() {
        testWithConfig("{\"elements\": {" + "\"exclude\": [{\"matcher\": \"regex\", \"match\": \"e.1\"}]" + "}}",
                setOf(new RegexElementMatcher()), results -> {
                    assertEquals(Ternary.FALSE, results.get(el1));
                    assertEquals(Ternary.TRUE, results.get(el2));
                });
    }

    @Test
    public void exclusionOverride() throws Exception {
        String configJSON = "{" + "\"elements\": {" + "\"exclude\": [\"el1\"]," + "\"include\": [\"el2\", \"el11\"]"
                + "}}";

        ConfigurableElementFilter filter = new ConfigurableElementFilter();

        AnalysisContext ctx = AnalysisContext.builder().build()
                .copyWithConfiguration(new ObjectMapper().readTree(configJSON));

        filter.initialize(ctx);

        Optional<TreeFilter<DummyElement>> of = filter.filterFor(null);
        assertTrue(of.isPresent());
        TreeFilter<DummyElement> f = of.get();

        DummyElement el11 = new DummyElement("el11", el1.getArchive());
        el1.getChildren().add(el11);

        Map<DummyElement, Ternary> ret = new HashMap<>();

        FilterStartResult sr = f.start(el1);
        ret.put(el1, sr.getMatch());

        addElementResults(f, el11, ret);

        Ternary fr = f.finish(el1).getMatch();
        ret.put(el1, fr);

        addElementResults(f, el2, ret);

        f.finish().forEach((e, r) -> ret.put(e, r.getMatch()));

        assertSame(Ternary.FALSE, ret.get(el1));
        assertSame(Ternary.TRUE, ret.get(el11));
        assertSame(Ternary.TRUE, ret.get(el2));
    }

    private void testWithConfig(String configJSON, Consumer<Map<DummyElement, Ternary>> test) {
        testWithConfig(configJSON, emptySet(), test);
    }

    private void testWithConfig(String configJSON, Set<ElementMatcher> matchers,
            Consumer<Map<DummyElement, Ternary>> test) {
        ConfigurableElementFilter filter = new ConfigurableElementFilter();

        AnalysisContext ctx = AnalysisContext.builder().build()
                .copyWithConfiguration(ModelNode.fromJSONString(configJSON)).copyWithMatchers(matchers);

        filter.initialize(ctx);

        Optional<TreeFilter<DummyElement>> of = filter.filterFor(null);
        assertTrue(of.isPresent());
        TreeFilter<DummyElement> f = of.get();

        Map<DummyElement, Ternary> ret = new HashMap<>();
        addElementResults(f, el1, ret);
        addElementResults(f, el2, ret);

        f.finish().forEach((e, r) -> ret.put(e, r.getMatch()));

        test.accept(ret);
    }

    private static void addElementResults(TreeFilter<DummyElement> f, DummyElement el,
            Map<DummyElement, Ternary> results) {
        FilterStartResult sr = f.start(el);
        results.put(el, sr.getMatch());

        Ternary fr = f.finish(el).getMatch();
        results.put(el, fr);
    }

    private <T> Set<T> setOf(T... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static final class DummyArchive implements Archive {
        final String name;

        private DummyArchive(String name) {
            this.name = name;
        }

        @Nonnull
        @Override
        public String getName() {
            return name;
        }

        @Nonnull
        @Override
        public InputStream openStream() throws IOException {
            throw new UnsupportedOperationException();
        }
    }

    private static final class DummyElement extends BaseElement<DummyElement> {
        final String name;

        DummyElement(String name, Archive archive) {
            super(null, archive);
            this.name = name;
        }

        @Nonnull
        @Override
        public API getApi() {
            throw new UnsupportedOperationException();
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }

        @Override
        public int compareTo(DummyElement o) {
            return 0;
        }
    }
}
