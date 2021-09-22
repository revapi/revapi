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
package org.revapi.maven;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementFilter;
import org.revapi.Revapi;
import org.revapi.configuration.JSONUtil;

public class AnalysisConfigurationGathererTest {

    @Test
    public void testReadingConfigurationFromJSON() throws Exception {
        PlexusConfiguration analysisConfiguration = new XmlPlexusConfiguration("analysisConfiguration");
        String json = "[{\"extension\": \"test\", \"configuration\": \"yes\"}]";
        analysisConfiguration.setValue(json);

        AnalysisConfigurationGatherer gatherer = new AnalysisConfigurationGatherer(analysisConfiguration, new Object[0],
                false, false, new PropertyValueResolver(new Properties()), null, null);

        Revapi revapi = Revapi.builder().withFilters(TestExtension.class).build();
        AnalysisContext.Builder ctxBld = AnalysisContext.builder();

        gatherer.gatherConfig(revapi, ctxBld);

        AnalysisContext ctx = ctxBld.build();
        ModelNode cfg = ctx.getConfiguration();
        ModelNode expected = ModelNode.fromJSONString(json);

        assertEquals(expected, cfg);
    }

    @Test
    public void testReadingConfigurationFromXML() throws Exception {
        PlexusConfiguration analysisConfiguration = new XmlPlexusConfiguration("analysisConfiguration");
        PlexusConfiguration testConfig = new XmlPlexusConfiguration("test");
        testConfig.setValue("yes");
        analysisConfiguration.addChild(testConfig);

        AnalysisConfigurationGatherer gatherer = new AnalysisConfigurationGatherer(analysisConfiguration, new Object[0],
                false, false, new PropertyValueResolver(new Properties()), null, null);

        Revapi revapi = Revapi.builder().withFilters(TestExtension.class).build();
        AnalysisContext.Builder ctxBld = AnalysisContext.builder();

        gatherer.gatherConfig(revapi, ctxBld);

        AnalysisContext ctx = ctxBld.build();
        ModelNode cfg = ctx.getConfiguration();

        String json = "[{\"extension\": \"test\", \"configuration\": \"yes\"}]";
        ModelNode expected = ModelNode.fromJSONString(json);

        assertEquals(expected, cfg);
    }

    @Test
    public void testReadingConfigurationFromFile() throws Exception {
        InputStream data = getClass().getResourceAsStream("/test-configuration-file.json");
        assertNotNull("Could not read the test configuration file.", data);
        File f = File.createTempFile("revapi-maven-plugin-test", null);
        Files.copy(data, f.toPath(), StandardCopyOption.REPLACE_EXISTING);

        try {
            AnalysisConfigurationGatherer gatherer = new AnalysisConfigurationGatherer(null,
                    new Object[] { f.getAbsolutePath() }, false, false, new PropertyValueResolver(new Properties()),
                    null, null);

            Revapi revapi = Revapi.builder().withFilters(TestExtension.class).build();
            AnalysisContext.Builder ctxBld = AnalysisContext.builder();

            gatherer.gatherConfig(revapi, ctxBld);

            AnalysisContext ctx = ctxBld.build();
            ModelNode cfg = ctx.getConfiguration();

            String json = "[{\"extension\": \"test\", \"configuration\": \"yes\"}]";
            ModelNode expected = ModelNode.fromJSONString(json);

            assertEquals(expected, cfg);
        } finally {
            // noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    @Test
    public void testReadingConfigurationFromClassPath() throws Exception {
        ConfigurationFile configurationFile = new ConfigurationFile();
        configurationFile.setResource("test-configuration-file.xml");

        AnalysisConfigurationGatherer gatherer = new AnalysisConfigurationGatherer(null,
                new Object[] { configurationFile }, false, false, new PropertyValueResolver(new Properties()), null,
                null);

        Revapi revapi = Revapi.builder().withFilters(TestExtension.class).build();
        AnalysisContext.Builder ctxBld = AnalysisContext.builder();

        gatherer.gatherConfig(revapi, ctxBld);

        AnalysisContext ctx = ctxBld.build();
        ModelNode cfg = ctx.getConfiguration();

        String json = "[{\"extension\": \"test\", \"configuration\": \"yes\"}]";
        ModelNode expected = ModelNode.fromJSONString(json);

        assertEquals(expected, cfg);
    }

    @Test
    public void testPropertyExpansionInJSON() throws Exception {
        ConfigurationFile configurationFile = new ConfigurationFile();
        configurationFile.setResource("prop-expansion-config.json");

        Properties props = new Properties();
        props.put("prop", "yes");
        AnalysisConfigurationGatherer gatherer = new AnalysisConfigurationGatherer(null,
                new Object[] { configurationFile }, false, true, new PropertyValueResolver(props), null, null);

        Revapi revapi = Revapi.builder().withFilters(TestExtension.class).build();
        AnalysisContext.Builder ctxBld = AnalysisContext.builder();

        gatherer.gatherConfig(revapi, ctxBld);

        AnalysisContext ctx = ctxBld.build();
        JsonNode cfg = ctx.getConfigurationNode();

        String json = "[{\"extension\": \"test\", \"configuration\": \"yes\"}]";
        JsonNode expected = JSONUtil.parse(json);

        assertEquals(expected, cfg);
    }

    @Test
    public void testPropertyExpansionInXml() throws Exception {
        ConfigurationFile configurationFile = new ConfigurationFile();
        configurationFile.setResource("prop-expansion-config.xml");

        Properties props = new Properties();
        props.put("prop", "yes");
        AnalysisConfigurationGatherer gatherer = new AnalysisConfigurationGatherer(null,
                new Object[] { configurationFile }, false, true, new PropertyValueResolver(props), null, null);

        Revapi revapi = Revapi.builder().withFilters(TestExtension.class).build();
        AnalysisContext.Builder ctxBld = AnalysisContext.builder();

        gatherer.gatherConfig(revapi, ctxBld);

        AnalysisContext ctx = ctxBld.build();
        ModelNode cfg = ctx.getConfiguration();

        String json = "[{\"extension\": \"test\", \"configuration\": \"yes\"}]";
        ModelNode expected = ModelNode.fromJSONString(json);

        assertEquals(expected, cfg);
    }

    public static final class TestExtension implements ElementFilter {

        @Override
        public void close() throws Exception {

        }

        @Override
        public String getExtensionId() {
            return "test";
        }

        @Nullable
        @Override
        public Reader getJSONSchema() {
            return new StringReader("{\"type\": \"string\"}");
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {

        }

        @Override
        public boolean applies(@Nullable Element element) {
            return false;
        }

        @Override
        public boolean shouldDescendInto(@Nullable Object element) {
            return false;
        }
    }
}
