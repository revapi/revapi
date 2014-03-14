/*
 * Copyright 2014 Lukas Krejci
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.query.CompoundFilter;
import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Revapi {
    private static final Logger LOG = LoggerFactory.getLogger(Revapi.class);

    public static final class Builder {
        private Set<ApiAnalyzer> analyzers = null;
        private Set<Reporter> reporters = null;
        private Set<DifferenceTransform> transforms = null;
        private Set<ElementFilter> filters = null;
        private Locale locale = Locale.getDefault();
        private Map<String, String> configuration = Collections.emptyMap();

        @Nonnull
        public Builder withAnalyzersFromThreadContextClassLoader() {
            return withAnalyzers(ServiceLoader.load(ApiAnalyzer.class));
        }

        @Nonnull
        public Builder withAnalyzersFrom(@Nonnull ClassLoader cl) {
            return withAnalyzers(ServiceLoader.load(ApiAnalyzer.class, cl));
        }

        @Nonnull
        public Builder withAnalyzers(ApiAnalyzer... analyzers) {
            return withAnalyzers(Arrays.asList(analyzers));
        }

        @Nonnull
        public Builder withAnalyzers(@Nonnull Iterable<? extends ApiAnalyzer> analyzers) {
            if (this.analyzers == null) {
                this.analyzers = new HashSet<>();
            }
            for (ApiAnalyzer a : analyzers) {
                this.analyzers.add(a);
            }

            return this;
        }

        @Nonnull
        public Builder withReportersFromThreadContextClassLoader() {
            return withReporters(ServiceLoader.load(Reporter.class));
        }

        @Nonnull
        public Builder withReportersFrom(@Nonnull ClassLoader cl) {
            return withReporters(ServiceLoader.load(Reporter.class, cl));
        }

        @Nonnull
        public Builder withReporters(Reporter... reporters) {
            return withReporters(Arrays.asList(reporters));
        }

        @Nonnull
        public Builder withReporters(@Nonnull Iterable<? extends Reporter> reporters) {
            if (this.reporters == null) {
                this.reporters = new HashSet<>();
            }
            for (Reporter r : reporters) {
                this.reporters.add(r);
            }

            return this;
        }

        @Nonnull
        public Builder withTransformsFromThreadContextClassLoader() {
            return withTransforms(ServiceLoader.load(DifferenceTransform.class));
        }

        @Nonnull
        public Builder withTransformsFrom(@Nonnull ClassLoader cl) {
            return withTransforms(ServiceLoader.load(DifferenceTransform.class, cl));
        }

        @Nonnull
        public Builder withTransforms(DifferenceTransform... transforms) {
            return withTransforms(Arrays.asList(transforms));
        }

        @Nonnull
        public Builder withTransforms(@Nonnull Iterable<? extends DifferenceTransform> transforms) {
            if (this.transforms == null) {
                this.transforms = new HashSet<>();
            }
            for (DifferenceTransform t : transforms) {
                this.transforms.add(t);
            }

            return this;
        }

        @Nonnull
        public Builder withFiltersFromThreadContextClassLoader() {
            return withFilters(ServiceLoader.load(ElementFilter.class));
        }

        @Nonnull
        public Builder withFiltersFrom(@Nonnull ClassLoader cl) {
            return withFilters(ServiceLoader.load(ElementFilter.class, cl));
        }

        @Nonnull
        public Builder withFilters(ElementFilter... filters) {
            return withFilters(Arrays.asList(filters));
        }

        @Nonnull
        public Builder withFilters(@Nonnull Iterable<? extends ElementFilter> filters) {
            if (this.filters == null) {
                this.filters = new HashSet<>();
            }
            for (ElementFilter f : filters) {
                this.filters.add(f);
            }

            return this;
        }

        @Nonnull
        public Builder withDefaultLocale() {
            this.locale = Locale.getDefault();
            return this;
        }

        @Nonnull
        public Builder withLocale(@Nonnull Locale locale) {
            this.locale = locale;
            return this;
        }

        @Nonnull
        public Builder withConfiguration(@Nonnull Map<String, String> configuration) {
            this.configuration = configuration;
            return this;
        }

        @Nonnull
        @SuppressWarnings({"unchecked", "rawtypes"})
        public Builder withConfiguration(@Nonnull Properties properties) {
            this.configuration = (Map<String, String>) (Map) properties;
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

        @Nonnull
        public Revapi build() {
            analyzers = analyzers == null ? Collections.<ApiAnalyzer>emptySet() : analyzers;
            reporters = reporters == null ? Collections.<Reporter>emptySet() : reporters;
            transforms = transforms == null ? Collections.<DifferenceTransform>emptySet() : transforms;
            filters = filters == null ? Collections.<ElementFilter>emptySet() : filters;

            return new Revapi(analyzers, reporters, transforms, filters, locale, configuration);
        }
    }

    private final Set<ApiAnalyzer> availableApiAnalyzers;
    private final Set<Reporter> availableReporters;
    private final Set<DifferenceTransform> availableProblemTransforms;
    private final CompoundFilter<Element> availableFilters;
    private final Map<String, String> configurationProperties;
    private final Locale locale;
    private Configuration configuration;

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Use the {@link #builder()} instead.
     *
     * @throws java.lang.IllegalArgumentException if any of the parameters is null
     */
    public Revapi(@Nonnull Set<ApiAnalyzer> availableApiAnalyzers, @Nonnull Set<Reporter> availableReporters,
        @Nonnull Set<DifferenceTransform> availableProblemTransforms, @Nonnull Set<ElementFilter> elementFilters,
        @Nonnull Locale locale, @Nonnull Map<String, String> configurationProperties) {

        this.availableApiAnalyzers = availableApiAnalyzers;
        this.availableReporters = availableReporters;
        this.availableProblemTransforms = availableProblemTransforms;
        this.availableFilters = new CompoundFilter<>(elementFilters);
        this.configurationProperties = configurationProperties;
        this.locale = locale;
    }

    public void analyze(@Nonnull Iterable<? extends Archive> oldArchives,
        @Nullable Iterable<? extends Archive> oldSupplementaryArchives,
        @Nonnull Iterable<? extends Archive> newArchives,
        @Nullable Iterable<? extends Archive> newSupplementaryArchives)
        throws Exception {

        this.configuration = new Configuration(locale, configurationProperties,
            new API(oldArchives, oldSupplementaryArchives),
            new API(newArchives, newSupplementaryArchives));

        initReporters();
        initAnalyzers();
        initProblemTransforms();

        try {
            for (ApiAnalyzer analyzer : availableApiAnalyzers) {
                analyzeWith(analyzer, configuration.getOldApi(), configuration.getNewApi());
            }
        } finally {
            closeAll(availableProblemTransforms, "problem transform");
            closeElementFilters();
            closeAll(availableApiAnalyzers, "api analyzer");
            closeAll(availableReporters, "reporter");
        }
    }

    private void initReporters() {
        for (Reporter r : availableReporters) {
            r.initialize(configuration);
        }
    }

    private void initAnalyzers() {
        for (ApiAnalyzer a : availableApiAnalyzers) {
            a.initialize(configuration);
        }
    }

    private void initProblemTransforms() {
        for (DifferenceTransform f : availableProblemTransforms) {
            f.initialize(configuration);
        }
    }

    private void closeAll(Iterable<? extends AutoCloseable> closeables, String type) {
        for (AutoCloseable c : closeables) {
            try {
                c.close();
            } catch (Exception e) {
                LOG.warn("Failed to close " + type + " " + c, e);
            }
        }
    }

    private void closeElementFilters() {
        for (Filter<? super Element> f : availableFilters.getWrappedFilters()) {
            if (f instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) f).close();
                } catch (Exception e) {
                    LOG.warn("Failed to close element filter " + f, e);
                }
            }
        }
    }

    private void analyzeWith(ApiAnalyzer apiAnalyzer, API oldApi, API newApi)
        throws Exception {

        ArchiveAnalyzer oldAnalyzer = apiAnalyzer.getArchiveAnalyzer(oldApi);
        ArchiveAnalyzer newAnalyzer = apiAnalyzer.getArchiveAnalyzer(newApi);

        ElementForest oldTree = oldAnalyzer.analyze();
        ElementForest newTree = newAnalyzer.analyze();

        DifferenceAnalyzer elementDifferenceAnalyzer = apiAnalyzer.getDifferenceAnalyzer(oldAnalyzer, newAnalyzer);

        SortedSet<? extends Element> as = oldTree.getRoots();
        SortedSet<? extends Element> bs = newTree.getRoots();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Old tree: {}", oldTree);
            LOG.trace("New tree: {}", newTree);
        }

        elementDifferenceAnalyzer.open();
        analyze(elementDifferenceAnalyzer, as, bs);
        elementDifferenceAnalyzer.close();
    }

    private void analyze(DifferenceAnalyzer elementDifferenceAnalyzer,
        SortedSet<? extends Element> as, SortedSet<? extends Element> bs) {

        CoIterator<Element> it = new CoIterator<>(as.iterator(), bs.iterator());

        while (it.hasNext()) {
            it.next();

            Element a = it.getLeft();
            Element b = it.getRight();

            boolean analyzeThis =
                (a == null || availableFilters.applies(a)) && (b == null || availableFilters.applies(b));

            if (analyzeThis) {
                elementDifferenceAnalyzer.beginAnalysis(a, b);
            }

            if (a != null && b != null && availableFilters.shouldDescendInto(a) &&
                availableFilters.shouldDescendInto(b)) {

                analyze(elementDifferenceAnalyzer, a.getChildren(), b.getChildren());
            }

            if (analyzeThis) {
                transformAndReport(elementDifferenceAnalyzer.endAnalysis(a, b));
            }
        }
    }

    private void transformAndReport(Report report) {
        if (report == null) {
            return;
        }

        int iteration = 0;
        boolean changed;
        do {
            changed = false;

            for (DifferenceTransform t : availableProblemTransforms) {
                ListIterator<Difference> it = report.getDifferences().listIterator();
                while (it.hasNext()) {
                    Difference p = it.next();
                    Difference tp = t.transform(report.getOldElement(), report.getNewElement(), p);
                    // ignore if transformation returned null, meaning that it "swallowed" the problem..
                    // once the changes are done, we'll loop through once more and remove the problems that the
                    // transforms want removed.
                    // This prevents 1 transformation from disallowing other transformation to do what it needs
                    // if both apply to the same problem.
                    if (tp != null && tp != p) { //yes, reference equality is OK here
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Problem transform {} transforms {} to  {}", t.getClass(), p, tp);
                        }
                        it.set(tp);
                        changed = true;
                    }
                }
            }

            iteration++;

            if (iteration % 100 == 0) {
                LOG.warn("Transformation of problems in match report " + report + " has cycled " + iteration +
                    " times. Maybe we're in an infinite loop with problems transforming back and forth?");
            }
        } while (changed);

        //now remove the problems that the transforms want removed
        for (DifferenceTransform t : availableProblemTransforms) {
            ListIterator<Difference> it = report.getDifferences().listIterator();
            while (it.hasNext()) {
                Difference p = it.next();
                Difference tp = t.transform(report.getOldElement(), report.getNewElement(), p);
                if (tp == null) {
                    it.remove();
                }
            }
        }

        if (!report.getDifferences().isEmpty()) {
            for (Reporter reporter : availableReporters) {
                reporter.report(report);
            }
        }
    }
}
