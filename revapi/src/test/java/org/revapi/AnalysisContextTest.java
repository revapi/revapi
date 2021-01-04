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
package org.revapi;

import java.io.Reader;
import java.io.StringReader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.base.BaseReporter;
import org.revapi.configuration.JSONUtil;

/**
 * @author Lukas Krejci
 * @since 0.8.0
 */
public class AnalysisContextTest {

    @Test
    public void testConfigurationHandling_convertOldStyle() throws Exception {
        Dummy.schema = "{\"type\": \"integer\"}";
        Dummy.extensionId = "ext";
        String oldCfg = "{\"ext\": 1}";
        JsonNode newCfg = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": 1}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi).withConfigurationFromJSON(oldCfg).build();

        Assert.assertEquals(newCfg, ctx.getConfigurationNode());
    }

    @Test
    public void testConfigurationHandling_setNewStyle() throws Exception {
        Dummy.schema = "{\"type\": \"integer\"}";
        Dummy.extensionId = "ext";
        JsonNode newCfg = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": 1}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi).withConfiguration(newCfg).build();

        Assert.assertEquals(newCfg, ctx.getConfigurationNode());
    }

    @Test
    public void testConfigurationHandling_mergeNewStyle() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        JsonNode cfg1 = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        JsonNode cfg2 = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": {\"b\": [\"y\"]}}]");
        JsonNode newCfg = JSONUtil.parse(
                "[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfigurationNode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationHandling_mergeNewStyle_singleMasterWithMultipleIdlessMergers() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        JsonNode cfg1 = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        JsonNode cfg2 = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": {\"b\": [\"y\"]}}," +
                "{\"extension\": \"ext\", \"configuration\": {\"b\": [\"z\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();
    }

    @Test
    public void testConfigurationHandling_mergeNewStyle_withIds() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        JsonNode cfg1 = JSONUtil.parse("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        JsonNode cfg2 = JSONUtil.parse("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"b\": [\"y\"]}}," +
                "{\"extension\": \"ext\", \"id\": \"b\", \"configuration\": {\"b\": [\"y\"]}}]");
        JsonNode newCfg = JSONUtil.parse(
                "[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}," +
                        "{\"extension\": \"ext\", \"id\": \"b\", \"configuration\": {\"b\": [\"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfigurationNode());
    }

    @Test
    public void testConfigurationHandling_mergeOldStyle() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        JsonNode cfg1 = JSONUtil.parse("[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        JsonNode cfg2 = JSONUtil.parse("{\"ext\": {\"b\": [\"y\"]}}");
        JsonNode newCfg = JSONUtil.parse(
                "[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfigurationNode());
    }

    @Test
    public void testConfigurationHandling_mergeOldStyle_withSingleId() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        JsonNode cfg1 = JSONUtil.parse("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        JsonNode cfg2 = JSONUtil.parse("{\"ext\": {\"b\": [\"y\"]}}");
        JsonNode newCfg = JSONUtil.parse(
                "[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfigurationNode());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationHandling_mergeOldStyle_withMultipleIds() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        JsonNode cfg1 = JSONUtil.parse("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}," +
                "{\"extension\": \"ext\", \"id\": \"b\", \"configuration\": {\"b\": [\"y\"]}}]");
        JsonNode cfg2 = JSONUtil.parse("{\"ext\": {\"b\": [\"y\"]}}");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(TestReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(new ModelNode(), ctx.getConfigurationNode());
    }

    public static final class TestReporter extends BaseReporter {
        @Override
        public String getExtensionId() {
            return "reporter";
        }

        @Override
        public void report(@Nonnull Report report) {
        }
    }

    public static final class Dummy implements ApiAnalyzer {
        static String extensionId;
        static String schema;

        @Override public void close() throws Exception {
        }

        @Nullable @Override public String getExtensionId() {
            return extensionId;
        }

        @Nullable @Override public Reader getJSONSchema() {
            return new StringReader(schema);
        }

        @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
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
    }
}
