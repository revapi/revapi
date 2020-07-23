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
package org.revapi;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;

/**
 * This class represents the configuration of the Revapi analysis pipeline. This is different from the configuration of
 * the individual extensions (which are part of the pipeline) as it provides the configuration of the pipeline itself.
 * Namely, this class defines all the possible extensions that can be used during the analysis as well as
 * the configuration of the "shape" of the pipeline - as of now only the transformation blocks can be defined.
 *
 * <p>The analysis can be influenced by defining "transformation blocks". By default, each
 * {@link DifferenceTransform} can transform a difference into another one. Each such newly created difference is then
 * again examined by all difference transforms to see if it is also can be transformed again. This is sometimes not what
 * the user intends with the configuration, because it might be useful to "group" certain transformations into "blocks"
 * that happen "atomically" without the constituent transforms in the block influencing each others results.
 *
 * <p>E.g., consider the following scenario:
 * <p>There is a "reclassify" transform that is configured to "tune down" severity of certain differences. At the same
 * time, there is another transformation, say a "policy transformation" that considers any potentially breaking change
 * as breaking - the goal of the policy is to have a "clean" set of changes without any potentially breaking change in
 * the release.
 *
 * <p>Without a transformation block, these two transformations cannot coexist together without causing an "infinite
 * loop" (Revapi will interrupt the difference transformations after a certain number attempts).
 *
 * <p>In the outlined scenario, the intention of the user is to have the "reclassify" transform make some differences
 * admissible for the release and the "policy" transform to make all other differences breaking. The problem with this
 * is that, as described above, Revapi cannot guess this intention and just blindly applies all transforms on each and
 * every difference found. This makes it impossible for the "policy" transform to "not see" the admissible differences
 * that the "reclassify" transform tunes down.
 *
 * <p>Enter transformation blocks. Transformation blocks enable certain transformations to be applied in the defined
 * order while considering the result of such series of transformations as potential input for other transformations
 * outside of the block. This enables the user to say "first reclassify then apply policy", resulting her original
 * intention being clear to Revapi.
 *
 * <p>As explained in the {@link AnalysisContext}, each transformation extension can be configured multiple times using
 * different IDs. An ID is optional if there is only a single configuration of a single type of
 * transformation. When trying to group the transformations into blocks, Revapi first tries to find a transformation
 * with given ID and if it finds none, it tries to match the provided string with the name of the extension (internally
 * called the extension ID).
 */
public final class PipelineConfiguration {
    private final Set<Class<? extends ApiAnalyzer>> apiAnalyzerTypes;
    private final Set<Class<? extends Reporter>> reporterTypes;
    private final Set<Class<? extends DifferenceTransform<?>>> transformTypes;
    private final Set<Class<? extends ElementFilter>> filterTypes;
    private final Set<List<String>> transformationBlocks;
    private final List<String> includedAnalyzerExtensionIds;
    private final List<String> excludedAnalyzerExtensionIds;
    private final List<String> includedReporterExtensionIds;
    private final List<String> excludedReporterExtensionIds;
    private final List<String> includedTransformExtensionIds;
    private final List<String> excludedTransformExtensionIds;
    private final List<String> includedFilterExtensionIds;
    private final List<String> excludedFilterExtensionIds;
    private final Set<Criticality> criticalities;
    private final Map<DifferenceSeverity, Criticality> severityMapping;

