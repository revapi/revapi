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
package org.revapi.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.revapi.AnalysisContext;
import org.revapi.Revapi;
import org.revapi.configuration.JSONUtil;
import org.revapi.configuration.XmlToJson;

final class AnalysisConfigurationGatherer {
    private final PlexusConfiguration analysisConfiguration;

    private final Object[] analysisConfigurationFiles;

    private final boolean failOnMissingConfigurationFiles;

    private final boolean expandProperties;

    private final PropertyValueResolver propertyValueResolver;

    private final File relativePathBaseDir;

    private final Log log;

    AnalysisConfigurationGatherer(PlexusConfiguration analysisConfiguration, Object[] analysisConfigurationFiles,
            boolean failOnMissingConfigurationFiles, boolean expandProperties,
            PropertyValueResolver propertyValueResolver, File relativePathBaseDir, Log log) {
        this.analysisConfiguration = analysisConfiguration;
        this.analysisConfigurationFiles = analysisConfigurationFiles;
        this.failOnMissingConfigurationFiles = failOnMissingConfigurationFiles;
        this.expandProperties = expandProperties;
        this.log = log;
        this.propertyValueResolver = propertyValueResolver;
        this.relativePathBaseDir = relativePathBaseDir;
    }

    void gatherConfig(Revapi revapi, AnalysisContext.Builder ctxBld) throws MojoExecutionException {
        if (analysisConfigurationFiles != null && analysisConfigurationFiles.length > 0) {
            for (Object pathOrConfigFile : analysisConfigurationFiles) {
                ConfigurationFile configFile;
                if (pathOrConfigFile instanceof String) {
                    configFile = new ConfigurationFile();
                    configFile.setPath((String) pathOrConfigFile);
                } else {
                    configFile = (ConfigurationFile) pathOrConfigFile;
                }

                String path = configFile.getPath();
                String resource = configFile.getResource();

                if (path == null && resource == null) {
                    throw new MojoExecutionException(
                            "Either 'path' or 'resource' has to be specified in a configurationFile definition.");
                } else if (path != null && resource != null) {
                    throw new MojoExecutionException(
                            "Either 'path' or 'resource' has to be specified in a configurationFile definition but"
                                    + " not both.");
                }

                String readErrorMessage = "Error while processing the configuration file on "
                        + (path == null ? "classpath " + resource : "path " + path) + ": ";

                Supplier<Iterator<InputStream>> configFileContents;

                if (path != null) {
                    File f = new File(path);
                    if (!f.isAbsolute()) {
                        f = new File(relativePathBaseDir, path);
                    }

                    if (!f.isFile() || !f.canRead()) {
                        String message = "Could not locate analysis configuration file '" + f.getAbsolutePath() + "'.";
                        if (failOnMissingConfigurationFiles) {
                            throw new MojoExecutionException(message);
                        } else {
                            log.debug(message);
                            continue;
                        }
                    }

                    final File ff = f;
                    configFileContents = () -> {
                        try {
                            return Collections.<InputStream> singletonList(new FileInputStream(ff)).iterator();
                        } catch (FileNotFoundException e) {
                            throw new IllegalArgumentException(
                                    "Failed to read the configuration file '" + ff.getAbsolutePath() + "'.", e);
                        }
                    };
                } else {
                    configFileContents = () -> {
                        try {
                            return Collections.list(getClass().getClassLoader().getResources(resource)).stream()
                                    .map(url -> {
                                        try {
                                            return url.openStream();
                                        } catch (IOException e) {
                                            throw new IllegalArgumentException(
                                                    "Failed to read the classpath resource '" + url + "'.");
                                        }
                                    }).iterator();
                        } catch (IOException e) {
                            throw new IllegalArgumentException(
                                    "Failed to locate classpath resources on path '" + resource + "'.");
                        }
                    };
                }

                Iterator<InputStream> it = configFileContents.get();
                List<Integer> nonJsonIndexes = new ArrayList<>(4);
                int idx = 0;
                while (it.hasNext()) {
                    JsonNode config;
                    try (InputStream in = it.next()) {
                        config = readJson(in);
                    } catch (IllegalArgumentException | IOException e) {
                        throw new MojoExecutionException(readErrorMessage + e.getMessage(), e);
                    }

                    if (config == null) {
                        nonJsonIndexes.add(idx);
                        continue;
                    }

                    config = expandVariables(config);

                    mergeJsonConfigFile(ctxBld, configFile, config);

                    idx++;
                }

                if (!nonJsonIndexes.isEmpty()) {
                    idx = 0;
                    it = configFileContents.get();
                    while (it.hasNext()) {
                        try (Reader rdr = new InputStreamReader(it.next())) {
                            if (nonJsonIndexes.contains(idx)) {
                                mergeXmlConfigFile(revapi, ctxBld, configFile, rdr);
                            }
                        } catch (IllegalArgumentException | IOException | XmlPullParserException e) {
                            throw new MojoExecutionException(readErrorMessage + e.getMessage(), e);
                        }

                        idx++;
                    }
                }
            }
        }

        if (analysisConfiguration != null) {
            try {
                String text = analysisConfiguration.getValue();
                if (text == null || text.isEmpty()) {
                    convertNewStyleConfigFromXml(ctxBld, revapi);
                } else {
                    ctxBld.mergeConfiguration(expandVariables(JSONUtil.parse(JSONUtil.stripComments(text))));
                }
            } catch (PlexusConfigurationException e) {
                throw new MojoExecutionException("Failed to read the configuration: " + e.getMessage(), e);
            }
        }
    }

