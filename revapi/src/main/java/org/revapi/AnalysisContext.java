/*
 * Copyright 2014-2018 Lukas Krejci
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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.configuration.Configurable;
import org.revapi.configuration.JSONUtil;

/**
 * An analysis context is an aggregation of the APIs to check and configuration for the analysis.
 *
 * <p>The analysis context can also contain arbitrary data that can be then accessed by the extensions. This can be used
 * to pass runtime data to the extensions that cannot be captured by their configurations.
 *
 * <p>The configuration accepted by the builder is actually of 2 forms.
 * <ol>
 * <li>The "old style" configuration where each extension could be configured at most once
 * <li>The "new style" configuration that enables multiple configurations for each extension and is more easily
 * translatable to XML.
 * </ol>
 * <p>
 * In the old format, the configuration of an extension was nested inside objects corresponding to its exploded
 * extension id. E.g. if the extension had an id of "my.extension", the configuration would look like:
 * <pre><code>
 *     {
 *         "my": {
 *             "extension": {
 *                 ... the actual configuration of the extension ...
 *             }
 *         }
 *     }
 * </code></pre>
 * The original idea for doing things this way was that such configuration is easily mergeable
 * (e.g. when configuration is split across multiple files). There can be only one configuration for each extension and
 * each extension resides in a different "section" of the configuration. The nesting provides for a seemingly logical
 * structure of the configuration.
 *
 * <p>On the other hand the new configuration format is different. It stresses the possibility of having more than 1
 * configuration for an extension and is more easily translatable into XML. On the other hand it is not that well nested
 * as the old configuration style so it might not look that well logically structured. The new configuration looks like
 * this:
 * <pre><code>
 *     [
 *         {
 *             "extension": "my.extension",
 *             "id": "optional-id",
 *             "configuration": {
 *                 ... the actual configuration of the extension ...
 *             }
 *         },
 *         {
 *             "extension": "other.extension",
 *             "configuration": {
 *                 ... the actual configuration of the extension ...
 *             }
 *         }
 *     ]
 * </code></pre>
 * Notice that configurations for different extensions (as well as different configurations for the same extension) are
 * contained in a top level list. The configurations of different extensions are therefore no longer nested inside each
 * other, but are "laid out" sequentially.
 *
 * <p>Each such configuration can optionally be identified by an arbitrary ID which can be used during merging of
 * configuration from multiple sources.
 *
 * <p><b>Merging Configurations</b>
 *
 * <p>As was the case with the old style configurations, the new style configurations can also be merged into each other
 * enabling composition of the final analysis configuration from multiple sources. Because each extension can be
 * configured multiple times in the new style configuration, the identification of the configs to merge is slightly more
 * complicated than it used to.
 *
 * <p>As long as the "master" configuration and "mergee" configuration contain ids for each configuration of each
 * extension things are simple. Configurations for the same extension with the same ID are merged together, otherwise
 * their added to the list of all extension configurations.
 *
 * <p>Things get a little bit more complex when the configurations don't have an explicit ID. This use case is supported
 * for seamless conversion from old style configuration (where it didn't make sense to have any kind of configuration
 * ID) to new style configurations that we support now (after all there are quite some clients already having their
 * configurations set up and it would be bad to force them to rewrite them all).
 *
 * <p>Simply put, id-less configurations are mergeable as long as the master configuration contains only a single
 * configuration for given extension and there is just 1 id-less configuration for given extension in the "mergee".
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public final class AnalysisContext {

    private final Locale locale;
    private final ModelNode configuration;
    private final API oldApi;
    private final API newApi;
    private final Map<String, Object> data;

    /**
     * Constructor
     *
     * @param locale        the locale the analysis reporters should use
     * @param configuration configuration represented as DMR node
     * @param oldApi        the old API
     * @param newApi        the new API
     * @param data          the data that should be attached to the analysis context
     */
    private AnalysisContext(@Nonnull Locale locale, @Nullable ModelNode configuration, @Nonnull API oldApi,
            @Nonnull API newApi, @Nonnull Map<String, Object> data) {
        this.locale = locale;
        if (configuration == null) {
            this.configuration = new ModelNode();
            this.configuration.setEmptyList();
        } else {
            this.configuration = configuration;
        }
        this.oldApi = oldApi;
        this.newApi = newApi;
        this.data = data;
    }

    /**
     * Returns a new analysis context builder that extracts the information about the available extensions from
     * the provided Revapi instance.
     *
     * <p>The extensions have to be known so that both old and new style of configuration can be usefully worked with.
     *
     * @param revapi the revapi instance to read the available extensions from
     * @return a new analysis context builder
     */
    @Nonnull
    public static Builder builder(Revapi revapi) {
        List<String> knownExtensionIds = new ArrayList<>();

        addExtensionIds(revapi.getPipelineConfiguration().getApiAnalyzerTypes(), knownExtensionIds);
        addExtensionIds(revapi.getPipelineConfiguration().getTransformTypes(), knownExtensionIds);
        addExtensionIds(revapi.getPipelineConfiguration().getFilterTypes(), knownExtensionIds);
        addExtensionIds(revapi.getPipelineConfiguration().getReporterTypes(), knownExtensionIds);

        return new Builder(knownExtensionIds);
    }

    /**
     * This method can be used to instantiate a new analysis context builder without the need for prior instantiation
     * of Revapi. Such builder is only able to process a new-style configuration though!
     *
     * @return a new analysis context builder
     */
    public static Builder builder() {
        return new Builder(null);
    }

    /**
     * This is generally only useful for extensions that delegate some of their functionality to other "internal"
     * extensions of their own that they need to configure.
     *
     * @param configuration the configuration to be supplied with the returned analysis context.
     * @return an analysis context that is a clone of this instance but its configuration is replaced with the provided
     * one.
     */
    public AnalysisContext copyWithConfiguration(ModelNode configuration) {
        return new AnalysisContext(this.locale, configuration, this.oldApi, this.newApi, this.data);
    }

    @Nonnull
    public Locale getLocale() {
        return locale;
    }

    @Nonnull
    public ModelNode getConfiguration() {
        return configuration;
    }

    @Nonnull
    public API getOldApi() {
        return oldApi;
    }

    @Nonnull
    public API getNewApi() {
        return newApi;
    }

    @Nullable
    public Object getData(String key) {
        return data.get(key);
    }

    private static <T extends Configurable>
    void addExtensionIds(Collection<Class<? extends T>> cs, List<String> extensionIds) {
        cs.stream()
                .map(AnalysisContext::instantiate)
                .map(Configurable::getExtensionId)
                .filter(Objects::nonNull)
                .forEach(extensionIds::add);
    }

    private static <T> T instantiate(Class<T> cls) {
        try {
            return cls.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Class " + cls + " does not have a public no-arg constructor.", e);
        }
    }

    public static final class Builder {
        private final List<String> knownExtensionIds;

        private Locale locale = Locale.getDefault();
        private API oldApi;
        private API newApi;
        private ModelNode configuration;
        private Map<String, Object> data = new HashMap<>(2);

        private Builder(List<String> knownExtensionIds) {
            this.knownExtensionIds = knownExtensionIds;
        }

        public Builder withLocale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder withOldAPI(API api) {
            this.oldApi = api;
            return this;
        }

        public Builder withNewAPI(API api) {
            this.newApi = api;
            return this;
        }

        public Builder withConfiguration(ModelNode data) {
            this.configuration = convertToNewStyle(data);
            return this;
        }

        public Builder withConfigurationFromJSON(String json) {
            this.configuration = convertToNewStyle(ModelNode.fromJSONString(JSONUtil.stripComments(json)));
            return this;
        }

        public Builder withConfigurationFromJSONStream(InputStream jsonStream) throws IOException {
            this.configuration = convertToNewStyle(ModelNode
                    .fromJSONStream(JSONUtil.stripComments(jsonStream, Charset.forName("UTF-8"))));
            return this;
        }

        /**
         * Tries to merge the provided configuration into the already existing one.
         *
         * <p>See the {@link AnalysisContext} documentation for detailed explanation of the merging logic.
         *
         * @param config the configuration to merge
         * @return this builder
         */
        public Builder mergeConfiguration(ModelNode config) {
            if (configuration == null) {
                configuration = new ModelNode();
                configuration.setEmptyList();
            }
            mergeConfigs(this.configuration, convertToNewStyle(config));
            return this;
        }

        public Builder mergeConfigurationFromJSON(String json) {
            if (configuration == null) {
                configuration = new ModelNode();
                configuration.setEmptyList();
            }
            mergeConfigs(configuration, convertToNewStyle(ModelNode.fromJSONString(JSONUtil.stripComments(json))));
            return this;
        }

        public Builder mergeConfigurationFromJSONStream(InputStream jsonStream) throws IOException {
            if (configuration == null) {
                configuration = new ModelNode();
                configuration.setEmptyList();
            }
            InputStream str = JSONUtil.stripComments(jsonStream, Charset.forName("UTF-8"));
            mergeConfigs(configuration, ModelNode.fromJSONStream(str));
            return this;
        }

        public Builder withData(Map<String, Object> data) {
            this.data.putAll(data);
            return this;
        }

        public Builder withData(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public AnalysisContext build() {
            return new AnalysisContext(locale, configuration, oldApi, newApi, data);
        }

        private ModelNode convertToNewStyle(ModelNode configuration) {
            if (configuration.getType() == ModelType.LIST) {
                Map<String, Set<String>> idsByExtension = new HashMap<>(4);
                for (ModelNode c : configuration.asList()) {

                    if (c.hasDefined("id")) {
                        String extension = c.get("extension").asString();
                        String id = c.get("id").asString();

                        boolean added = idsByExtension.computeIfAbsent(extension, x -> new HashSet<>(2)).add(id);
                        if (!added) {
                            throw new IllegalArgumentException(
                                    "A configuration cannot contain 2 extension configurations with the same id. " +
                                            "At least 2 extension configurations of extension '" + extension +
                                            "' have the id '" + id + "'.");
                        }
                    }
                }

                return configuration;
            }

            if (knownExtensionIds == null) {
                throw new IllegalArgumentException(
                        "The analysis context builder wasn't supplied with the list of known extension ids," +
                                " so it only can process new-style configurations.");
            }

            ModelNode newStyleConfig = new ModelNode();
            newStyleConfig.setEmptyList();

            extensionScan:
            for (String extensionId : knownExtensionIds) {
                String[] explodedId = extensionId.split("\\.");

                ModelNode extConfig = configuration;
                for (String segment : explodedId) {
                    if (!extConfig.hasDefined(segment)) {
                        continue extensionScan;
                    } else {
                        extConfig = extConfig.get(segment);
                    }
                }

                ModelNode extNewStyle = new ModelNode();
                extNewStyle.get("extension").set(extensionId);
                extNewStyle.get("configuration").set(extConfig);

                newStyleConfig.add(extNewStyle);
            }

            return newStyleConfig;
        }

        private static void splitByExtensionAndId(List<ModelNode> configs,
                Map<String, Map<String, ModelNode>> byExtensionAndId,
                Map<String, List<ModelNode>> idlessByExtension) {
            for (ModelNode c : configs) {
                String extensionId = c.get("extension").asString();
                if (!c.hasDefined("id")) {
                    idlessByExtension.computeIfAbsent(extensionId, x -> new ArrayList<>(2)).add(c);
                    continue;
                }

                String id = c.get("id").asString();

                byExtensionAndId.computeIfAbsent(extensionId, x -> new HashMap<>(2)).compute(id, (i, n) -> {
                    if (n == null) {
                        return c;
                    } else {
                        throw new IllegalArgumentException(
                                "There cannot be 2 or more configurations with the same ID.");
                    }
                });
            }
        }

        private static void mergeConfigs(ModelNode a, ModelNode b) {
            Map<String, Map<String, ModelNode>> aByExtensionAndId = new HashMap<>(4);
            Map<String, List<ModelNode>> idlessAByExtensionId = new HashMap<>(4);
            splitByExtensionAndId(a.asList(), aByExtensionAndId, idlessAByExtensionId);

            Map<String, Map<String, ModelNode>> bByExtensionAndId = new HashMap<>(4);
            Map<String, List<ModelNode>> idlessBByExtensionId = new HashMap<>(4);
            splitByExtensionAndId(b.asList(), bByExtensionAndId, idlessBByExtensionId);

            //we cannot merge if:
            //1) "a" contains 2 or more (idless or not) for an extension and b contains at least 1 idless for an extension
            //2) "a" contains at least 1 for an extension and b contains 2 or more idless for an extension
            Stream.concat(aByExtensionAndId.keySet().stream(), idlessAByExtensionId.keySet().stream()).forEach(ext -> {
                int aCnt = idlessAByExtensionId.getOrDefault(ext, emptyList()).size()
                        + aByExtensionAndId.getOrDefault(ext, emptyMap()).size();

                int bCnt = idlessBByExtensionId.getOrDefault(ext, emptyList()).size();

                //rule 1
                if (aCnt > 1 && bCnt > 0) {
                    throw new IllegalArgumentException(
                            "The configuration already contains more than 1 configuration for extension " +
                                    ext + ". Cannot determine which one of them to merge" +
                                    " the new configuration(s) (which don't have an explicit ID) into.");
                }

                //rule 2
                if (aCnt > 0 && bCnt > 1) {
                    throw new IllegalArgumentException(
                            "The configuration already contains 1 or more configurations for extension " +
                                    ext + ". At the same time, the configuration to merge already contains 2 or more" +
                                    " configurations for the same extension without an explicit ID." +
                                    " Cannot figure out how to merge these together.");
                }
            });

            int bcIdx = 0;
            for (ModelNode bc : b.asList()) {
                String bcId = bc.hasDefined("id") ? bc.get("id").asString() : null;
                String bcExtension = bc.get("extension").asString();

                if (bcId == null) {
                    List<ModelNode> idless = idlessAByExtensionId.get(bcExtension);
                    if (idless != null) {
                        if (idless.size() == 1) {
                            List<String> path = new ArrayList<>(4);
                            path.addAll(Arrays.asList("[" + bcIdx + "]", "configuration"));
                            mergeNodes(bcExtension, null, path, idless.get(0).get("configuration"), bc.get("configuration"));
                        }
                    } else {
                        Map<String, ModelNode> aExtensions = aByExtensionAndId.get(bcExtension);
                        if (aExtensions == null) {
                            a.add(bc);
                            continue;
                        }

                        List<String> path = new ArrayList<>(4);
                        path.addAll(Arrays.asList("[" + bcIdx + "]", "configuration"));

                        ModelNode aConfig = aExtensions.values().iterator().next();
                        mergeNodes(bcExtension, null, path, aConfig.get("configuration"), bc.get("configuration"));
                    }
                } else {
                    Map<String, ModelNode> aExtensions = aByExtensionAndId.get(bcExtension);
                    if (aExtensions == null) {
                        a.add(bc);
                        continue;
                    }

                    ModelNode aConfig = aExtensions.get(bcId);

                    if (aConfig == null) {
                        a.add(bc);
                    } else {
                        List<String> path = new ArrayList<>(4);
                        path.addAll(Arrays.asList("[" + bcIdx + "]", "configuration"));
                        mergeNodes(bcExtension, bcId, path, aConfig.get("configuration"), bc.get("configuration"));
                    }
                }

                bcIdx++;
            }
        }

        private static void mergeNodes(String extension, String id, List<String> path, ModelNode a, ModelNode b) {
            switch (b.getType()) {
                case LIST:
                    for (ModelNode v : b.asList()) {
                        a.add(v.clone());
                    }
                    break;
                case OBJECT:
                    for (String k : b.keys()) {
                        ModelNode ak = a.get(k);
                        path.add(k);
                        mergeNodes(extension, id, path, ak, b.get(k));
                        path.remove(path.size() - 1);
                    }
                    break;
                default:
                    if (a.isDefined()) {
                        String p = path.stream().collect(Collectors.joining("/"));
                        throw new IllegalArgumentException(
                                "A conflict detected while merging configurations of extension '" + extension +
                                        "' with id '" + id + "'. A value on path '" + p + "' would overwrite an already existing one.");
                    } else {
                        a.set(b);
                    }
            }
        }
    }
}