    /**
     * @return a pipeline configuration builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Parses the configuration node and provides a pipeline configuration without any extensions marked for loading.
     * The configuration node is supposed to conform to the pipeline configuration JSON schema.
     *
     * <p>The caller is supposed to use the methods from the builder to add/find extension classes that will be used in
     * the analysis.
     *
     * <p>Note that the returned pipeline configuration might not contain all the extensions available in
     * the classloader depending on the include/exclude filters in the configuration.
     *
     * @param json the configuration node
     * @return a pipeline configuration parsed from the configuration
     * @see Builder#build()
     */
    public static PipelineConfiguration.Builder parse(ModelNode json) {
        ModelNode analyzerIncludeNode = json.get("analyzers").get("include");
        ModelNode analyzerExcludeNode = json.get("analyzers").get("exclude");
        ModelNode filterIncludeNode = json.get("filters").get("include");
        ModelNode filterExcludeNode = json.get("filters").get("exclude");
        ModelNode transformIncludeNode = json.get("transforms").get("include");
        ModelNode transformExcludeNode = json.get("transforms").get("exclude");
        ModelNode reporterIncludeNode = json.get("reporters").get("include");
        ModelNode reporterExcludeNode = json.get("reporters").get("exclude");
        ModelNode criticalities = json.get("criticalities");
        ModelNode severityMapping = json.get("severityMapping");

        return builder()
                .withTransformationBlocks(json.get("transformBlocks"))
                .withAnalyzerExtensionIdsInclude(asStringList(analyzerIncludeNode))
                .withAnalyzerExtensionIdsExclude(asStringList(analyzerExcludeNode))
                .withFilterExtensionIdsInclude(asStringList(filterIncludeNode))
                .withFilterExtensionIdsExclude(asStringList(filterExcludeNode))
                .withTransformExtensionIdsInclude(asStringList(transformIncludeNode))
                .withTransformExtensionIdsExclude(asStringList(transformExcludeNode))
                .withReporterExtensionIdsInclude(asStringList(reporterIncludeNode))
                .withReporterExtensionIdsExclude(asStringList(reporterExcludeNode))
                .withCriticalities(asCriticalitySet(criticalities))
                .withUntypedSeverityMapping(asSeverityMapping(severityMapping));
    }

    /**
     * Similar to {@link #parse(ModelNode)} but the extensions to use are provided by the caller straight away instead
     * of letting the caller use the builder to finish up the configuration.
     *
     * <p>Note that the returned pipeline configuration might not contain all the extensions available in
     * the classloader depending on the include/exclude filters in the configuration.
     *
     * @param json       the configuration node
     * @param analyzers  the set of analyzers to choose from
     * @param filters    the set of filters to choose from
     * @param transforms the set of transforms to choose from
     * @param reporters  the set of reporters to choose from
     * @return pipeline configuration corresponding to the provided configuration node
     * @see Builder#build()
     */
    public static PipelineConfiguration parse(ModelNode json, Collection<Class<? extends ApiAnalyzer>> analyzers,
            Collection<Class<? extends ElementFilter>> filters,
            Collection<Class<? extends DifferenceTransform<?>>> transforms,
            Collection<Class<? extends Reporter>> reporters) {

        return parse(json)
                .withAnalyzers(analyzers)
                .withFilters(filters)
                .withTransforms(transforms)
                .withReporters(reporters)
                .build();
    }

    private static List<String> asStringList(ModelNode listNode) {
        if (!listNode.isDefined()) {
            return Collections.emptyList();
        } else {
            return listNode.asList().stream().map(ModelNode::asString).collect(toList());
        }
    }

    private static Set<Criticality> asCriticalitySet(ModelNode node) {
        if (!node.isDefined()) {
            return emptySet();
        }

        return node.asList().stream()
                .map(n -> new Criticality(n.get("name").asString(), n.get("level").asInt()))
                .collect(toSet());
    }

    private static Map<DifferenceSeverity, String> asSeverityMapping(ModelNode node) {
        if (!node.isDefined()) {
            return emptyMap();
        }

        return node.asList().stream()
                .collect(Collectors.toMap(
                        n -> DifferenceSeverity.fromCamelCase(n.get("severity").asString()),
                        n -> n.get("criticality").asString()));
    }

    public PipelineConfiguration(Set<Class<? extends ApiAnalyzer>> apiAnalyzerTypes,
            Set<Class<? extends Reporter>> reporterTypes, Set<Class<? extends DifferenceTransform<?>>> transformTypes,
            Set<Class<? extends ElementFilter>> filterTypes, Set<List<String>> transformationBlocks,
            List<String> includedAnalyzerExtensionIds, List<String> excludedAnalyzerExtensionIds,
            List<String> includedReporterExtensionIds, List<String> excludedReporterExtensionIds,
            List<String> includedTransformExtensionIds, List<String> excludedTransformExtensionIds,
            List<String> includedFilterExtensionIds, List<String> excludedFilterExtensionIds,
            Set<Criticality> criticalities,
            Map<DifferenceSeverity, Criticality> severityMapping) {
        this.apiAnalyzerTypes = apiAnalyzerTypes;
        this.reporterTypes = reporterTypes;
        this.transformTypes = transformTypes;
        this.filterTypes = filterTypes;
        this.transformationBlocks = transformationBlocks;
        this.includedAnalyzerExtensionIds = includedAnalyzerExtensionIds;
        this.excludedAnalyzerExtensionIds = excludedAnalyzerExtensionIds;
        this.includedReporterExtensionIds = includedReporterExtensionIds;
        this.excludedReporterExtensionIds = excludedReporterExtensionIds;
        this.includedTransformExtensionIds = includedTransformExtensionIds;
        this.excludedTransformExtensionIds = excludedTransformExtensionIds;
        this.includedFilterExtensionIds = includedFilterExtensionIds;
        this.excludedFilterExtensionIds = excludedFilterExtensionIds;
        this.criticalities = criticalities;
        this.severityMapping = severityMapping;
    }