    private void mergeXmlConfigFile(Revapi revapi, AnalysisContext.Builder ctxBld, ConfigurationFile configFile,
            Reader rdr) throws IOException, XmlPullParserException {
        XmlToJson<PlexusConfigurationWrapper> conv = new XmlToJson<>(revapi, PlexusConfigurationWrapper::getName,
                PlexusConfigurationWrapper::getValue, PlexusConfigurationWrapper::getAttribute,
                PlexusConfigurationWrapper::getChildren);

        PlexusConfigurationWrapper xml = new PlexusConfigurationWrapper(
                new XmlPlexusConfiguration(Xpp3DomBuilder.build(rdr)));

        String[] roots = configFile.getRoots();

        if (roots == null) {
            ctxBld.mergeConfiguration(expandVariables(conv.convertXml(xml)));
        } else {
            roots: for (String r : roots) {
                PlexusConfigurationWrapper root = xml;
                boolean first = true;
                String[] rootPath = r.split("/");
                for (String name : rootPath) {
                    if (first) {
                        first = false;
                        if (!name.equals(root.getName())) {
                            continue roots;
                        }
                    } else {
                        root = root.getChild(name);
                        if (root == null) {
                            continue roots;
                        }
                    }
                }

                ctxBld.mergeConfiguration(expandVariables(conv.convertXml(root)));
            }
        }
    }

    private void mergeJsonConfigFile(AnalysisContext.Builder ctxBld, ConfigurationFile configFile, JsonNode config) {
        String[] roots = configFile.getRoots();

        if (roots == null) {
            ctxBld.mergeConfiguration(config);
        } else {
            for (String r : roots) {
                String[] rootPath = r.split("/");
                JsonNode root = config;
                for (String path : rootPath) {
                    root = root.path(path);
                }

                if (root.isMissingNode()) {
                    continue;
                }

                ctxBld.mergeConfiguration(root);
            }
        }
    }

    private void convertNewStyleConfigFromXml(AnalysisContext.Builder bld, Revapi revapi) {
        XmlToJson<PlexusConfigurationWrapper> conv = XmlToJson.fromRevapi(revapi, PlexusConfigurationWrapper::getName,
                PlexusConfigurationWrapper::getValue, PlexusConfigurationWrapper::getAttribute,
                PlexusConfigurationWrapper::getChildren);

        bld.mergeConfiguration(expandVariables(conv.convertXml(new PlexusConfigurationWrapper(analysisConfiguration))));
    }

    private JsonNode readJson(InputStream in) {
        try {
            return JSONUtil.parse(JSONUtil.stripComments(in));
        } catch (IOException e) {
            return null;
        }
    }

    private JsonNode expandVariables(JsonNode config) {
        if (!expandProperties) {
            return config;
        }

        if (config.isArray()) {
            for (int i = 0; i < config.size(); ++i) {
                ((ArrayNode) config).set(i, expandVariables(config.get(i)));
            }
        } else if (config.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = config.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                e.setValue(expandVariables(e.getValue()));
            }
        } else {
            config = expandVariable(config, propertyValueResolver);
        }

        return config;
    }

    private static JsonNode expandVariable(JsonNode node, PropertyValueResolver resolver) {
        // Intentionally call .toString(), because that produces a valid JSON representation of the node that we will
        // then interpolate and reparse. Reparsing is required
        String val = node.toString();
        if (!resolver.containsVariables(val)) {
            return node;
        }
        return JSONUtil.parse(resolver.resolve(val));
    }

    private static final class PlexusConfigurationWrapper {
        private final PlexusConfiguration config;

        private PlexusConfigurationWrapper(PlexusConfiguration config) {
            this.config = config;
        }

        public String getName() {
            return config.getName();
        }

        public String getValue() {
            try {
                return config.getValue();
            } catch (PlexusConfigurationException e) {
                throw new IllegalStateException("Failed to read configuration: " + e.getMessage(), e);
            }
        }

        public String getAttribute(String name) {
            try {
                return config.getAttribute(name);
            } catch (PlexusConfigurationException e) {
                throw new IllegalStateException("Failed to read configuration: " + e.getMessage(), e);
            }
        }

        public List<PlexusConfigurationWrapper> getChildren() {
            return Stream.of(config.getChildren()).map(PlexusConfigurationWrapper::new).collect(Collectors.toList());
        }

        @Nullable
        public PlexusConfigurationWrapper getChild(String name) {
            PlexusConfiguration c = config.getChild(name);
            return c == null ? null : new PlexusConfigurationWrapper(c);
        }
    }
}
