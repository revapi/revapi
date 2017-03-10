/*
 * Copyright 2017 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi;

import java.io.Reader;
import java.io.StringReader;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.revapi.simple.SimpleReporter;

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
        ModelNode newCfg = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"configuration\": 1}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi).withConfigurationFromJSON(oldCfg).build();

        Assert.assertEquals(newCfg, ctx.getConfiguration());
    }

    @Test
    public void testConfigurationHandling_setNewStyle() throws Exception {
        Dummy.schema = "{\"type\": \"integer\"}";
        Dummy.extensionId = "ext";
        ModelNode newCfg = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"configuration\": 1}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi).withConfiguration(newCfg).build();

        Assert.assertEquals(newCfg, ctx.getConfiguration());
    }

    @Test
    public void testConfigurationHandling_mergeNewStyle() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        ModelNode cfg1 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        ModelNode cfg2 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"configuration\": {\"b\": [\"y\"]}}]");
        ModelNode newCfg = ModelNode.fromJSONString(
                "[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfiguration());
    }

    @Test
    public void testConfigurationHandling_mergeNewStyle_withIds() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        ModelNode cfg1 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        ModelNode cfg2 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"b\": [\"y\"]}}," +
                "{\"extension\": \"ext\", \"id\": \"b\", \"configuration\": {\"b\": [\"y\"]}}]");
        ModelNode newCfg = ModelNode.fromJSONString(
                "[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}," +
                        "{\"extension\": \"ext\", \"id\": \"b\", \"configuration\": {\"b\": [\"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfiguration());
    }

    @Test
    public void testConfigurationHandling_mergeOldStyle() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        ModelNode cfg1 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        ModelNode cfg2 = ModelNode.fromJSONString("{\"ext\": {\"b\": [\"y\"]}}");
        ModelNode newCfg = ModelNode.fromJSONString(
                "[{\"extension\": \"ext\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfiguration());
    }

    @Test
    public void testConfigurationHandling_mergeOldStyle_withSingleId() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        ModelNode cfg1 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}]");
        ModelNode cfg2 = ModelNode.fromJSONString("{\"ext\": {\"b\": [\"y\"]}}");
        ModelNode newCfg = ModelNode.fromJSONString(
                "[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\", \"y\"]}}]");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(newCfg, ctx.getConfiguration());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConfigurationHandling_mergeOldStyle_withMultipleIds() throws Exception {
        Dummy.schema = "{\"type\": \"object\", \"properties\": {\"a\": {\"type\": \"integer\"}," +
                " \"b\": {\"type\": \"array\", \"items\": {\"type\": \"string\"}}}}";
        Dummy.extensionId = "ext";
        ModelNode cfg1 = ModelNode.fromJSONString("[{\"extension\": \"ext\", \"id\": \"a\", \"configuration\": {\"a\": 1, \"b\": [\"x\"]}}," +
                "{\"extension\": \"ext\", \"id\": \"b\", \"configuration\": {\"b\": [\"y\"]}}]");
        ModelNode cfg2 = ModelNode.fromJSONString("{\"ext\": {\"b\": [\"y\"]}}");

        Revapi revapi = Revapi.builder().withAnalyzers(Dummy.class).withReporters(SimpleReporter.class).build();

        AnalysisContext ctx = AnalysisContext.builder(revapi)
                .withConfiguration(cfg1)
                .mergeConfiguration(cfg2)
                .build();

        Assert.assertEquals(new ModelNode(), ctx.getConfiguration());
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
