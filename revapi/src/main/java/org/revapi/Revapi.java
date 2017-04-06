/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;

import org.jboss.dmr.ModelNode;
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
    private final Set<Class<? extends ApiAnalyzer>> availableApiAnalyzers;
    private final Set<Class<? extends Reporter>> availableReporters;
    private final Set<Class<? extends DifferenceTransform<?>>> availableTransforms;
    private final Set<Class<? extends ElementFilter>> availableFilters;
    private final ConfigurationValidator configurationValidator;
    private final Map<String, List<DifferenceTransform<?>>> matchingTransformsCache = new HashMap<>();

    /**
     * Use the {@link #builder()} instead.
     *
     * @param availableApiAnalyzers the set of analyzers to use
     * @param availableReporters    the set of reporters to use
     * @param availableTransforms   the set of transforms to use
     * @param elementFilters        the set of element filters to use
     * @throws java.lang.IllegalArgumentException if any of the parameters is null
     */
    public Revapi(@Nonnull Set<Class<? extends ApiAnalyzer>> availableApiAnalyzers,
                  @Nonnull Set<Class<? extends Reporter>> availableReporters,
                  @Nonnull Set<Class<? extends DifferenceTransform<?>>> availableTransforms,
                  @Nonnull Set<Class<? extends ElementFilter>> elementFilters) {

        this.availableApiAnalyzers = availableApiAnalyzers;
        this.availableReporters = availableReporters;
        this.availableTransforms = availableTransforms;
        this.availableFilters = elementFilters;
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
                availableApiAnalyzers.stream(),
                availableFilters.stream(),
                availableReporters.stream(),
                availableTransforms.stream())
                .map(this::instantiate).iterator();

        validation = validate(analysisContext, validation, it);

        return validation;
    }

    /**
     * @return the set of api analyzers available to this Revapi instance
     */
    public Set<Class<? extends ApiAnalyzer>> getApiAnalyzerTypes() {
        return Collections.unmodifiableSet(availableApiAnalyzers);
    }

    /**
     * @return the set of reporters available to this Revapi instance
     */
    public Set<Class<? extends Reporter>> getReporterTypes() {
        return Collections.unmodifiableSet(availableReporters);
    }

    /**
     * @return the set of difference transforms available to this Revapi instance
     */
    public Set<Class<? extends DifferenceTransform<?>>> getDifferenceTransformTypes() {
        return Collections.unmodifiableSet(availableTransforms);
    }

    /**
     * @return the set of element filters available to this Revapi instance
     */
    public Set<Class<? extends ElementFilter>> getElementFilterTypes() {
        return Collections.unmodifiableSet(availableFilters);
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
        Map<ElementFilter, AnalysisContext> filters = splitByConfiguration(analysisContext, availableFilters);
        Map<Reporter, AnalysisContext> reporters = splitByConfiguration(analysisContext, availableReporters);
        Map<ApiAnalyzer, AnalysisContext> analyzers = splitByConfiguration(analysisContext, availableApiAnalyzers);
        Map<DifferenceTransform<?>, AnalysisContext> transforms = splitByConfiguration(analysisContext, availableTransforms);

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

        StreamSupport.stream(extensions.spliterator(), false)
                .map(e -> (Map.Entry<Configurable, AnalysisContext>) e)
                .forEach(e -> e.getKey().initialize(e.getValue()));

        TIMING_LOG.debug("Initialization complete.");

        matchingTransformsCache.clear();

        Exception error = null;
        try {
            for (ApiAnalyzer a : extensions.getAnalyzers().keySet()) {
                analyzeWith(a, analysisContext.getOldApi(), analysisContext.getNewApi(), extensions);
            }
        } catch (Exception t) {
            error = t;
        }

        return new AnalysisResult(error, extensions);
    }

    private <T extends Configurable> Map<T, AnalysisContext>
    splitByConfiguration(AnalysisContext fullConfig, Set<Class<? extends T>> configurables) {
        Map<T, AnalysisContext> map = new HashMap<>();
        for (Class<? extends T> cc : configurables) {
            T c = instantiate(cc);
            String extensionId = c.getExtensionId();
            if (extensionId == null) {
                map.put(c, fullConfig.copyWithConfiguration(new ModelNode()));
            } else {
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

                    map.put(inst, fullConfig.copyWithConfiguration(config.get("configuration").clone()));

                    configured = true;
                }

                if (!configured) {
                    map.put(c, fullConfig.copyWithConfiguration(new ModelNode()));
                }
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

    private void analyzeWith(ApiAnalyzer apiAnalyzer, API oldApi, API newApi, AnalysisResult.Extensions extensions)
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
            analyze(apiAnalyzer.getCorrespondenceDeducer(), elementDifferenceAnalyzer, as, bs, extensions);
            TIMING_LOG.debug("Closing difference analyzer");
        }
        TIMING_LOG.debug("Difference analyzer closed");
    }

    private void analyze(CorrespondenceComparatorDeducer deducer, DifferenceAnalyzer elementDifferenceAnalyzer,
                         SortedSet<? extends Element> as, SortedSet<? extends Element> bs,
                         AnalysisResult.Extensions extensions) {

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

            Stats.of("filters").start();
            Set<ElementFilter> filters = extensions.getFilters().keySet();
            boolean analyzeThis =
                    (a == null || filtersApply(a, filters)) && (b == null || filtersApply(b, filters));
            Stats.of("filters").end(a, b);

            long beginDuration = 0;
            if (analyzeThis) {
                Stats.of("analyses").start();
                Stats.of("analysisBegins").start();
                elementDifferenceAnalyzer.beginAnalysis(a, b);
                Stats.of("analysisBegins").end(a, b);
                beginDuration = Stats.of("analyses").reset();
            }

            Stats.of("descends").start();
            boolean shouldDescend = a != null && b != null && filtersDescend(a, filters) &&
                    filtersDescend(b, filters);
            Stats.of("descends").end(a, b);

            if (shouldDescend) {
                analyze(deducer, elementDifferenceAnalyzer, a.getChildren(), b.getChildren(), extensions);
            }

            if (analyzeThis) {
                Stats.of("analyses").start();
                Stats.of("analysisEnds").start();
                Report r = elementDifferenceAnalyzer.endAnalysis(a, b);
                Stats.of("analysisEnds").end(a, b);
                Stats.of("analyses").end(beginDuration, new AbstractMap.SimpleEntry<>(a, b));
                transformAndReport(r, extensions);
            }
        }
    }

    private <T> T instantiate(Class<? extends T> type) {
        try {
            return type.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
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

    @SuppressWarnings("unchecked")
    private static <T> Stream<T> concat(Stream<? extends T> head, Stream<? extends T>[] all, int from) {
        if (from == all.length - 1) {
            return Stream.concat(head, all[from]);
        } else {
            return Stream.concat(head, concat(all[from], all, from + 1));
        }
    }

    private void transformAndReport(Report report, AnalysisResult.Extensions extensions) {
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
                boolean shouldBeRemoved = false;
                boolean differenceChanged = false;
                for (DifferenceTransform<?> t : getTransformsForDifference(d, extensions)) {
                    // it is the responsibility of the transform to declare the proper type.
                    // it will get a ClassCastException if it fails to declare a type that is common to all differences
                    // it can handle
                    @SuppressWarnings("unchecked")
                    DifferenceTransform<Element> tt = (DifferenceTransform<Element>) t;

                    Difference td = d;
                    try {
                        td = tt.transform(report.getOldElement(), report.getNewElement(), d);
                    } catch (Exception e) {
                        LOG.warn("Difference transform " + t + " of class '" + t.getClass() + " threw an exception" +
                                " while processing difference " + d + " on old element " + report.getOldElement() +
                                " and" +
                                " new element " + report.getNewElement(), e);
                    }

                    // ignore if transformation returned null, meaning that it "swallowed" the difference..
                    if (td == null) {
                        shouldBeRemoved = true;
                        listChanged = true;
                        differenceChanged = true;
                    } else if (!d.equals(td)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Difference transform {} transforms {} to {}", t.getClass(), d, td);
                        }

                        transformed.add(td);
                        listChanged = true;
                        differenceChanged = true;
                    }
                }

                if (differenceChanged) {
                    //we need to remove the element in either case
                    it.remove();
                    if (!shouldBeRemoved) {
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
            }

            iteration++;

            if (iteration % 100 == 0) {
                LOG.warn("Transformation of differences in match report " + report + " has cycled " + iteration +
                        " times. Maybe we're in an infinite loop with differences transforming back and forth?");
            }

            if (iteration == Integer.MAX_VALUE) {
                throw new IllegalStateException("Transformation failed to settle in " + Integer.MAX_VALUE +
                        " iterations. This is most probably an error in difference transform configuration that" +
                        " cycles between two or more changes back and forth.");
            }
        } while (listChanged);

        Stats.of("transforms").end(report);

        if (!report.getDifferences().isEmpty()) {
            Stats.of("reports").start();
            for (Reporter reporter : extensions.getReporters().keySet()) {
                reporter.report(report);
            }
            Stats.of("reports").end(report);
        }
    }

    private List<DifferenceTransform<?>> getTransformsForDifference(Difference diff, AnalysisResult.Extensions extensions) {
        List<DifferenceTransform<?>> ret = matchingTransformsCache.get(diff.code);
        if (ret == null) {
            ret = new ArrayList<>();
            for (DifferenceTransform<?> t : extensions.getTransforms().keySet()) {
                for (Pattern p : t.getDifferenceCodePatterns()) {
                    if (p.matcher(diff.code).matches()) {
                        ret.add(t);
                        break;
                    }
                }
            }
            matchingTransformsCache.put(diff.code, ret);
        }

        return ret;
    }

    public static final class Builder {
        private Set<Class<? extends ApiAnalyzer>> analyzers = null;
        private Set<Class<? extends Reporter>> reporters = null;
        private Set<Class<? extends DifferenceTransform<?>>> transforms = null;
        private Set<Class<? extends ElementFilter>> filters = null;

        @Nonnull
        public Builder withAnalyzersFromThreadContextClassLoader() {
            return withAnalyzers(ServiceTypeLoader.load(ApiAnalyzer.class));
        }

        @Nonnull
        public Builder withAnalyzersFrom(@Nonnull ClassLoader cl) {
            return withAnalyzers(ServiceTypeLoader.load(ApiAnalyzer.class, cl));
        }

        @SafeVarargs
        @Nonnull
        public final Builder withAnalyzers(Class<? extends ApiAnalyzer>... analyzers) {
            return withAnalyzers(Arrays.asList(analyzers));
        }

        @Nonnull
        public Builder withAnalyzers(@Nonnull Iterable<Class<? extends ApiAnalyzer>> analyzers) {
            if (this.analyzers == null) {
                this.analyzers = new HashSet<>();
            }
            for (Class<? extends ApiAnalyzer> a : analyzers) {
                this.analyzers.add(a);
            }

            return this;
        }

        @Nonnull
        public Builder withReportersFromThreadContextClassLoader() {
            return withReporters(ServiceTypeLoader.load(Reporter.class));
        }

        @Nonnull
        public Builder withReportersFrom(@Nonnull ClassLoader cl) {
            return withReporters(ServiceTypeLoader.load(Reporter.class, cl));
        }

        @SafeVarargs
        @Nonnull
        public final Builder withReporters(Class<? extends Reporter>... reporters) {
            return withReporters(Arrays.asList(reporters));
        }

        @Nonnull
        public Builder withReporters(@Nonnull Iterable<Class<? extends Reporter>> reporters) {
            if (this.reporters == null) {
                this.reporters = new HashSet<>();
            }
            for (Class<? extends Reporter> r : reporters) {
                this.reporters.add(r);
            }

            return this;
        }

        @Nonnull
        public Builder withTransformsFromThreadContextClassLoader() {
            //don't you love Java generics? ;)
            @SuppressWarnings("rawtypes")
            Iterable trs = ServiceTypeLoader.load(DifferenceTransform.class);

            @SuppressWarnings("unchecked")
            Iterable<Class<? extends DifferenceTransform<?>>> rtrs
                    = (Iterable<Class<? extends DifferenceTransform<?>>>) trs;

            return withTransforms(rtrs);
        }

        @Nonnull
        public Builder withTransformsFrom(@Nonnull ClassLoader cl) {
            //don't you love Java generics? ;)
            @SuppressWarnings("rawtypes")
            Iterable trs = ServiceTypeLoader.load(DifferenceTransform.class, cl);

            @SuppressWarnings("unchecked")
            Iterable<Class<? extends DifferenceTransform<?>>> rtrs
                    = (Iterable<Class<? extends DifferenceTransform<?>>>) trs;

            return withTransforms(rtrs);
        }

        @SafeVarargs
        @Nonnull
        public final Builder withTransforms(Class<? extends DifferenceTransform<?>>... transforms) {
            return withTransforms(Arrays.asList(transforms));
        }

        @Nonnull
        public Builder withTransforms(@Nonnull Iterable<Class<? extends DifferenceTransform<?>>> transforms) {
            if (this.transforms == null) {
                this.transforms = new HashSet<>();
            }
            for (Class<? extends DifferenceTransform<?>> t : transforms) {
                this.transforms.add(t);
            }

            return this;
        }

        @Nonnull
        public Builder withFiltersFromThreadContextClassLoader() {
            return withFilters(ServiceTypeLoader.load(ElementFilter.class));
        }

        @Nonnull
        public Builder withFiltersFrom(@Nonnull ClassLoader cl) {
            return withFilters(ServiceTypeLoader.load(ElementFilter.class, cl));
        }

        @SafeVarargs
        @Nonnull
        public final Builder withFilters(Class<? extends ElementFilter>... filters) {
            return withFilters(Arrays.asList(filters));
        }

        @Nonnull
        public Builder withFilters(@Nonnull Iterable<Class<? extends ElementFilter>> filters) {
            if (this.filters == null) {
                this.filters = new HashSet<>();
            }
            for (Class<? extends ElementFilter> f : filters) {
                this.filters.add(f);
            }

            return this;
        }

        @Nonnull
        public Builder withAllExtensionsFromThreadContextClassLoader() {
            return withAllExtensionsFrom(Thread.currentThread().getContextClassLoader());
        }

        @Nonnull
        public Builder withAllExtensionsFrom(@Nonnull ClassLoader cl) {
            return withAnalyzersFrom(cl).withFiltersFrom(cl).withReportersFrom(cl)
                    .withTransformsFrom(cl);
        }

        /**
         * @throws IllegalStateException if there are no api analyzers or no reporters added.
         * @return a new Revapi instance
         */
        @Nonnull
        public Revapi build() throws IllegalStateException {
            analyzers = analyzers == null ? Collections.emptySet() : analyzers;
            reporters = reporters == null ? Collections.emptySet() : reporters;
            transforms = transforms == null ? Collections.emptySet() : transforms;
            filters = filters == null ? Collections.emptySet() : filters;

            if (analyzers.isEmpty()) {
                throw new IllegalStateException(
                        "No API analyzers defined. The analysis cannot run without an analyzer.");
            }

            if (reporters.isEmpty()) {
                throw new IllegalStateException(
                        "No reporters defined. There is no way how to obtain the results of the analysis without" +
                                " a reporter.");
            }

            return new Revapi(analyzers, reporters, transforms, filters);
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

}
