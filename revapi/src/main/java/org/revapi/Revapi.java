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

import static java.util.Collections.emptySortedSet;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.revapi.AnalysisResult.ExtensionInstance;
import org.revapi.configuration.Configurable;
import org.revapi.configuration.ConfigurationValidator;
import org.revapi.configuration.JSONUtil;
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
        TIMING_LOG.debug("Validation starts");

        ValidationResult validation = ValidationResult.success();

        // even though we're not using the extensions much during validation and we actually don't run any analysis
        // at all, let's just use the same method for instantiating the extensions as during the analysis even though we
        // actually don't need the extensions classified by their type.
        AnalysisResult.Extensions exts = prepareAnalysis(analysisContext);
        validation = validate(analysisContext, validation, exts);

        TIMING_LOG.debug("Validation finished");

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
    public AnalysisResult.Extensions prepareAnalysis(AnalysisContext analysisContext) {
        Map<ExtensionInstance<TreeFilterProvider>, AnalysisContext> filters = splitByConfiguration(analysisContext,
                pipelineConfiguration.getTreeFilterTypes(), pipelineConfiguration.getIncludedFilterExtensionIds(),
                pipelineConfiguration.getExcludedFilterExtensionIds());
        Map<ExtensionInstance<Reporter>, AnalysisContext> reporters = splitByConfiguration(analysisContext,
                pipelineConfiguration.getReporterTypes(), pipelineConfiguration.getIncludedReporterExtensionIds(),
                pipelineConfiguration.getExcludedReporterExtensionIds());
        Map<ExtensionInstance<ApiAnalyzer<?>>, AnalysisContext> analyzers = splitByConfiguration(analysisContext,
                pipelineConfiguration.getApiAnalyzerTypes(), pipelineConfiguration.getIncludedAnalyzerExtensionIds(),
                pipelineConfiguration.getExcludedAnalyzerExtensionIds());
        Map<ExtensionInstance<DifferenceTransform<?>>, AnalysisContext> transforms = splitByConfiguration(analysisContext,
                pipelineConfiguration.getTransformTypes(), pipelineConfiguration.getIncludedTransformExtensionIds(),
                pipelineConfiguration.getExcludedTransformExtensionIds());
        Map<ExtensionInstance<ElementMatcher>, AnalysisContext> matchers = splitByConfiguration(analysisContext,
                pipelineConfiguration.getMatcherTypes(), pipelineConfiguration.getIncludedMatcherExtensionIds(),
                pipelineConfiguration.getExcludedMatcherExtensionIds());

        BiFunction<Object, AnalysisContext, AnalysisContext> addMatchers =
                (__, ctx) -> ctx.copyWithMatchers(matchers.keySet().stream().map(ExtensionInstance::getInstance)
                        .collect(toSet()));

        filters.replaceAll(addMatchers);
        reporters.replaceAll(addMatchers);
        analyzers.replaceAll(addMatchers);
        transforms.replaceAll(addMatchers);
        matchers.replaceAll(addMatchers);

        return new AnalysisResult.Extensions(analyzers, filters, reporters, transforms, matchers);
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

        extensions.stream()
                .map(e -> (Map.Entry<ExtensionInstance<Configurable>, AnalysisContext>) (Map.Entry) e)
                .forEach(e -> {
                    ExtensionInstance<Configurable> i = e.getKey();

                    try {
                        i.getInstance().initialize(e.getValue());
                    } catch (Exception ex) {
                        throw new IllegalStateException("Extension " + i.getInstance().getExtensionId()
                                + (i.getId() == null ? "" : "(id=" + i.getId() + ")")
                                + " failed to initialize: " + ex.getMessage(), ex);
                    }
                });

        AnalysisProgress progress = new AnalysisProgress(extensions, pipelineConfiguration, analysisContext.getOldApi(),
                analysisContext.getNewApi());

        TIMING_LOG.debug("Initialization complete.");

        matchingTransformsCache.clear();

        Exception error = null;
        try {
            for (ExtensionInstance<ApiAnalyzer<?>> ia : extensions.getAnalyzers().keySet()) {
                analyzeWith(ia.getInstance(), progress);
                progress.reports.clear();
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
                extensionId = "$$%%(@#_)I#@)(*)(#$)(@#$__IMPROBABLE, right??!?!?!";
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
            for (JsonNode config : fullConfig.getConfigurationNode()) {
                if (config.path("extension").isMissingNode()) {
                    throw new IllegalArgumentException("Invalid configuration: missing the extension name.");
                }
                String configExtension = config.get("extension").asText();
                if (!extensionId.equals(configExtension)) {
                    continue;
                }
                if (inst == null) {
                    inst = c;
                } else {
                    inst = instantiate(cc);
                }

                JsonNode idNode = config.get("id");

                String instanceId = !JSONUtil.isNullOrUndefined(idNode) ? idNode.asText() : null;

                ExtensionInstance<T> key = new ExtensionInstance<>(inst, instanceId);

                map.put(key, fullConfig.copyWithConfiguration(config.get("configuration").deepCopy()));

                configured = true;
            }

            if (!configured) {
                map.put(new ExtensionInstance<>(c, null), fullConfig.copyWithConfiguration(JsonNodeFactory.instance.nullNode()));
            }
        }

        return map;
    }

    private ValidationResult validate(@Nonnull AnalysisContext analysisContext, ValidationResult validationResult,
            AnalysisResult.Extensions configurables) {
        for (Map.Entry<ExtensionInstance<?>, AnalysisContext> e : configurables) {
            if (!(e.getKey().getInstance() instanceof Configurable)) {
                continue;
            }

            Configurable c = (Configurable) e.getKey().getInstance();
            ValidationResult partial = configurationValidator.validate(analysisContext.getConfigurationNode(), c);
            validationResult = validationResult.merge(partial);
        }

        return validationResult;
    }

    private <E extends Element<E>>void analyzeWith(ApiAnalyzer<E> apiAnalyzer, AnalysisProgress config)
            throws Exception {

        API oldApi = config.oldApi;
        API newApi = config.newApi;

        if (TIMING_LOG.isDebugEnabled()) {
            TIMING_LOG.debug("Commencing analysis using " + apiAnalyzer + " on:\nOld API:\n" + oldApi + "\n\nNew API:\n"
                    + newApi);
        }

        ArchiveAnalyzer<E> oldAnalyzer = apiAnalyzer.getArchiveAnalyzer(oldApi);
        ArchiveAnalyzer<E> newAnalyzer = apiAnalyzer.getArchiveAnalyzer(newApi);

        TreeFilterProvider filter = unionFilter(config.extensions);

        TIMING_LOG.debug("Obtaining API trees.");

        ElementForest<E> oldTree = analyzeAndPrune(oldAnalyzer, filter);
        ElementForest<E> newTree = analyzeAndPrune(newAnalyzer, filter);

        TIMING_LOG.debug("API trees obtained");

        try (DifferenceAnalyzer<E> elementDifferenceAnalyzer = apiAnalyzer.getDifferenceAnalyzer(oldAnalyzer, newAnalyzer)) {
            TIMING_LOG.debug("Obtaining API roots");
            SortedSet<E> as = oldTree.getRoots();
            SortedSet<E> bs = newTree.getRoots();
            TIMING_LOG.debug("API roots obtained");

            if (LOG.isDebugEnabled()) {
                LOG.debug("Old tree: {}", oldTree);
                LOG.debug("New tree: {}", newTree);
            }

            TIMING_LOG.debug("Opening difference analyzer");
            elementDifferenceAnalyzer.open();

            Map<DifferenceTransform<?>, Optional<DifferenceTransform.TraversalTracker<E>>> allTransforms =
                    config.extensions.getTransforms().keySet().stream()
                            .map(ExtensionInstance::getInstance)
                            .collect(toMap(
                                    i -> (DifferenceTransform<?>) i,
                                    i -> i.startTraversal(apiAnalyzer, oldAnalyzer, newAnalyzer)));

            List<DifferenceTransform.TraversalTracker<E>> activeTransforms = allTransforms.values().stream()
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .collect(toList());

            analyze(apiAnalyzer.getCorrespondenceDeducer(), elementDifferenceAnalyzer, as, bs,
                    activeTransforms, config);

            allTransforms.values().forEach(tr -> tr.ifPresent(DifferenceTransform.TraversalTracker::endTraversal));

            Set<Reporter> reporters = config.extensions.getReporters().keySet().stream()
                    .map(ExtensionInstance::getInstance).collect(toSet());

            config.reports.forEach(r -> {
                transform(r, allTransforms.keySet(), null, false, config);

                if (!r.getDifferences().isEmpty()) {
                    Stats.of("reports").start();

                    // make sure all the differences have a non-null criticality before being sent to the reporters
                    ListIterator<Difference> it = r.getDifferences().listIterator();
                    while (it.hasNext()) {
                        Difference orig = it.next();
                        if (orig.criticality == null) {
                            DifferenceSeverity maxSeverity = orig.classification.values().stream()
                                    .max(comparingInt(Enum::ordinal))
                                    .orElse(DifferenceSeverity.EQUIVALENT);

                            // all extensions share the criticality mapping and we're guaranteed to have at least 1 api analyzer
                            AnalysisContext ctx = config.extensions.getFirstConfigurationOrNull(ApiAnalyzer.class);
                            if (ctx == null) {
                                throw new IllegalStateException("There should be at least 1 API analyzer during the analysis" +
                                        "progress.");
                            }

                            Difference.Builder d = Difference.copy(orig)
                                    .withCriticality(ctx.getDefaultCriticality(maxSeverity));

                            it.set(d.build());
                        }
                    }

                    for (Reporter reporter : reporters) {
                        reporter.report(r);
                    }
                    Stats.of("reports").end(r);
                }
            });

            allTransforms.forEach((trans, track) -> {
                trans.endTraversal(track.orElse(null));
            });

            TIMING_LOG.debug("Closing difference analyzer");
        }

        TIMING_LOG.debug("Difference analyzer closed");
    }

    private <E extends Element<E>> ElementForest<E> analyzeAndPrune(ArchiveAnalyzer<E> analyzer, TreeFilterProvider filter) {
        TreeFilter<E> tf = filter.filterFor(analyzer).orElseGet(TreeFilter::matchAndDescend);
        ElementForest<E> forest = analyzer.analyze(tf);
        analyzer.prune(forest);

        return forest;
    }

    private <E extends Element<E>> void analyze(CorrespondenceComparatorDeducer<E> deducer, DifferenceAnalyzer<E> elementDifferenceAnalyzer,
            SortedSet<E> as, SortedSet<E> bs,
            Collection<DifferenceTransform.TraversalTracker<E>> activeTransforms, AnalysisProgress progress) {

        List<E> sortedAs = new ArrayList<>(as);
        List<E> sortedBs = new ArrayList<>(bs);

        Stats.of("sorts").start();
        Comparator<? super E> comp = deducer.sortAndGetCorrespondenceComparator(sortedAs, sortedBs);
        Stats.of("sorts").end(sortedAs, sortedBs);

        CoIterator<E> it = new CoIterator<>(sortedAs.iterator(), sortedBs.iterator(), comp);

        while (it.hasNext()) {
            it.next();

            E a = it.getLeft();
            E b = it.getRight();

            LOG.trace("Inspecting {} and {}", a, b);

            long beginDuration;
            Stats.of("analyses").start();
            Stats.of("analysisBegins").start();

            List<DifferenceTransform.TraversalTracker<E>> childTransforms = activeTransforms.stream()
                    .filter(t -> (a != null && b != null) | t.startElements(a, b)) //intentional non-short-circuit "or"
                    .collect(toList());

            elementDifferenceAnalyzer.beginAnalysis(a, b);

            Stats.of("analysisBegins").end(a, b);
            beginDuration = Stats.of("analyses").reset();

            boolean shouldDescend = a != null && b != null;
            if (!shouldDescend) {
                shouldDescend = !childTransforms.isEmpty() || elementDifferenceAnalyzer. isDescendRequired(a, b);
            }

            if (shouldDescend) {
                LOG.trace("Descending into {}, {} pair.", a, b);
                analyze(deducer, elementDifferenceAnalyzer,
                        a == null ? emptySortedSet() : a.getChildren(),
                        b == null ? emptySortedSet() : b.getChildren(),
                        childTransforms, progress);
            } else {
                LOG.trace("Filters disallowed descending into {} and {}.", a, b);
            }

            Stats.of("analyses").start();
            Stats.of("analysisEnds").start();

            Report r = elementDifferenceAnalyzer.endAnalysis(a, b);
            addDefaultAttachments(r, progress);

            progress.reports.add(r);

            Stats.of("analysisEnds").end(a, b);
            Stats.of("analyses").end(beginDuration, new AbstractMap.SimpleEntry<>(a, b));

            for (DifferenceTransform.TraversalTracker<E> t : activeTransforms) {
                t.endElements(a, b);
            }
        }
    }

    private void addDefaultAttachments(Report r, AnalysisProgress progress) {
        Element oldElement = r.getOldElement();
        Element newElement = r.getNewElement();
        API oldApi = progress.oldApi;
        API newApi = progress.newApi;

        ListIterator<Difference> it = r.getDifferences().listIterator();
        while (it.hasNext()) {
            Difference.Builder d = Difference.copy(it.next());
            if (oldElement != null && oldElement.getArchive() != null) {
                d.addAttachment("oldArchive", oldElement.getArchive().getName());
                d.addAttachment("oldArchiveRole", oldApi.getArchiveRole(oldElement.getArchive()).name().toLowerCase());
            }

            if (newElement != null && newElement.getArchive() != null) {
                d.addAttachment("newArchive", newElement.getArchive().getName());
                d.addAttachment("newArchiveRole", newApi.getArchiveRole(newElement.getArchive()).name().toLowerCase());
            }

            it.set(d.build());
        }

    }

    private <T> T instantiate(Class<? extends T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to instantiate extension: " + type, e);
        }
    }

    private TreeFilterProvider unionFilter(AnalysisResult.Extensions extensions) {
        return new TreeFilterProvider() {
            @Override
            public <E extends Element<E>> Optional<TreeFilter<E>> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
                List<TreeFilter<E>> applicables = extensions.getFilters().keySet().stream()
                        .map(f -> f.getInstance().filterFor(archiveAnalyzer).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(toList());

                return Optional.of(new TreeFilter<E>() {
                    @Override
                    public FilterStartResult start(E element) {
                        return applicables.stream().map(f -> f.start(element)).reduce(FilterStartResult::and)
                                .orElse(FilterStartResult.matchAndDescend());
                    }

                    @Override
                    public FilterFinishResult finish(E element) {
                        return applicables.stream().map(f -> f.finish(element)).reduce(FilterFinishResult::and)
                                .orElse(FilterFinishResult.doesntMatch());
                    }

                    @Override
                    public Map<E, FilterFinishResult> finish() {
                        return applicables.stream().map(TreeFilter::finish)
                                .reduce(new HashMap<>(), (ret, res) -> {
                                    ret.putAll(res);
                                    return ret;
                                });
                    }
                });
            }

            @Override
            public void close() throws Exception {
                List<Exception> failures = new ArrayList<>(1);

                extensions.getFilters().keySet().forEach(f -> {
                    try {
                        f.getInstance().close();
                    } catch (Exception e) {
                        failures.add(e);
                    }
                });

                if (!failures.isEmpty()) {
                    Iterator<Exception> it = failures.iterator();
                    Exception ex = new Exception(it.next());
                    it.forEachRemaining(ex::addSuppressed);
                    throw ex;
                }
            }

            @Nullable
            @Override
            public String getExtensionId() {
                return null;
            }

            @Nullable
            @Override
            public Reader getJSONSchema() {
                return null;
            }

            @Override
            public void initialize(@Nonnull AnalysisContext analysisContext) {
                extensions.getFilters().forEach((i, __) -> i.getInstance().initialize(analysisContext));
            }
        };
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

    private <E extends Element<E>> void transform(Report report, Collection<DifferenceTransform<?>> eligibleTransforms,
            Collection<Report> undecidedReports, boolean collectUndecided, AnalysisProgress progress) {

        if (report == null) {
            return;
        }

        Stats.of("transforms").start();

        Element<?> oldElement = report.getOldElement();
        Element<?> newElement = report.getNewElement();

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
                boolean undecided = false;

                LOG.debug("Transformation iteration {}", iteration);

                for (List<DifferenceTransform<?>> tb : getTransformsForDifference(d, eligibleTransforms, progress)) {
                    List<Difference> blockResults = new ArrayList<>(singletonList(d));

                    for (DifferenceTransform<?> t : tb) {
                        ListIterator<Difference> blockResultsIt = blockResults.listIterator();

                        // it is the responsibility of the transform to declare the proper type.
                        // it will get a ClassCastException if it fails to declare a type that is common to all differences
                        // it can handle
                        @SuppressWarnings("rawtypes")
                        DifferenceTransform transform = t;

                        while (blockResultsIt.hasNext()) {
                            Difference currentDiff = blockResultsIt.next();
                            TransformationResult res;
                            try {
                                //noinspection unchecked
                                res = transform.tryTransform(oldElement, newElement, currentDiff);
                            } catch (Exception e) {
                                res = TransformationResult.keep();
                                LOG.warn("Difference transform " + t + " of class '" + t.getClass() + " threw an" +
                                        " exception while processing difference " + currentDiff + " on old element " +
                                        report.getOldElement() + " and new element " + report.getNewElement(), e);
                            }

                            switch (res.getResolution()) {
                            case KEEP:
                                // good, let's continue with the next transform in the block
                                break;
                            case REPLACE:
                                blockResultsIt.remove();
                                if (res.getDifferences() != null) {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Difference transform {} transforms {} to {}", t.getClass(),
                                                currentDiff, res.getDifferences());
                                    }
                                    res.getDifferences().forEach(blockResultsIt::add);
                                }
                                break;
                            case DISCARD:
                                blockResultsIt.remove();
                                break;
                            case UNDECIDED:
                                // when we're not collecting the undecided reports, this is the same as KEEP
                                if (collectUndecided) {
                                    undecidedReports.add(report);
                                    undecided = true;
                                }
                                break;
                            }
                        }
                    }

                    // ignore if the transforms in the block swallowed all the differences
                    if (blockResults.isEmpty()) {
                        differenceChanged = true;
                    } else if (blockResults.size() > 1 || !d.equals(blockResults.get(0))) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Difference transform(s) {} transform {} to {}", tb, d, blockResults);
                        }

                        transformed.addAll(blockResults);
                        differenceChanged = true;
                    }
                }

                if (!undecided && differenceChanged) {
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
    }

    private Set<List<DifferenceTransform<?>>> getTransformsForDifference(Difference diff,
            Collection<DifferenceTransform<?>> transforms, AnalysisProgress config) {
        Set<List<DifferenceTransform<?>>> ret = matchingTransformsCache.get(diff.code);
        if (ret == null) {
            ret = new HashSet<>();
            for (List<DifferenceTransform<?>> ts : config.transformBlocks) {
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

        ret.forEach(l -> l.retainAll(transforms));

        return ret;
    }

    /**
     * This builder is merely a proxy to the {@link PipelineConfiguration} and its builder. It is provided just for
     * convenience (and also to keep backwards compatibility ;) ).
     *
     * @deprecated favor the {@link PipelineConfiguration.Builder}
     */
    @Deprecated
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
        public final Builder withTransforms(Class<? extends DifferenceTransform>... transforms) {
            pb.withTransforms(transforms);
            return this;
        }

        @Nonnull
        public Builder withTransforms(@Nonnull Iterable<Class<? extends DifferenceTransform>> transforms) {
            pb.withTransforms(transforms);
            return this;
        }

        @Nonnull
        @SuppressWarnings({"unchecked", "RedundantCast"})
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
        public final Builder withFilters(Class<? extends TreeFilterProvider>... filters) {
            pb.withFilters(filters);
            return this;
        }

        @Nonnull
        public Builder withFilters(@Nonnull Iterable<Class<? extends TreeFilterProvider>> filters) {
            pb.withFilters(filters);
            return this;
        }

        @Nonnull
        public Builder withMatchersFromThreadContextClassLoader() {
            pb.withMatchersFromThreadContextClassLoader();
            return this;
        }

        @Nonnull
        public Builder withMatchersFrom(@Nonnull ClassLoader cl) {
            pb.withMatchersFrom(cl);
            return this;
        }

        @SafeVarargs
        @Nonnull
        public final Builder withMatchers(Class<? extends ElementMatcher>... matchers) {
            pb.withMatchers(matchers);
            return this;
        }

        @Nonnull
        public Builder withMatchers(@Nonnull Iterable<Class<? extends ElementMatcher>> matchers) {
            pb.withMatchers(matchers);
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

    private static final class AnalysisProgress {
        final AnalysisResult.Extensions extensions;
        final Set<List<DifferenceTransform<?>>> transformBlocks;
        final API oldApi;
        final API newApi;
        final List<Report> reports;

        AnalysisProgress(AnalysisResult.Extensions extensions, PipelineConfiguration configuration,
                API oldApi, API newApi) {
            this.extensions = extensions;
            this.oldApi = oldApi;
            this.newApi = newApi;
            this.transformBlocks = groupTransformsToBlocks(extensions, configuration);
            this.reports = new ArrayList<>();
        }

        private static Set<List<DifferenceTransform<?>>>
        groupTransformsToBlocks(AnalysisResult.Extensions extensions, PipelineConfiguration configuration) {
            Set<List<DifferenceTransform<?>>> ret = new HashSet<>();

            Map<String, List<DifferenceTransform<?>>> transformsById = new HashMap<>();
            Set<DifferenceTransform<?>> allTransforms = newSetFromMap(new IdentityHashMap<>());
            for (ExtensionInstance<DifferenceTransform<?>> t : extensions.getTransforms().keySet()) {
                String configurationId = t.getId();
                String extensionId = t.getInstance().getExtensionId();

                if (configurationId != null) {
                    transformsById.computeIfAbsent(configurationId, __ -> new ArrayList<>()).add(t.getInstance());
                }
                transformsById.computeIfAbsent(extensionId, __ -> new ArrayList<>()).add(t.getInstance());
                allTransforms.add(t.getInstance());
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
                        DifferenceTransform<?> t = candidates.get(0);
                        ts.add(t);
                        allTransforms.remove(t);
                    }
                }

                ret.add(ts);
            }

            // now we're left with the transformations that are not grouped into any explicit blocks. Let's make them
            // single-element blocks

            allTransforms.forEach(t -> ret.add(singletonList(t)));

            return ret;
        }
    }
}
