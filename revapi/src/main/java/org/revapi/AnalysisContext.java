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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.dmr.ModelNode;
import org.revapi.configuration.Configurable;
import org.revapi.configuration.JSONUtil;

/**
 * An analysis context is an aggregation of the APIs to check and configuration for the analysis.
 *
 * <p>
 * The analysis context can also contain arbitrary data that can be then accessed by the extensions. This can be used to
 * pass runtime data to the extensions that cannot be captured by their configurations.
 *
 * <p>
 * The configuration accepted by the builder is actually of 2 forms.
 * <ol>
 * <li>The "old style" configuration where each extension could be configured at most once
 * <li>The "new style" configuration that enables multiple configurations for each extension and is more easily
 * translatable to XML.
 * </ol>
 * <p>
 * In the old format, the configuration of an extension was nested inside objects corresponding to its exploded
 * extension id. E.g. if the extension had an id of "my.extension", the configuration would look like:
 * 
 * <pre>
 * <code>
 *     {
 *         "my": {
 *             "extension": {
 *                 ... the actual configuration of the extension ...
 *             }
 *         }
 *     }
 * </code>
 * </pre>
 * 
 * The original idea for doing things this way was that such configuration is easily mergeable (e.g. when configuration
 * is split across multiple files). There can be only one configuration for each extension and each extension resides in
 * a different "section" of the configuration. The nesting provides for a seemingly logical structure of the
 * configuration.
 *
 * <p>
 * On the other hand the new configuration format is different. It stresses the possibility of having more than 1
 * configuration for an extension and is more easily translatable into XML. On the other hand it is not that well nested
 * as the old configuration style so it might not look that well logically structured. The new configuration looks like
 * this:
 * 
 * <pre>
 * <code>
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
 * </code>
 * </pre>
 * 
 * Notice that configurations for different extensions (as well as different configurations for the same extension) are
 * contained in a top level list. The configurations of different extensions are therefore no longer nested inside each
 * other, but are "laid out" sequentially.
 *
 * <p>
 * Each such configuration can optionally be identified by an arbitrary ID which can be used during merging of
 * configuration from multiple sources.
 *
 * <p>
 * <b>Merging Configurations</b>
 *
 * <p>
 * As was the case with the old style configurations, the new style configurations can also be merged into each other
 * enabling composition of the final analysis configuration from multiple sources. Because each extension can be
 * configured multiple times in the new style configuration, the identification of the configs to merge is slightly more
 * complicated than it used to.
 *
 * <p>
 * As long as the "master" configuration and "mergee" configuration contain ids for each configuration of each extension
 * things are simple. Configurations for the same extension with the same ID are merged together, otherwise their added
 * to the list of all extension configurations.
 *
 * <p>
 * Things get a little bit more complex when the configurations don't have an explicit ID. This use case is supported
 * for seamless conversion from old style configuration (where it didn't make sense to have any kind of configuration
 * ID) to new style configurations that we support now (after all there are quite some clients already having their
 * configurations set up and it would be bad to force them to rewrite them all).
 *
 * <p>
 * Simply put, id-less configurations are mergeable as long as the master configuration contains only a single
 * configuration for given extension and there is just 1 id-less configuration for given extension in the "mergee".
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class AnalysisContext {

    private final Locale locale;
    private final JsonNode configuration;
    private final API oldApi;
    private final API newApi;
    private final Map<String, ElementMatcher> matchers;
    private final Map<String, Object> data;
    private final Map<DifferenceSeverity, Criticality> defaultSeverityMapping;
    private final Map<String, Criticality> criticalityByName;

    /**
     * Constructor
     *
     * @param locale
     *            the locale the analysis reporters should use
     * @param configuration
     *            configuration represented as DMR node
     * @param oldApi
     *            the old API
     * @param newApi
     *            the new API
     * @param data
     *            the data that should be attached to the analysis context
     * 
     * @deprecated use jackson-based methods
     */
    @Deprecated
    private AnalysisContext(Locale locale, @Nullable ModelNode configuration, API oldApi, API newApi,
            Collection<ElementMatcher> elementMatchers, Map<String, Object> data,
            Collection<Criticality> knownCriticalities, Map<DifferenceSeverity, Criticality> defaultSeverityMapping) {
        this(locale, JSONUtil.convert(configuration), oldApi, newApi, elementMatchers, data, knownCriticalities,
                defaultSeverityMapping);
    }

    private AnalysisContext(Locale locale, @Nullable JsonNode configuration, API oldApi, API newApi,
            Collection<ElementMatcher> elementMatchers, Map<String, Object> data,
            Collection<Criticality> knownCriticalities, Map<DifferenceSeverity, Criticality> defaultSeverityMapping) {
        this.locale = locale;
        if (configuration == null) {
            this.configuration = JsonNodeFactory.instance.arrayNode();
        } else {
            this.configuration = configuration;
        }
        this.oldApi = oldApi;
        this.newApi = newApi;
        this.matchers = elementMatchers == null ? Collections.emptyMap() : Collections.unmodifiableMap(
                elementMatchers.stream().collect(Collectors.toMap(Configurable::getExtensionId, Function.identity())));
        this.data = data;
        this.criticalityByName = knownCriticalities.stream().collect(toMap(Criticality::getName, identity()));
        this.defaultSeverityMapping = defaultSeverityMapping;
    }

    /**
     * Returns a new analysis context builder that extracts the information about the available extensions from the
     * provided Revapi instance.
     *
     * <p>
     * The extensions have to be known so that both old and new style of configuration can be usefully worked with.
     *
     * @param revapi
     *            the revapi instance to read the available extensions from
     * 
     * @return a new analysis context builder
     */
    public static Builder builder(Revapi revapi) {
        return builder(revapi.getPipelineConfiguration());
    }

    /**
     * Returns a new analysis context builder that extracts the information about the available extensions from the
     * provided pipeline configuration.
     *
     * <p>
     * The extensions have to be known so that both old and new style of configuration can be usefully worked with.
     *
     * @param pipelineConfiguration
     *            the pipeline configuration to initialize from
     * 
     * @return a new analysis context builder
     */
    public static Builder builder(PipelineConfiguration pipelineConfiguration) {
        List<String> knownExtensionIds = new ArrayList<>();

        addExtensionIds(pipelineConfiguration.getApiAnalyzerTypes(), knownExtensionIds);
        addExtensionIds(pipelineConfiguration.getTransformTypes(), knownExtensionIds);
        addExtensionIds(pipelineConfiguration.getTreeFilterTypes(), knownExtensionIds);
        addExtensionIds(pipelineConfiguration.getReporterTypes(), knownExtensionIds);
        addExtensionIds(pipelineConfiguration.getMatcherTypes(), knownExtensionIds);

        return new Builder(knownExtensionIds, pipelineConfiguration.getCriticalities(),
                pipelineConfiguration.getSeverityMapping());
    }

    /**
     * This method can be used to instantiate a new analysis context builder without the need for prior instantiation of
     * Revapi. Such builder is only able to process a new-style configuration though!
     *
     * <p>
     * This analysis context will also always uses the default criticalities and severity-to-criticality mapping.
     *
     * @return a new analysis context builder
     */
    public static Builder builder() {
        return new Builder(null, Criticality.defaultCriticalities(), Criticality.defaultSeverityMapping());
    }

    /**
     * This is generally only useful for extensions that delegate some of their functionality to other "internal"
     * extensions of their own that they need to configure.
     *
     * @param configuration
     *            the configuration to be supplied with the returned analysis context.
     * 
     * @return an analysis context that is a clone of this instance but its configuration is replaced with the provided
     *         one.
     * 
     * @deprecated use the Jackson-based variant
     */
    @Deprecated
    public AnalysisContext copyWithConfiguration(ModelNode configuration) {
        return new AnalysisContext(this.locale, configuration, this.oldApi, this.newApi, this.matchers.values(),
                this.data, this.criticalityByName.values(), this.defaultSeverityMapping);
    }

    /**
     * This is generally only useful for extensions that delegate some of their functionality to other "internal"
     * extensions of their own that they need to configure.
     *
     * @param configuration
     *            the configuration to be supplied with the returned analysis context.
     * 
     * @return an analysis context that is a clone of this instance but its configuration is replaced with the provided
     *         one.
     */
    public AnalysisContext copyWithConfiguration(JsonNode configuration) {
        return new AnalysisContext(this.locale, configuration, this.oldApi, this.newApi, this.matchers.values(),
                this.data, this.criticalityByName.values(), this.defaultSeverityMapping);
    }

    /**
     * A helper method to instantiate a new analysis context with the provided set of matchers. This is only meant to be
     * used during analysis initialization.
     *
     * @param matchers
     *            the list of matchers to provide through this context
     * 
     * @return a copy of this instance with the provided matchers replacing any existing in this instance
     */
    public AnalysisContext copyWithMatchers(Set<ElementMatcher> matchers) {
        return new AnalysisContext(this.locale, this.configuration, this.oldApi, this.newApi, matchers, this.data,
                this.criticalityByName.values(), this.defaultSeverityMapping);
    }

    public Locale getLocale() {
        return locale;
    }

    /**
     * @deprecated use {@link #getConfigurationNode()} instead
     */
    @Deprecated
    public ModelNode getConfiguration() {
        return JSONUtil.convert(configuration);
    }

    /**
     * Returns the configuration node. This is never null. If the configuration is not present a
     * {@link com.fasterxml.jackson.databind.node.NullNode} is returned.
     */
    public JsonNode getConfigurationNode() {
        return configuration;
    }

    public API getOldApi() {
        return oldApi;
    }

    public API getNewApi() {
        return newApi;
    }

    public Map<String, ElementMatcher> getMatchers() {
        return matchers;
    }

    @Nullable
    public Object getData(String key) {
        return data.get(key);
    }

    @Nullable
    public Criticality getCriticalityByName(String name) {
        return criticalityByName.get(name);
    }

    public Criticality getDefaultCriticality(DifferenceSeverity severity) {
        return defaultSeverityMapping.get(severity);
    }

    private static <T extends Configurable> void addExtensionIds(Collection<Class<? extends T>> cs,
            List<String> extensionIds) {
        cs.stream().map(AnalysisContext::instantiate).map(Configurable::getExtensionId).filter(Objects::nonNull)
                .forEach(extensionIds::add);
    }

    private static <T> T instantiate(Class<T> cls) {
        try {
            return cls.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                | InvocationTargetException e) {
            throw new IllegalStateException("Class " + cls + " does not have a public no-arg constructor.", e);
        }
    }

    public static final class Builder {
        private final List<String> knownExtensionIds;
        private final Set<Criticality> knownCriticalities;

        private Locale locale = Locale.getDefault();
        private API oldApi;
        private API newApi;
        private ArrayNode configuration;
        private Map<String, Object> data = new HashMap<>(2);
        private final Map<DifferenceSeverity, Criticality> defaultSeverityMapping;

        private Builder(List<String> knownExtensionIds, Set<Criticality> knownCriticalities,
                Map<DifferenceSeverity, Criticality> defaultSeverityMapping) {
            this.knownExtensionIds = knownExtensionIds;
            this.knownCriticalities = knownCriticalities;
            this.defaultSeverityMapping = defaultSeverityMapping;
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

        /**
         * @deprecated use {@link #withConfiguration(JsonNode)}
         */
        @Deprecated
        public Builder withConfiguration(ModelNode data) {
            this.configuration = convertToNewStyle(JSONUtil.convert(data));
            return this;
        }

        public Builder withConfiguration(JsonNode data) {
            this.configuration = convertToNewStyle(data);
            return this;
        }

        public Builder withConfigurationFromJSON(String json) {
            this.configuration = convertToNewStyle(JSONUtil.parse(JSONUtil.stripComments(json)));
            return this;
        }

        public Builder withConfigurationFromJSONStream(InputStream jsonStream) throws IOException {
            this.configuration = convertToNewStyle(JSONUtil.parse(JSONUtil.stripComments(jsonStream)));
            return this;
        }

        /**
         * Tries to merge the provided configuration into the already existing one.
         *
         * <p>
         * See the {@link AnalysisContext} documentation for detailed explanation of the merging logic.
         *
         * @param config
         *            the configuration to merge
         * 
         * @return this builder
         * 
         * @deprecated use the Jackson-based variant
         */
        @Deprecated
        public Builder mergeConfiguration(ModelNode config) {
            if (configuration == null) {
                configuration = JsonNodeFactory.instance.arrayNode();
            }
            mergeConfigs(this.configuration, convertToNewStyle(JSONUtil.convert(config)));
            return this;
        }

        /**
         * Tries to merge the provided configuration into the already existing one.
         *
         * <p>
         * See the {@link AnalysisContext} documentation for detailed explanation of the merging logic.
         *
         * @param config
         *            the configuration to merge
         * 
         * @return this builder
         */
        public Builder mergeConfiguration(JsonNode config) {
            if (configuration == null) {
                configuration = JsonNodeFactory.instance.arrayNode();
            }
            mergeConfigs(this.configuration, convertToNewStyle(config));
            return this;
        }

        public Builder mergeConfigurationFromJSON(String json) {
            if (configuration == null) {
                configuration = JsonNodeFactory.instance.arrayNode();
            }
            mergeConfigs(configuration, convertToNewStyle(JSONUtil.parse(JSONUtil.stripComments(json))));
            return this;
        }

        public Builder mergeConfigurationFromJSONStream(InputStream jsonStream) throws IOException {
            if (configuration == null) {
                configuration = JsonNodeFactory.instance.arrayNode();
            }
            Reader rdr = JSONUtil.stripComments(jsonStream);
            mergeConfigs(configuration, convertToNewStyle(JSONUtil.parse(JSONUtil.stripComments(rdr))));
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
            return new AnalysisContext(locale, configuration, oldApi, newApi, Collections.emptySet(), data,
                    knownCriticalities, defaultSeverityMapping);
        }

        private ArrayNode convertToNewStyle(JsonNode configuration) {
            if (configuration.isArray()) {
                Map<String, Set<String>> idsByExtension = new HashMap<>(4);
                for (JsonNode c : configuration) {

                    if (c.hasNonNull("id")) {
                        String extension = c.get("extension").asText();
                        String id = c.get("id").asText();

                        boolean added = idsByExtension.computeIfAbsent(extension, x -> new HashSet<>(2)).add(id);
                        if (!added) {
                            throw new IllegalArgumentException(
                                    "A configuration cannot contain 2 extension configurations with the same id. "
                                            + "At least 2 extension configurations of extension '" + extension
                                            + "' have the id '" + id + "'.");
                        }
                    }
                }

                return (ArrayNode) configuration;
            }

            if (knownExtensionIds == null) {
                throw new IllegalArgumentException(
                        "The analysis context builder wasn't supplied with the list of known extension ids,"
                                + " so it only can process new-style configurations.");
            }

            ArrayNode newStyleConfig = JsonNodeFactory.instance.arrayNode();

            extensionScan: for (String extensionId : knownExtensionIds) {
                String[] explodedId = extensionId.split("\\.");

                JsonNode extConfig = configuration;
                for (String segment : explodedId) {
                    if (!extConfig.has(segment)) {
                        continue extensionScan;
                    } else {
                        extConfig = extConfig.get(segment);
                    }
                }

                ObjectNode extNewStyle = JsonNodeFactory.instance.objectNode();

                extNewStyle.put("extension", extensionId);
                extNewStyle.set("configuration", extConfig);

                newStyleConfig.add(extNewStyle);
            }

            return newStyleConfig;
        }

        private static void splitByExtensionAndId(ArrayNode configs,
                Map<String, Map<String, ObjectNode>> byExtensionAndId,
                Map<String, List<ObjectNode>> idlessByExtension) {
            for (JsonNode n : configs) {
                ObjectNode c = (ObjectNode) n;
                String extensionId = c.get("extension").asText();
                if (!c.hasNonNull("id")) {
                    idlessByExtension.computeIfAbsent(extensionId, x -> new ArrayList<>(2)).add(c);
                    continue;
                }

                String id = c.get("id").asText();

                byExtensionAndId.computeIfAbsent(extensionId, x -> new HashMap<>(2)).compute(id, (i, x) -> {
                    if (x == null) {
                        return (ObjectNode) c;
                    } else {
                        throw new IllegalArgumentException(
                                "There cannot be 2 or more configurations with the same ID.");
                    }
                });
            }
        }

        private static void mergeConfigs(ArrayNode a, ArrayNode b) {
            Map<String, Map<String, ObjectNode>> aByExtensionAndId = new HashMap<>(4);
            Map<String, List<ObjectNode>> idlessAByExtensionId = new HashMap<>(4);
            splitByExtensionAndId(a, aByExtensionAndId, idlessAByExtensionId);

            Map<String, Map<String, ObjectNode>> bByExtensionAndId = new HashMap<>(4);
            Map<String, List<ObjectNode>> idlessBByExtensionId = new HashMap<>(4);
            splitByExtensionAndId(b, bByExtensionAndId, idlessBByExtensionId);

            // we cannot merge if:
            // 1) "a" contains 2 or more (idless or not) for an extension and b contains at least 1 idless for an
            // extension
            // 2) "a" contains at least 1 for an extension and b contains 2 or more idless for an extension
            Stream.concat(aByExtensionAndId.keySet().stream(), idlessAByExtensionId.keySet().stream()).forEach(ext -> {
                int aCnt = idlessAByExtensionId.getOrDefault(ext, emptyList()).size()
                        + aByExtensionAndId.getOrDefault(ext, emptyMap()).size();

                int bCnt = idlessBByExtensionId.getOrDefault(ext, emptyList()).size();

                // rule 1
                if (aCnt > 1 && bCnt > 0) {
                    throw new IllegalArgumentException(
                            "The configuration already contains more than 1 configuration for extension " + ext
                                    + ". Cannot determine which one of them to merge"
                                    + " the new configuration(s) (which don't have an explicit ID) into.");
                }

                // rule 2
                if (aCnt > 0 && bCnt > 1) {
                    throw new IllegalArgumentException(
                            "The configuration already contains 1 or more configurations for extension " + ext
                                    + ". At the same time, the configuration to merge already contains 2 or more"
                                    + " configurations for the same extension without an explicit ID."
                                    + " Cannot figure out how to merge these together.");
                }
            });

            int bcIdx = 0;
            for (JsonNode bc : b) {
                String bcId = bc.hasNonNull("id") ? bc.get("id").asText() : null;
                String bcExtension = bc.get("extension").asText();

                if (bcId == null) {
                    List<ObjectNode> idless = idlessAByExtensionId.get(bcExtension);
                    if (idless != null) {
                        if (idless.size() == 1) {
                            List<String> path = new ArrayList<>(4);
                            path.addAll(Arrays.asList("[" + bcIdx + "]", "configuration"));
                            ObjectNode o = idless.get(0);
                            mergeNodes(bcExtension, null, path, o, "configuration", o.get("configuration"),
                                    bc.get("configuration"));
                        }
                    } else {
                        Map<String, ObjectNode> aExtensions = aByExtensionAndId.get(bcExtension);
                        if (aExtensions == null) {
                            a.add(bc);
                            continue;
                        }

                        List<String> path = new ArrayList<>(4);
                        path.addAll(Arrays.asList("[" + bcIdx + "]", "configuration"));

                        ObjectNode aConfig = aExtensions.values().iterator().next();
                        mergeNodes(bcExtension, null, path, aConfig, "configuration", aConfig.get("configuration"),
                                bc.get("configuration"));
                    }
                } else {
                    Map<String, ObjectNode> aExtensions = aByExtensionAndId.get(bcExtension);
                    if (aExtensions == null) {
                        a.add(bc);
                        continue;
                    }

                    ObjectNode aConfig = aExtensions.get(bcId);

                    if (aConfig == null) {
                        a.add(bc);
                    } else {
                        List<String> path = new ArrayList<>(4);
                        path.addAll(Arrays.asList("[" + bcIdx + "]", "configuration"));
                        mergeNodes(bcExtension, bcId, path, aConfig, "configuration", aConfig.get("configuration"),
                                bc.get("configuration"));
                    }
                }

                bcIdx++;
            }
        }

        private static void mergeNodes(String extension, String id, List<String> path, ObjectNode aParent,
                String parentKey, @Nullable JsonNode a, @Nullable JsonNode b) {
            if (b == null) {
                aParent.remove(parentKey);
                return;
            }
            switch (b.getNodeType()) {
            case ARRAY:
                if (a == null) {
                    a = JsonNodeFactory.instance.arrayNode();
                    aParent.set(parentKey, a);
                }
                for (JsonNode v : b) {
                    ((ArrayNode) a).add(v);
                }
                break;
            case OBJECT:
                if (a == null) {
                    a = JsonNodeFactory.instance.objectNode();
                    aParent.set(parentKey, a);
                }
                Iterator<Map.Entry<String, JsonNode>> it = b.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    JsonNode ak = a.get(e.getKey());
                    path.add(e.getKey());
                    mergeNodes(extension, id, path, (ObjectNode) a, e.getKey(), ak, e.getValue());
                }
                break;
            default:
                if (a != null) {
                    String p = String.join("/", path);
                    throw new IllegalArgumentException("A conflict detected while merging configurations of extension '"
                            + extension + "' with id '" + id + "'. A value on path '" + p
                            + "' would overwrite an already existing one.");
                } else {
                    aParent.set(parentKey, b);
                }
            }
        }
    }
}