    public Set<Class<? extends ApiAnalyzer>> getApiAnalyzerTypes() {
        return apiAnalyzerTypes;
    }

    public Set<Class<? extends Reporter>> getReporterTypes() {
        return reporterTypes;
    }

    public Set<Class<? extends DifferenceTransform<?>>> getTransformTypes() {
        return transformTypes;
    }

    public Set<Class<? extends ElementFilter>> getFilterTypes() {
        return filterTypes;
    }

    public Set<List<String>> getTransformationBlocks() {
        return transformationBlocks;
    }

    public List<String> getIncludedAnalyzerExtensionIds() {
        return includedAnalyzerExtensionIds;
    }

    public List<String> getExcludedAnalyzerExtensionIds() {
        return excludedAnalyzerExtensionIds;
    }

    public List<String> getIncludedReporterExtensionIds() {
        return includedReporterExtensionIds;
    }

    public List<String> getExcludedReporterExtensionIds() {
        return excludedReporterExtensionIds;
    }

    public List<String> getIncludedTransformExtensionIds() {
        return includedTransformExtensionIds;
    }

    public List<String> getExcludedTransformExtensionIds() {
        return excludedTransformExtensionIds;
    }

    public List<String> getIncludedFilterExtensionIds() {
        return includedFilterExtensionIds;
    }

    public List<String> getExcludedFilterExtensionIds() {
        return excludedFilterExtensionIds;
    }

    public Set<Criticality> getCriticalities() {
        return criticalities;
    }

    public Map<DifferenceSeverity, Criticality> getSeverityMapping() {
        return severityMapping;
    }

    public static final class Builder {
        private Set<Class<? extends ApiAnalyzer>> analyzers = null;
        private Set<Class<? extends Reporter>> reporters = null;
        private Set<Class<? extends DifferenceTransform<?>>> transforms = null;
        private Set<Class<? extends ElementFilter>> filters = null;
        private Set<List<String>> transformationBlocks = null;
        private List<String> includedAnalyzerExtensionIds = null;
        private List<String> excludedAnalyzerExtensionIds = null;
        private List<String> includedReporterExtensionIds = null;
        private List<String> excludedReporterExtensionIds = null;
        private List<String> includedTransformExtensionIds = null;
        private List<String> excludedTransformExtensionIds = null;
        private List<String> includedFilterExtensionIds = null;
        private List<String> excludedFilterExtensionIds = null;
        private Set<Criticality> criticalities = null;
        private Map<DifferenceSeverity, String> severityMapping;

        public Builder withAnalyzersFromThreadContextClassLoader() {
            return withAnalyzers(ServiceTypeLoader.load(ApiAnalyzer.class));
        }

        public Builder withAnalyzersFrom(ClassLoader cl) {
            return withAnalyzers(ServiceTypeLoader.load(ApiAnalyzer.class, cl));
        }

        @SafeVarargs
        public final Builder withAnalyzers(Class<? extends ApiAnalyzer>... analyzers) {
            return withAnalyzers(Arrays.asList(analyzers));
        }

        public Builder withAnalyzers(Iterable<Class<? extends ApiAnalyzer>> analyzers) {
            if (this.analyzers == null) {
                this.analyzers = new HashSet<>();
            }
            for (Class<? extends ApiAnalyzer> a : analyzers) {
                this.analyzers.add(a);
            }

            return this;
        }

        public Builder withReportersFromThreadContextClassLoader() {
            return withReporters(ServiceTypeLoader.load(Reporter.class));
        }

        public Builder withReportersFrom(ClassLoader cl) {
            return withReporters(ServiceTypeLoader.load(Reporter.class, cl));
        }

        @SafeVarargs
        public final Builder withReporters(Class<? extends Reporter>... reporters) {
            return withReporters(Arrays.asList(reporters));
        }

