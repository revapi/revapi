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
package org.revapi.basic;

import static java.util.Collections.emptySet;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.ElementGateway;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;
import org.revapi.configuration.ConfigurationValidator;
import org.revapi.configuration.ValidationResult;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ConfigurableElementFilterTest {

    private Element el1;
    private Element el2;

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
    public void testInclusionByArchive() throws Exception {
        testWithConfig("{\"archives\": {" +
                "\"include\": [\"archive\"]" +
                "}}", filter -> {
            assertEquals(FilterMatch.MATCHES, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el1)
                    .getMatch());
            assertEquals(FilterMatch.DOESNT_MATCH, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el2)
                    .getMatch());
        });
    }

    @Test
    public void testExclusionByArchive() throws Exception {
        testWithConfig("{\"archives\": {" +
                "\"exclude\": [\"archive\"]" +
                "}}", filter -> {

            assertEquals(FilterMatch.DOESNT_MATCH, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el1)
                    .getMatch());
            assertEquals(FilterMatch.MATCHES, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el2)
                    .getMatch());
        });
    }

    @Test
    public void testInclusionByElementRegex() throws Exception {
        testWithConfig("{\"elements\": {" +
                "\"include\": [\"el1\"]" +
                "}}", filter -> {

            assertEquals(FilterMatch.MATCHES, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el1)
                    .getMatch());
            assertEquals(FilterMatch.DOESNT_MATCH, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el2)
                    .getMatch());
        });
    }

    @Test
    public void testExclusionByElementRegex() throws Exception {
        testWithConfig("{\"elements\": {" +
                "\"exclude\": [\"el1\"]" +
                "}}", filter -> {

            assertEquals(FilterMatch.DOESNT_MATCH, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el1)
                    .getMatch());
            assertEquals(FilterMatch.MATCHES, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el2)
                    .getMatch());
        });
    }

    @Test
    public void testInclusionByMatch() throws Exception {
        testWithConfig("{\"elements\": {" +
                "\"include\": [{\"matcher\": \"matcher.exact\", \"recipe\": \"el1\"}]" +
                "}}", setOf(new ExactElementMatcher()), filter -> {

            assertEquals(FilterMatch.MATCHES, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el1)
                    .getMatch());
            assertEquals(FilterMatch.DOESNT_MATCH, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el2)
                    .getMatch());
        });
    }

    @Test
    public void testExclusionByMatch() throws Exception {
        testWithConfig("{\"elements\": {" +
                "\"exclude\": [{\"matcher\": \"matcher.regex\", \"recipe\": \"e.1\"}]" +
                "}}", setOf(new RegexElementMatcher()), filter -> {

            assertEquals(FilterMatch.DOESNT_MATCH, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el1)
                    .getMatch());
            assertEquals(FilterMatch.MATCHES, filter.filter(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, el2)
                    .getMatch());
        });
    }

    private void testWithConfig(String configJSON, Consumer<ConfigurableElementFilter> test) {
        testWithConfig(configJSON, emptySet(), test);
    }

    private void testWithConfig(String configJSON, Set<ElementMatcher> matchers,
            Consumer<ConfigurableElementFilter> test) {
        ConfigurableElementFilter filter = new ConfigurableElementFilter();

        AnalysisContext ctx = AnalysisContext.builder().build()
                .copyWithConfiguration(ModelNode.fromJSONString(configJSON))
                .copyWithMatchers(matchers);

        filter.initialize(ctx);

        test.accept(filter);
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

    private static final class DummyElement extends SimpleElement {
        final Archive archive;
        final String name;

        DummyElement(Archive archive) {
            this("", archive);
        }

        DummyElement(String name, Archive archive) {
            this.archive = archive;
            this.name = name;
        }

        @Nonnull
        @Override
        public API getApi() {
            throw new UnsupportedOperationException();
        }

        @Nullable
        @Override
        public Archive getArchive() {
            return archive;
        }

        @Override
        public int compareTo(Element o) {
            return 0;
        }

        @Nonnull
        @Override
        public String getFullHumanReadableString() {
            return name;
        }
    }
}
