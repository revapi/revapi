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
package org.revapi.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.jboss.dmr.ValueExpressionResolver;
import org.revapi.AnalysisContext;
import org.revapi.Revapi;
import org.revapi.configuration.JSONUtil;
import org.revapi.configuration.XmlToJson;

final class AnalysisConfigurationGatherer {
    private final PlexusConfiguration analysisConfiguration;

    private final Object[] analysisConfigurationFiles;

    private final boolean failOnMissingConfigurationFiles;

    private final boolean expandProperties;

    private final ValueExpressionResolver resolver;

    private final File relativePathBaseDir;

    private final Log log;

    AnalysisConfigurationGatherer(PlexusConfiguration analysisConfiguration,
            Object[] analysisConfigurationFiles, boolean failOnMissingConfigurationFiles, boolean expandProperties,
            PropertyValueInterpolator interpolator, File relativePathBaseDir, Log log) {
        this.analysisConfiguration = analysisConfiguration;
        this.analysisConfigurationFiles = analysisConfigurationFiles;
        this.failOnMissingConfigurationFiles = failOnMissingConfigurationFiles;
        this.expandProperties = expandProperties;
        this.log = log;
        this.resolver = new PropertyExpressionResolver(interpolator);
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
                            "Either 'path' or 'resource' has to be specified in a configurationFile definition but" +
                                    " not both.");
                }

                String readErrorMessage = "Error while processing the configuration file on "
                        + (path == null ? "classpath " + resource : "path " + path);

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
                            return Collections.<InputStream>singletonList(new FileInputStream(ff)).iterator();
                        } catch (FileNotFoundException e) {
                            throw new IllegalArgumentException("Failed to read the configuration file '"
                                    + ff.getAbsolutePath() + "'.", e);
                        }
                    };
                } else {
                     configFileContents =
                            () -> {
                                try {
                                    return Collections.list(getClass().getClassLoader().getResources(resource))
                                            .stream()
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
                    ModelNode config;
                    try (InputStream in = it.next()) {
                        config = readJson(in);
                    } catch (IllegalArgumentException | IOException e) {
                        throw new MojoExecutionException(readErrorMessage, e.getCause());
                    }

                    if (config == null) {
                        nonJsonIndexes.add(idx);
                        continue;
                    }

                    expandVariables(config);

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
                            throw new MojoExecutionException(readErrorMessage, e.getCause());
                        }

                        idx++;
                    }
                }
            }
        }

        if (analysisConfiguration != null) {
            String text = analysisConfiguration.getValue();
            if (text == null || text.isEmpty()) {
                convertNewStyleConfigFromXml(ctxBld, revapi);
            } else {
                ctxBld.mergeConfiguration(expandVariables(ModelNode.fromJSONString(JSONUtil.stripComments(text))));
            }
        }
    }

    private void mergeXmlConfigFile(Revapi revapi, AnalysisContext.Builder ctxBld, ConfigurationFile configFile, Reader rdr)
            throws IOException, XmlPullParserException {
        XmlToJson<PlexusConfiguration> conv = new XmlToJson<>(revapi, PlexusConfiguration::getName,
                PlexusConfiguration::getValue, PlexusConfiguration::getAttribute, x -> Arrays.asList(x.getChildren()));

        PlexusConfiguration xml = new XmlPlexusConfiguration(Xpp3DomBuilder.build(rdr));

        String[] roots = configFile.getRoots();

        if (roots == null) {
            ctxBld.mergeConfiguration(expandVariables(conv.convert(xml)));
        } else {
            roots:
            for (String r : roots) {
                PlexusConfiguration root = xml;
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

                ctxBld.mergeConfiguration(expandVariables(conv.convert(root)));
            }
        }
    }

    private void mergeJsonConfigFile(AnalysisContext.Builder ctxBld, ConfigurationFile configFile, ModelNode config) {
        String[] roots = configFile.getRoots();

        if (roots == null) {
            ctxBld.mergeConfiguration(config);
        } else {
            for (String r : roots) {
                String[] rootPath = r.split("/");
                ModelNode root = config.get(rootPath);

                if (!root.isDefined()) {
                    continue;
                }

                ctxBld.mergeConfiguration(root);
            }
        }
    }

    private void convertNewStyleConfigFromXml(AnalysisContext.Builder bld, Revapi revapi) {
        XmlToJson<PlexusConfiguration> conv = new XmlToJson<>(revapi, PlexusConfiguration::getName,
                PlexusConfiguration::getValue, PlexusConfiguration::getAttribute, x -> Arrays.asList(x.getChildren()));

        bld.mergeConfiguration(expandVariables(conv.convert(analysisConfiguration)));
    }

    private ModelNode readJson(InputStream in) {
        try {
            return ModelNode.fromJSONStream(JSONUtil.stripComments(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    private ModelNode expandVariables(ModelNode config) {
        if (!expandProperties) {
            return config;
        }

        switch (config.getType()) {
        case LIST:
            config.asList().forEach(this::expandVariables);
            break;
        case OBJECT:
            for (String key : config.asObject().keys()) {
                expandVariables(config.get(key));
            }
            break;
        default:
            expandVariable(config, resolver);
        }

        return config;
    }

    private static void expandVariable(ModelNode node, ValueExpressionResolver resolver) {
        ValueExpression val = new ValueExpression(node.asString());
        switch (node.getType()) {
        case STRING:
            node.set(val.resolveString(resolver));
            break;
        case BOOLEAN:
            node.set(val.resolveBoolean(resolver));
            break;
        case LONG:
            node.set(val.resolveLong(resolver));
            break;
        case DOUBLE:
            node.set(val.resolveBigDecimal(resolver).doubleValue());
            break;
        case INT:
            node.set(val.resolveInt(resolver));
            break;
        case BIG_DECIMAL:
            node.set(val.resolveBigDecimal(resolver));
            break;
        case BIG_INTEGER:
            node.set(val.resolveBigDecimal(resolver).toBigInteger());
            break;
        }
    }

    private static final class PropertyExpressionResolver extends ValueExpressionResolver {
        private final PropertyValueInterpolator interpolator;

        private PropertyExpressionResolver(PropertyValueInterpolator interpolator) {
            this.interpolator = interpolator;
        }

        @Override
        protected String resolvePart(String name) {
            return interpolator.interpolate(name);
        }
    }
}