        public Builder withReporters(Iterable<Class<? extends Reporter>> reporters) {
            if (this.reporters == null) {
                this.reporters = new HashSet<>();
            }
            for (Class<? extends Reporter> r : reporters) {
                this.reporters.add(r);
            }

            return this;
        }

        public Builder withTransformsFromThreadContextClassLoader() {
            //don't you love Java generics? ;)
            @SuppressWarnings("rawtypes")
            Iterable trs = ServiceTypeLoader.load(DifferenceTransform.class);

            @SuppressWarnings("unchecked")
            Iterable<Class<? extends DifferenceTransform<?>>> rtrs
                    = (Iterable<Class<? extends DifferenceTransform<?>>>) trs;

            return withTransforms(rtrs);
        }

        public Builder withTransformsFrom(ClassLoader cl) {
            //don't you love Java generics? ;)
            @SuppressWarnings("rawtypes")
            Iterable trs = ServiceTypeLoader.load(DifferenceTransform.class, cl);

            @SuppressWarnings("unchecked")
            Iterable<Class<? extends DifferenceTransform<?>>> rtrs
                    = (Iterable<Class<? extends DifferenceTransform<?>>>) trs;

            return withTransforms(rtrs);
        }

        @SafeVarargs
        public final Builder withTransforms(Class<? extends DifferenceTransform<?>>... transforms) {
            return withTransforms(Arrays.asList(transforms));
        }

        public Builder withTransforms(Iterable<Class<? extends DifferenceTransform<?>>> transforms) {
            if (this.transforms == null) {
                this.transforms = new HashSet<>();
            }
            for (Class<? extends DifferenceTransform<?>> t : transforms) {
                this.transforms.add(t);
            }

            return this;
        }

        public Builder withFiltersFromThreadContextClassLoader() {
            return withFilters(ServiceTypeLoader.load(ElementFilter.class));
        }

        public Builder withFiltersFrom(ClassLoader cl) {
            return withFilters(ServiceTypeLoader.load(ElementFilter.class, cl));
        }

        @SafeVarargs
        public final Builder withFilters(Class<? extends ElementFilter>... filters) {
            return withFilters(Arrays.asList(filters));
        }

        public Builder withFilters(Iterable<Class<? extends ElementFilter>> filters) {
            if (this.filters == null) {
                this.filters = new HashSet<>();
            }
            for (Class<? extends ElementFilter> f : filters) {
                this.filters.add(f);
            }

            return this;
        }

        public Builder withAllExtensionsFromThreadContextClassLoader() {
            return withAllExtensionsFrom(Thread.currentThread().getContextClassLoader());
        }

        public Builder withAllExtensionsFrom(ClassLoader cl) {
            return withAnalyzersFrom(cl).withFiltersFrom(cl).withReportersFrom(cl)
                    .withTransformsFrom(cl);
        }

        public Builder withTransformationBlocks(Set<List<String>> transformationBlocks) {
            this.transformationBlocks = new HashSet<>(transformationBlocks.size());

            for (List<String> block : transformationBlocks) {
                this.transformationBlocks.add(new ArrayList<>(block));
            }

            return this;
        }

        public Builder addTransformationBlock(List<String> transformationBlock) {
            if (transformationBlocks == null) {
                transformationBlocks = new HashSet<>();
            }
            transformationBlocks.add(new ArrayList<>(transformationBlock));
            return this;
        }

        public Builder withAnalyzerExtensionIdsInclude(List<String> analyzerExtensionIds) {
            this.includedAnalyzerExtensionIds = new ArrayList<>(analyzerExtensionIds);
            return this;
        }

        public Builder addAnalyzerExtensionIdInclude(String analyzerExtensionId) {
            if (this.includedAnalyzerExtensionIds == null) {
                this.includedAnalyzerExtensionIds = new ArrayList<>();
            }

            this.includedAnalyzerExtensionIds.add(analyzerExtensionId);
            return this;
        }

        public Builder withAnalyzerExtensionIdsExclude(List<String> analyzerExtensionIds) {
            this.excludedAnalyzerExtensionIds = new ArrayList<>(analyzerExtensionIds);
            return this;
        }

        public Builder addAnalyzerExtensionIdExclude(String analyzerExtensionId) {
            if (this.excludedAnalyzerExtensionIds == null) {
                this.excludedAnalyzerExtensionIds = new ArrayList<>();
            }

            this.excludedAnalyzerExtensionIds.add(analyzerExtensionId);
            return this;
        }

