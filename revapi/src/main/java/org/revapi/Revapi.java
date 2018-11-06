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

import static java.util.Collections.emptySortedSet;
import static java.util.Collections.singletonList;

import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisResult.ExtensionInstance;
import org.revapi.configuration.Configurable;
import org.revapi.configuration.ConfigurationValidator;
import org.revapi.configuration.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main entry point to the library. The instance of this class is initialized with the different extensions and then
 * can run analyses on APIs with different configurations using the {@link #analyze(AnalysisContext)} method.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Revapi {
    private static final Logger LOG = LoggerFactory.getLogger(Revapi.class);
    static final Logger TIMING_LOG = LoggerFactory.getLogger("revapi.analysis.timing");
    private static final long MAX_TRANSFORMATION_ITERATIONS = 1_000_000;

    private final PipelineConfiguration pipelineConfiguration;
    private final ConfigurationValidator configurationValidator;
    private final Map<String, Set<List<DifferenceTransform<?>>>> matchingTransformsCache = new HashMap<>();

    /**
     * Use the {@link #builder()} instead.
     *
     * @param pipelineConfiguration the configuration of the analysis pipeline
     * @throws java.lang.IllegalArgumentException if any of the parameters is null
     */
    public Revapi(PipelineConfiguration pipelineConfiguration) {
        this.pipelineConfiguration = pipelineConfiguration;
        this.configurationValidator = new ConfigurationValidator();
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validates the configuration of the analysis context.
     *
     * @param analysisContext the analysis context
     * @return the validation result
     */
    public ValidationResult validateConfiguration(@Nonnull AnalysisContext analysisContext) {
        ValidationResult validation = ValidationResult.success();

        Iterator<? extends Configurable> it = concat(
                pipelineConfiguration.getApiAnalyzerTypes().stream(),
                pipelineConfiguration.getFilterTypes().stream(),
                pipelineConfiguration.getReporterTypes().stream(),
                pipelineConfiguration.getTransformTypes().stream())
                .map(this::instantiate).iterator();

        validation = validate(analysisContext, validation, it);

        return validation;
    }

    public PipelineConfiguration getPipelineConfiguration() {
        return pipelineConfiguration;
    }

    /**
     * This instantiates the individual extensions and assigns the configurations to each one of them. The caller of
     * this method gains insight on what extensions with what configurations would be executed by the analysis.
     *
     * <p>Note that the extensions are instantiated but NOT initialized after this call.
     *
     * @param analysisContext the analysis context containing the "global" configuration of all extensions
     * @return the instantiated extensions and their individual configurations
     */
    public AnalysisResult.Extensions prepareAnalysis(@Nonnull AnalysisContext analysisContext) {
        Map<ExtensionInstance<ElementFilter>, AnalysisContext> filters = splitByConfiguration(analysisContext,
                pipelineConfiguration.getFilterTypes(), pipelineConfiguration.getIncludedFilterExtensionIds(),
                pipelineConfiguration.getExcludedFilterExtensionIds());
        Map<ExtensionInstance<Reporter>, AnalysisContext> reporters = splitByConfiguration(analysisContext,
                pipelineConfiguration.getReporterTypes(), pipelineConfiguration.getIncludedReporterExtensionIds(),
                pipelineConfiguration.getExcludedReporterExtensionIds());
        Map<ExtensionInstance<ApiAnalyzer>, AnalysisContext> analyzers = splitByConfiguration(analysisContext,
                pipelineConfiguration.getApiAnalyzerTypes(), pipelineConfiguration.getIncludedAnalyzerExtensionIds(),
                pipelineConfiguration.getExcludedAnalyzerExtensionIds());
        Map<ExtensionInstance<DifferenceTransform<?>>, AnalysisContext> transforms = splitByConfiguration(analysisContext,
                pipelineConfiguration.getTransformTypes(), pipelineConfiguration.getIncludedTransformExtensionIds(),
                pipelineConfiguration.getExcludedTransformExtensionIds());

        return new AnalysisResult.Extensions(analyzers, filters, reporters, transforms);
    }

    /**
     * Performs the analysis configured by the given analysis context.
     * <p>
     * Make sure to call the {@link AnalysisResult#close()} method (or perform the analysis in try-with-resources
     * block).
     *
     * @param analysisContext describes the analysis to be performed
     * @return a result object that has to be closed for the analysis to conclude
     */
    @SuppressWarnings("unchecked")
    public AnalysisResult analyze(@Nonnull AnalysisContext analysisContext) {
        TIMING_LOG.debug("Analysis starts");

        AnalysisResult.Extensions extensions = prepareAnalysis(analysisContext);

        if (extensions.getAnalyzers().isEmpty()) {
            throw new IllegalArgumentException("At least one analyzer needs to be present. Make sure there is at" +
                    " least one on the classpath and that the extension filters do not exclude all of them.");
        }

        StreamSupport.stream(extensions.spliterator(), false)
                .map(e -> (Map.Entry<ExtensionInstance<? extends Configurable>, AnalysisContext>) (Map.Entry) e)
                .forEach(e -> e.getKey().getInstance().initialize(e.getValue()));

        AnalysisProgress progress = new AnalysisProgress(extensions, pipelineConfiguration);

        TIMING_LOG.debug("Initialization complete.");

        matchingTransformsCache.clear();

        Exception error = null;
        try {
            for (ExtensionInstance<ApiAnalyzer> ia : extensions.getAnalyzers().keySet()) {
                analyzeWith(ia.getInstance(), analysisContext.getOldApi(), analysisContext.getNewApi(), progress);
            }
        } catch (Exception t) {
            error = t;
        }

        return new AnalysisResult(error, extensions);
    }

    private <T extends Configurable> Map<ExtensionInstance<T>, AnalysisContext>
    splitByConfiguration(AnalysisContext fullConfig, Set<Class<? extends T>> configurables,
            List<String> extensionIdIncludes, List<String> extensionIdExcludes) {

        Map<ExtensionInstance<T>, AnalysisContext> map = new HashMap<>();
        for (Class<? extends T> cc : configurables) {
            T c = instantiate(cc);
            String extensionId = c.getExtensionId();

            if (extensionId == null) {
                throw new IllegalArgumentException("Extension " + cc.getCanonicalName() + " has null extension id." +
                        " This is illegal.");
            }

            // apply the filtering
            if (!extensionIdIncludes.isEmpty() && !extensionIdIncludes.contains(extensionId)) {
                continue;
            }

            if (extensionIdExcludes.contains(extensionId)) {
                continue;
            }

            T inst = null;
            boolean configured = false;
            for (ModelNode config : fullConfig.getConfiguration().asList()) {
                String configExtension = config.get("extension").asString();
                if (!extensionId.equals(configExtension)) {
                    continue;
                }
                if (inst == null) {
                    inst = c;
                } else {
                    inst = instantiate(cc);
                }

                String instanceId = config.get("id").asString();

                ExtensionInstance<T> key = new ExtensionInstance<>(inst, instanceId);

                map.put(key, fullConfig.copyWithConfiguration(config.get("configuration").clone()));

                configured = true;
            }

            if (!configured) {
                map.put(new ExtensionInstance<>(c, null), fullConfig.copyWithConfiguration(new ModelNode()));
            }
        }

        return map;
    }

    private ValidationResult validate(@Nonnull AnalysisContext analysisContext, ValidationResult validationResult,
            Iterator<? extends Configurable> configurables) {
        while (configurables.hasNext()) {
            Configurable c = configurables.next();
            ValidationResult partial = configurationValidator.validate(analysisContext.getConfiguration(), c);
            validationResult = validationResult.merge(partial);
        }

        return validationResult;
    }

    private void analyzeWith(ApiAnalyzer apiAnalyzer, API oldApi, API newApi, AnalysisProgress progress)
            throws Exception {

        if (TIMING_LOG.isDebugEnabled()) {
            TIMING_LOG.debug("Commencing analysis using " + apiAnalyzer + " on:\nOld API:\n" + oldApi + "\n\nNew API:\n"
                    + newApi);
        }

        ArchiveAnalyzer oldAnalyzer = apiAnalyzer.getArchiveAnalyzer(oldApi);
        ArchiveAnalyzer newAnalyzer = apiAnalyzer.getArchiveAnalyzer(newApi);

        TIMING_LOG.debug("Obtaining API trees.");
        ElementForest oldTree = oldAnalyzer.analyze();
        ElementForest newTree = newAnalyzer.analyze();
        TIMING_LOG.debug("API trees obtained");

        try (DifferenceAnalyzer elementDifferenceAnalyzer = apiAnalyzer.getDifferenceAnalyzer(oldAnalyzer, newAnalyzer)) {
            TIMING_LOG.debug("Obtaining API roots");
            SortedSet<? extends Element> as = oldTree.getRoots();
            SortedSet<? extends Element> bs = newTree.getRoots();
            TIMING_LOG.debug("API roots obtained");

            if (LOG.isDebugEnabled()) {
                LOG.debug("Old tree: {}", oldTree);
                LOG.debug("New tree: {}", newTree);
            }

            TIMING_LOG.debug("Opening difference analyzer");
            elementDifferenceAnalyzer.open();
            analyze(apiAnalyzer.getCorrespondenceDeducer(), elementDifferenceAnalyzer, as, bs, progress);
            TIMING_LOG.debug("Closing difference analyzer");
        }
        TIMING_LOG.debug("Difference analyzer closed");
    }

    private void analyze(CorrespondenceComparatorDeducer deducer, DifferenceAnalyzer elementDifferenceAnalyzer,
            SortedSet<? extends Element> as, SortedSet<? extends Element> bs,
            AnalysisProgress progress) {

        List<Element> sortedAs = new ArrayList<>(as);
        List<Element> sortedBs = new ArrayList<>(bs);

        Stats.of("sorts").start();
        Comparator<? super Element> comp = deducer.sortAndGetCorrespondenceComparator(sortedAs, sortedBs);
        Stats.of("sorts").end(sortedAs, sortedBs);

        CoIterator<Element> it = new CoIterator<>(sortedAs.iterator(), sortedBs.iterator(), comp);

        while (it.hasNext()) {
            it.next();

            Element a = it.getLeft();
            Element b = it.getRight();

            LOG.trace("Inspecting {} and {}", a, b);

            Stats.of("filters").start();
            Set<ElementFilter> filters = progress.extensions.getFilters().keySet().stream()
                    .map(ExtensionInstance::getInstance)
                    .collect(Collectors.toSet());
            boolean analyzeThis =
                    (a == null || filtersApply(a, filters)) && (b == null || filtersApply(b, filters));
            Stats.of("filters").end(a, b);

            long beginDuration = 0;
            if (analyzeThis) {
                LOG.trace("Starting analysis of {} and {}.", a, b);
                Stats.of("analyses").start();
                Stats.of("analysisBegins").start();
                elementDifferenceAnalyzer.beginAnalysis(a, b);
                Stats.of("analysisBegins").end(a, b);
                beginDuration = Stats.of("analyses").reset();
            } else {
                LOG.trace("Elements {} and {} were filtered out of analysis.", a, b);
            }

            Stats.of("descends").start();
            boolean shouldDescend;
            if (a == null || b == null) {
                shouldDescend = (a == null || filtersDescend(a, filters))
                        && (b == null || filtersDescend(b, filters))
                        && elementDifferenceAnalyzer.isDescendRequired(a, b);
            } else {
                shouldDescend = filtersDescend(a, filters) && filtersDescend(b, filters);
            }
            Stats.of("descends").end(a, b);

            if (shouldDescend) {
                LOG.trace("Descending into {}, {} pair.", a, b);
                analyze(deducer, elementDifferenceAnalyzer, a == null ? emptySortedSet() : a.getChildren(),
                        b == null ? emptySortedSet() : b.getChildren(), progress);
            } else {
                LOG.trace("Filters disallowed descending into {} and {}.", a, b);
            }

            if (analyzeThis) {
                LOG.trace("Ending the analysis of {} and {}.", a, b);
                Stats.of("analyses").start();
                Stats.of("analysisEnds").start();
                Report r = elementDifferenceAnalyzer.endAnalysis(a, b);
                Stats.of("analysisEnds").end(a, b);
                Stats.of("analyses").end(beginDuration, new AbstractMap.SimpleEntry<>(a, b));
                transformAndReport(r, progress);
            } else {
                LOG.trace("Finished the skipped analysis of {} and {}.", a, b);
            }
        }
    }

    private <T> T instantiate(Class<? extends T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to instantiate extension: " + type, e);
        }
    }

    @SafeVarargs
    private static <T> Stream<T> concat(Stream<? extends T>... streams) {
        if (streams.length == 0) {
            return Stream.empty();
        } else {
            return concat(streams[0], streams, 1);
        }
    }

    private static <T> Stream<T> concat(Stream<? extends T> head, Stream<? extends T>[] all, int from) {
        if (from == all.length - 1) {
            return Stream.concat(head, all[from]);
        } else {
            return Stream.concat(head, concat(all[from], all, from + 1));
        }
    }

    private void transformAndReport(Report report, AnalysisProgress progress) {
        if (report == null) {
            return;
        }

        Stats.of("transforms").start();

        int iteration = 0;
        boolean listChanged;
        do {
            listChanged = false;

            ListIterator<Difference> it = report.getDifferences().listIterator();
            List<Difference> transformed = new ArrayList<>(1); //this will hopefully be the max of transforms
            while (it.hasNext()) {
                Difference d = it.next();
                transformed.clear();
                boolean differenceChanged = false;

                LOG.debug("Transformation iteration {}", iteration);

                for (List<DifferenceTransform<?>> tb : getTransformsForDifference(d, progress)) {
                    // it is the responsibility of the transform to declare the proper type.
                    // it will get a ClassCastException if it fails to declare a type that is common to all differences
                    // it can handle
                    @SuppressWarnings("unchecked")
                    List<DifferenceTransform<Element>> tBlock = (List<DifferenceTransform<Element>>) (List) tb;

                    Difference td = d;
                    for (DifferenceTransform<Element> t : tBlock) {
                        if (td == null) {
                            break;
                        }

                        try {
                            td = t.transform(report.getOldElement(), report.getNewElement(), td);
                        } catch (Exception e) {
                            LOG.warn("Difference transform " + t + " of class '" + t.getClass() + " threw an" +
                                    " exception while processing difference " + d + " on old element " +
                                    report.getOldElement() + " and new element " + report.getNewElement(), e);
                        }
                    }

                    // ignore if transformation returned null, meaning that it "swallowed" the difference..
                    if (td == null) {
                        differenceChanged = true;
                    } else if (!d.equals(td)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Difference transform(s) {} transform {} to {}", tBlock, d, td);
                        }

                        transformed.add(td);
                        differenceChanged = true;
                    }
                }

                if (differenceChanged) {
                    listChanged = true;
                    //we need to remove the element in either case
                    it.remove();
                    if (!transformed.isEmpty()) {
                        //if it was not removed, but transformed, let's add the transformed difference in the place of
                        //our currently removed element
                        for (Difference td : transformed) {
                            //this adds the new element *before* the currently pointed to index...
                            it.add(td);
                            //we want to check the newly added difference, so we need the iterator to point at it...
                            it.previous();
                        }
                    }
                }

                iteration++;

                if (iteration % 1000 == 0) {
                    LOG.warn("Transformation of differences in match report " + report + " has cycled " + iteration +
                            " times. Maybe we're in an infinite loop with differences transforming back and forth?");
                }

                if (iteration == MAX_TRANSFORMATION_ITERATIONS) {
                    throw new IllegalStateException("Transformation failed to settle in " + MAX_TRANSFORMATION_ITERATIONS +
                            " iterations. This is most probably an error in difference transform configuration that" +
                            " cycles between two or more changes back and forth.");
                }
            }
        } while (listChanged);

        Stats.of("transforms").end(report);

        if (!report.getDifferences().isEmpty()) {
            Stats.of("reports").start();
            for (ExtensionInstance<Reporter> ir : progress.extensions.getReporters().keySet()) {
                ir.getInstance().report(report);
            }
            Stats.of("reports").end(report);
        }
    }

    private Set<List<DifferenceTransform<?>>> getTransformsForDifference(Difference diff, AnalysisProgress progress) {
        Set<List<DifferenceTransform<?>>> ret = matchingTransformsCache.get(diff.code);
        if (ret == null) {
            ret = new HashSet<>();
            for (List<DifferenceTransform<?>> ts : progress.transformBlocks) {
                List<DifferenceTransform<?>> actualTs = new ArrayList<>(ts.size());
                for (DifferenceTransform<?> t : ts) {
                    for (Pattern p : t.getDifferenceCodePatterns()) {
                        if (p.matcher(diff.code).matches()) {
                            actualTs.add(t);

                            break;
                        }
                    }
                }
                ret.add(actualTs);
            }
            matchingTransformsCache.put(diff.code, ret);
        }

        return ret;
    }

    /**
     * This builder is merely a proxy to the {@link PipelineConfiguration} and its builder. It is provided just for
     * convenience (and also to keep backwards compatibility ;) ).
     */
    public static final class Builder {
        private final PipelineConfiguration.Builder pb = PipelineConfiguration.builder();

        @Nonnull
        public Builder withAnalyzersFromThreadContextClassLoader() {
            pb.withAnalyzersFromThreadContextClassLoader();
            return this;
        }

        @Nonnull
        public Builder withAnalyzersFrom(@Nonnull ClassLoader cl) {
            pb.withAnalyzersFrom(cl);
            return this;
        }

        @SafeVarargs
        @Nonnull
        public final Builder withAnalyzers(Class<? extends ApiAnalyzer>... analyzers) {
            pb.withAnalyzers(analyzers);
            return this;
        }

        @Nonnull
        public Builder withAnalyzers(@Nonnull Iterable<Class<? extends ApiAnalyzer>> analyzers) {
            pb.withAnalyzers(analyzers);
            return this;
        }

        @Nonnull
        public Builder withReportersFromThreadContextClassLoader() {
            pb.withReportersFromThreadContextClassLoader();
            return this;
        }

        @Nonnull
        public Builder withReportersFrom(@Nonnull ClassLoader cl) {
            pb.withReportersFrom(cl);
            return this;
        }

        @SafeVarargs
        @Nonnull
        public final Builder withReporters(Class<? extends Reporter>... reporters) {
            pb.withReporters(reporters);
            return this;
        }

        @Nonnull
        public Builder withReporters(@Nonnull Iterable<Class<? extends Reporter>> reporters) {
            pb.withReporters(reporters);
            return this;
        }

        @Nonnull
        public Builder withTransformsFromThreadContextClassLoader() {
            pb.withTransformsFromThreadContextClassLoader();
            return this;
        }

        @Nonnull
        public Builder withTransformsFrom(@Nonnull ClassLoader cl) {
            pb.withTransformsFrom(cl);
            return this;
        }

        @SafeVarargs
        @Nonnull
        public final Builder withTransforms(Class<? extends DifferenceTransform<?>>... transforms) {
            pb.withTransforms(transforms);
            return this;
        }

        @Nonnull
        public Builder withTransforms(@Nonnull Iterable<Class<? extends DifferenceTransform<?>>> transforms) {
            pb.withTransforms(transforms);
            return this;
        }

        @Nonnull
        public Builder withFiltersFromThreadContextClassLoader() {
            pb.withFiltersFromThreadContextClassLoader();
            return this;
        }

        @Nonnull
        public Builder withFiltersFrom(@Nonnull ClassLoader cl) {
            pb.withFiltersFrom(cl);
            return this;
        }

        @SafeVarargs
        @Nonnull
        public final Builder withFilters(Class<? extends ElementFilter>... filters) {
            pb.withFilters(filters);
            return this;
        }

        @Nonnull
        public Builder withFilters(@Nonnull Iterable<Class<? extends ElementFilter>> filters) {
            pb.withFilters(filters);
            return this;
        }

        @Nonnull
        public Builder withAllExtensionsFromThreadContextClassLoader() {
            pb.withAllExtensionsFromThreadContextClassLoader();
            return this;
        }

        @Nonnull
        public Builder withAllExtensionsFrom(@Nonnull ClassLoader cl) {
            pb.withAllExtensionsFrom(cl);
            return this;
        }

        public Builder withTransformationBlocks(Set<List<String>> blocks) {
            pb.withTransformationBlocks(blocks);
            return this;
        }

        public Builder addTransformationBlock(List<String> block) {
            pb.addTransformationBlock(block);
            return this;
        }

        /**
         * @return a new Revapi instance
         * @throws IllegalStateException if there are no api analyzers or no reporters added.
         */
        @Nonnull
        public Revapi build() throws IllegalStateException {
            return new Revapi(pb.build());
        }
    }

    private static boolean filtersApply(Element element, Iterable<? extends ElementFilter> filters) {
        for (ElementFilter f : filters) {
            String name = f.getClass().getName() + ".applies";
            Stats.of(name).start();
            boolean applies = f.applies(element);
            Stats.of(name).end(element);
            if (!applies) {
                return false;
            }
        }
        return true;
    }

    private static boolean filtersDescend(Element element, Iterable<? extends ElementFilter> filters) {
        Iterator<? extends ElementFilter> it = filters.iterator();
        boolean hasNoFilters = !it.hasNext();

        while (it.hasNext()) {
            ElementFilter f = it.next();
            String name = f.getClass().getName() + ".shouldDescendInto";
            Stats.of(name).start();
            boolean should = f.shouldDescendInto(element);
            Stats.of(name).end(element);
            if (should) {
                return true;
            }
        }

        return hasNoFilters;
    }

    private static final class AnalysisProgress {
        final AnalysisResult.Extensions extensions;
        final Set<List<DifferenceTransform<?>>> transformBlocks;

        AnalysisProgress(AnalysisResult.Extensions extensions, PipelineConfiguration configuration) {
            this.extensions = extensions;
            this.transformBlocks = groupTransformsToBlocks(extensions, configuration);
        }

        private static Set<List<DifferenceTransform<?>>>
        groupTransformsToBlocks(AnalysisResult.Extensions extensions, PipelineConfiguration configuration) {
            Set<List<DifferenceTransform<?>>> ret = new HashSet<>();

            Map<String, List<DifferenceTransform<?>>> transformsById = new HashMap<>();
            for (ExtensionInstance<DifferenceTransform<?>> t : extensions.getTransforms().keySet()) {
                String configurationId = t.getId();
                String extensionId = t.getInstance().getExtensionId();

                if (configurationId != null) {
                    transformsById.computeIfAbsent(configurationId, __ -> new ArrayList<>()).add(t.getInstance());
                }
                transformsById.computeIfAbsent(extensionId, __ -> new ArrayList<>()).add(t.getInstance());
            }

            for (List<String> ids : configuration.getTransformationBlocks()) {
                List<DifferenceTransform<?>> ts = new ArrayList<>(ids.size());

                for (String id : ids) {
                    List<DifferenceTransform<?>> candidates = transformsById.remove(id);
                    if (candidates == null) {
                        throw new IllegalArgumentException(
                                "Unrecognized id in the transformation block configuration: " + id);
                    } else if (candidates.isEmpty()) {
                        throw new IllegalArgumentException("There is no transform with extension id or explicit id '"
                                + id + "'. Please fix the pipeline configuration.");
                    } else if (candidates.size() > 1) {
                        throw new IllegalArgumentException("There is more than 1 transform with extension id or" +
                                " explicit id '" + id + "'. Please fix the pipeline configuration and use unique ids" +
                                " for extension configurations.");
                    } else {
                        ts.add(candidates.get(0));
                    }
                }

                ret.add(ts);
            }

            // now we're left with the transformations that are not grouped into any explicit blocks. Let's make them
            // single-element blocks

            transformsById.values().stream().flatMap(List::stream).forEach(t -> ret.add(singletonList(t)));

            return ret;
        }
    }
}
