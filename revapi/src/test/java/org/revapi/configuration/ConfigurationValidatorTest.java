/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.configuration;

import java.io.Reader;
import java.io.StringReader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.CorrespondenceComparatorDeducer;
import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.Report;
import org.revapi.Reporter;
import org.revapi.Revapi;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class ConfigurationValidatorTest {

    private ValidationResult test(String object, final String extensionId, final String schema) {
        ConfigurationValidator validator = new ConfigurationValidator();

        JsonNode fullConfig = JSONUtil.parse(object);

        Configurable fakeConfigurable = new Configurable() {
            @Nullable
            @Override
            public String getExtensionId() {
                return extensionId;
            }

            @Nullable
            @Override
            public Reader getJSONSchema() {
                return new StringReader(schema);
            }

            @Override
            public void initialize(@Nonnull AnalysisContext analysisContext) {
            }
        };

        return validator.validate(fullConfig, fakeConfigurable);
    }

    @Test
    public void testValidSchema() throws Exception {
        final String schema = "{" +
            "\"properties\" : {" +
            "   \"id\" : {" +
            "      \"type\" : \"integer\"" +
            "   }" +
            "}}";

        String object = "[{\"extension\": \"my-config\", \"configuration\" : {\"id\" : 3}}]";

        ValidationResult result = test(object, "my-config", schema);

        Assert.assertTrue(result.toString(), result.isSuccessful());
    }

    @Test
    public void testSingleFailure() throws Exception {
        final String schema = "{" +
            "\"properties\" : {" +
            "   \"id\" : {" +
            "      \"type\" : \"integer\"" +
            "   }" +
            "}}";

        String object = "[{\"extension\": \"my-config\", \"configuration\" : {\"id\" : \"3\"}}]";

        ValidationResult result = test(object, "my-config", schema);

        Assert.assertFalse(result.toString(), result.isSuccessful());
        Assert.assertEquals(1, result.getErrors().length);
    }

    @Test
    public void testMultipleFailures() throws Exception {
        final String schema = "{" +
            "\"properties\" : {" +
            "   \"id\" : {" +
            "      \"type\" : \"integer\"" +
            "   }," +
            "   \"kachna\" : {" +
            "       \"type\" : \"string\"" +
            "   }" +
            "}}";

        String object = "[{\"extension\": \"my-config\", \"configuration\" : {\"id\" : \"3\", \"kachna\" : 42}}]";

        ValidationResult result = test(object, "my-config", schema);

        Assert.assertFalse(result.toString(), result.isSuccessful());
        Assert.assertEquals(2, result.getErrors().length);
    }

    @Test
    public void testMultipleConfigs() throws Exception {
        String schema = "{" +
                "\"properties\" : {" +
                "   \"id\" : {" +
                "      \"type\" : \"integer\"" +
                "   }," +
                "   \"kachna\" : {" +
                "       \"type\" : \"string\"" +
                "   }" +
                "}}";

        String config = "[" +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 3, \"kachna\": \"duck\"}}," +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 4, \"kachna\": \"no duck\"}}," +
                "{\"extension\": \"other-config\", \"configuration\": 1}" +
                "]";

        ValidationResult result = test(config, "my-config", schema);

        Assert.assertTrue(result.toString(), result.isSuccessful());
    }

    @Test
    public void testRevapiValidation() throws Exception {
        String config = "[" +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 3, \"kachna\": \"duck\"}}," +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 4, \"kachna\": \"no duck\"}}," +
                "{\"extension\": \"other-config\", \"configuration\": 1}" +
                "]";

        Revapi revapi = Revapi.builder().withFilters(TestFilter.class).withReporters(TestReporter.class)
                .withAnalyzers(DummyApiAnalyzer.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi).withConfigurationFromJSON(config).build();

        ValidationResult res = revapi.validateConfiguration(ctx);

        Assert.assertFalse(res.isSuccessful());
        Assert.assertNotNull(res.getErrors());
        Assert.assertEquals(1, res.getErrors().length);
        Assert.assertEquals("$[2].configuration", res.getErrors()[0].dataPath);
    }

    @Test
    public void testRevapiValidation_mergeWithoutIds() throws Exception {
        //partial config of the extension
        String config1 = "[" +
                "{\"extension\": \"my-config\", \"configuration\": {\"id\": 3}}" +
                "]";

        //complete the config of the extension and add another config for another extension
        String config2 = "[" +
                "{\"extension\": \"my-config\", \"configuration\": {\"kachna\": \"no duck\"}}," +
                "{\"extension\": \"other-config\", \"configuration\": 1}" +
                "]";

        Revapi revapi = Revapi.builder().withFilters(TestFilter.class).withReporters(TestReporter.class)
                .withAnalyzers(DummyApiAnalyzer.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfigurationFromJSON(config1).mergeConfigurationFromJSON(config2).build();

        ValidationResult res = revapi.validateConfiguration(ctx);

        Assert.assertFalse(res.isSuccessful());
        Assert.assertNotNull(res.getErrors());
        //we merged "my-config" from the second config into the first, so that should be ok. Only other-config should error out.
        Assert.assertEquals(1, res.getErrors().length);
        Assert.assertEquals("$[1].configuration", res.getErrors()[0].dataPath);
    }

    @Test
    public void testRevapiValidation_mergeWithIds() throws Exception {
        String config1 = "[" +
                "{\"extension\": \"my-config\", \"id\": \"c1\", \"configuration\": {\"id\": 3, \"kachna\": \"duck\"}}" +
                "]";

        String config2 = "[" +
                "{\"extension\": \"my-config\", \"id\": \"c2\", \"configuration\": {\"id\": 4, \"kachna\": \"no duck\"}}," +
                "{\"extension\": \"other-config\", \"configuration\": 1}" +
                "]";

        Revapi revapi = Revapi.builder().withFilters(TestFilter.class).withReporters(TestReporter.class)
                .withAnalyzers(DummyApiAnalyzer.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfigurationFromJSON(config1).mergeConfigurationFromJSON(config2).build();

        ValidationResult res = revapi.validateConfiguration(ctx);

        Assert.assertFalse(res.isSuccessful());
        Assert.assertNotNull(res.getErrors());
        Assert.assertEquals(1, res.getErrors().length);
        Assert.assertEquals("$[2].configuration", res.getErrors()[0].dataPath);
    }

    public static final class TestFilter implements ElementFilter {
        private static final String SCHEMA = "{" +
                "\"properties\" : {" +
                "   \"id\" : {" +
                "      \"type\" : \"integer\"" +
                "   }," +
                "   \"kachna\" : {" +
                "       \"type\" : \"string\"" +
                "   }" +
                "}}";

        @Override public String getExtensionId() {
            return "my-config";
        }

        @Override public Reader getJSONSchema() {
            return new StringReader(SCHEMA);
        }

        @Override public void close() throws Exception {
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        }

        @Override public boolean applies(@Nullable Element<?> element) {
            return false;
        }

        @Override public boolean shouldDescendInto(@Nullable Object element) {
            return false;
        }
    }

    public static final class TestReporter implements Reporter {
        @Override public void report(@Nonnull Report report) {
        }

        @Override public void close() throws Exception {
        }

        @Nullable @Override public String getExtensionId() {
            return "other-config";
        }

        @Nullable @Override public Reader getJSONSchema() {
            return new StringReader("{\"type\": \"string\"}");
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }

    public static final class DummyApiAnalyzer implements ApiAnalyzer {

        @Nullable @Override public String getExtensionId() {
            return null;
        }

        @Nullable @Override public Reader getJSONSchema() {
            return null;
        }

        @Nonnull @Override public ArchiveAnalyzer getArchiveAnalyzer(@Nonnull API api) {
            return null;
        }

        @Nonnull @Override public DifferenceAnalyzer getDifferenceAnalyzer(@Nonnull ArchiveAnalyzer oldArchive,
                                                                           @Nonnull ArchiveAnalyzer newArchive) {
            return null;
        }

        @Nonnull @Override public CorrespondenceComparatorDeducer getCorrespondenceDeducer() {
            return null;
        }

        @Override public void close() throws Exception {
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }
}