        public Builder withReporterExtensionIdsInclude(List<String> reporterExtensionIds) {
            this.includedReporterExtensionIds = new ArrayList<>(reporterExtensionIds);
            return this;
        }

        public Builder addReporterExtensionIdInclude(String reporterExtensionId) {
            if (this.includedReporterExtensionIds == null) {
                this.includedReporterExtensionIds = new ArrayList<>();
            }

            this.includedReporterExtensionIds.add(reporterExtensionId);
            return this;
        }

        public Builder withReporterExtensionIdsExclude(List<String> reporterExtensionIds) {
            this.excludedReporterExtensionIds = new ArrayList<>(reporterExtensionIds);
            return this;
        }

        public Builder addReporterExtensionIdExclude(String reporterExtensionId) {
            if (this.excludedReporterExtensionIds == null) {
                this.excludedReporterExtensionIds = new ArrayList<>();
            }

            this.excludedReporterExtensionIds.add(reporterExtensionId);
            return this;
        }

        public Builder withTransformExtensionIdsInclude(List<String> transformExtensionIds) {
            this.includedTransformExtensionIds = new ArrayList<>(transformExtensionIds);
            return this;
        }

        public Builder addTransformExtensionIdInclude(String transformExtensionId) {
            if (this.includedTransformExtensionIds == null) {
                this.includedTransformExtensionIds = new ArrayList<>();
            }

            this.includedTransformExtensionIds.add(transformExtensionId);
            return this;
        }

        public Builder withTransformExtensionIdsExclude(List<String> transformExtensionIds) {
            this.excludedTransformExtensionIds = new ArrayList<>(transformExtensionIds);
            return this;
        }

        public Builder addTransformExtensionIdExclude(String transformExtensionId) {
            if (this.excludedTransformExtensionIds == null) {
                this.excludedTransformExtensionIds = new ArrayList<>();
            }

            this.excludedTransformExtensionIds.add(transformExtensionId);
            return this;
        }

        public Builder withFilterExtensionIdsInclude(List<String> filterExtensionIds) {
            this.includedFilterExtensionIds = new ArrayList<>(filterExtensionIds);
            return this;
        }

        public Builder addFilterExtensionIdInclude(String filterExtensionId) {
            if (this.includedFilterExtensionIds == null) {
                this.includedFilterExtensionIds = new ArrayList<>();
            }

            this.includedFilterExtensionIds.add(filterExtensionId);
            return this;
        }

        public Builder withFilterExtensionIdsExclude(List<String> filterExtensionIds) {
            this.excludedFilterExtensionIds = new ArrayList<>(filterExtensionIds);
            return this;
        }

        public Builder addFilterExtensionIdExclude(String filterExtensionId) {
            if (this.excludedFilterExtensionIds == null) {
                this.excludedFilterExtensionIds = new ArrayList<>();
            }

            this.excludedFilterExtensionIds.add(filterExtensionId);
            return this;
        }

        public Builder withTransformationBlocks(ModelNode configuration) {
            if (configuration == null || !configuration.isDefined()) {
                return this;
            }

            List<ModelNode> blocks = configuration.asList();

            transformationBlocks = new HashSet<>();

            for (ModelNode block : blocks) {
                List<String> ids = block.asList().stream().map(ModelNode::asString).collect(toList());
                transformationBlocks.add(ids);
            }

            return this;
        }

        public Builder withCriticalities(Set<Criticality> criticalities) {
            if (criticalities == null || criticalities.isEmpty()) {
                return this;
            }

            if (this.criticalities == null) {
                this.criticalities = new HashSet<>();
            }

            this.criticalities.addAll(criticalities);
            return this;
        }

        public Builder addCriticality(Criticality criticality) {
            if (this.criticalities == null) {
                this.criticalities = new HashSet<>();
            }

            this.criticalities.add(criticality);
            return this;
        }

        public Builder withSeverityMapping(Map<DifferenceSeverity, Criticality> severityMapping) {
            if (severityMapping == null || severityMapping.isEmpty()) {
                return this;
            }

            if (this.severityMapping == null) {
                this.severityMapping = new EnumMap<>(DifferenceSeverity.class);
            }

            severityMapping.forEach((s, c) -> this.severityMapping.put(s, c.getName()));

            return this;
        }

        private Builder withUntypedSeverityMapping(Map<DifferenceSeverity, String> severityMapping) {
            if (severityMapping == null || severityMapping.isEmpty()) {
                return this;
            }

            if (this.severityMapping == null) {
                this.severityMapping = new EnumMap<>(DifferenceSeverity.class);
            }

            this.severityMapping.putAll(severityMapping);

            return this;
        }

        public Builder addSeverityMapping(DifferenceSeverity severity, Criticality criticality) {
            if (this.severityMapping == null) {
                this.severityMapping = new EnumMap<>(DifferenceSeverity.class);
            }

            this.severityMapping.put(severity, criticality.getName());
            return this;
        }

        /**
         * @return a new Revapi pipeline configuration
         * @throws IllegalStateException if there are no api analyzers or no reporters added.
         */
        public PipelineConfiguration build() throws IllegalStateException {
            analyzers = analyzers == null ? emptySet() : analyzers;
            reporters = reporters == null ? emptySet() : reporters;
            transforms = transforms == null ? emptySet() : transforms;
            filters = filters == null ? emptySet() : filters;
            transformationBlocks = transformationBlocks == null ? emptySet() : transformationBlocks;
            includedAnalyzerExtensionIds = includedAnalyzerExtensionIds == null ? emptyList() : includedAnalyzerExtensionIds;
            excludedAnalyzerExtensionIds = excludedAnalyzerExtensionIds == null ? emptyList() : excludedAnalyzerExtensionIds;
            includedReporterExtensionIds = includedReporterExtensionIds == null ? emptyList() : includedReporterExtensionIds;
            excludedReporterExtensionIds = excludedReporterExtensionIds == null ? emptyList() : excludedReporterExtensionIds;
            includedTransformExtensionIds = includedTransformExtensionIds == null ? emptyList() : includedTransformExtensionIds;
            excludedTransformExtensionIds = excludedTransformExtensionIds == null ? emptyList() : excludedTransformExtensionIds;
            includedFilterExtensionIds = includedFilterExtensionIds == null ? emptyList() : includedFilterExtensionIds;
            excludedFilterExtensionIds = excludedFilterExtensionIds == null ? emptyList() : excludedFilterExtensionIds;

            boolean defaultCriticalities = criticalities == null;
            criticalities = criticalities == null ? Criticality.defaultCriticalities() : criticalities;

            Map<DifferenceSeverity, Criticality> sm;
            if (severityMapping == null) {
                if (defaultCriticalities) {
                    sm = Criticality.defaultSeverityMapping();
                } else {
                    sm = new EnumMap<>(DifferenceSeverity.class);

                    Map<String, Criticality> cByName = criticalities.stream()
                            .collect(toMap(Criticality::getName, identity()));

                    for (Map.Entry<DifferenceSeverity, Criticality> e : Criticality.defaultSeverityMapping().entrySet()) {
                        // we might have redefined the criticalities with the same name, so let's use what's actually
                        // configured
                        Criticality realC = cByName.get(e.getValue().getName());
                        sm.put(e.getKey(), realC);
                    }
                }
            } else {
                Map<String, Criticality> cByName = criticalities.stream()
                        .collect(toMap(Criticality::getName, identity()));

                sm = new EnumMap<>(DifferenceSeverity.class);
                for (Map.Entry<DifferenceSeverity, String> e : severityMapping.entrySet()) {
                    Criticality c = cByName.get(e.getValue());
                    if (c == null) {
                        throw new IllegalArgumentException("Criticality '" + e.getValue() + "' not configured.");
                    }
                    sm.put(e.getKey(), c);
                }
            }

            Set<DifferenceSeverity> expectedMappings = EnumSet.allOf(DifferenceSeverity.class);
            expectedMappings.removeAll(sm.keySet());
            if (!expectedMappings.isEmpty()) {
                throw new IllegalArgumentException("The severity-to-criticality mapping is incomplete." +
                        " Missing mapping for: " + expectedMappings);
            }

            return new PipelineConfiguration(analyzers, reporters, transforms, filters, transformationBlocks,
                    includedAnalyzerExtensionIds, excludedAnalyzerExtensionIds, includedReporterExtensionIds,
                    excludedReporterExtensionIds, includedTransformExtensionIds, excludedTransformExtensionIds,
                    includedFilterExtensionIds, excludedFilterExtensionIds, criticalities, sm);
        }
    }
}
