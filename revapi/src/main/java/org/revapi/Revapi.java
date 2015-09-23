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

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;
import org.revapi.configuration.ConfigurationException;
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

    public static final class Builder {
        private Set<ApiAnalyzer> analyzers = null;
        private Set<Reporter> reporters = null;
        private Set<DifferenceTransform<?>> transforms = null;
        private Set<ElementFilter> filters = null;

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
            //don't you love Java generics? ;)
            @SuppressWarnings("rawtypes")
            Iterable trs = ServiceLoader.load(DifferenceTransform.class);

            @SuppressWarnings("unchecked")
            Iterable<DifferenceTransform<?>> rtrs = (Iterable<DifferenceTransform<?>>) trs;

            return withTransforms(rtrs);
        }

        @Nonnull
        public Builder withTransformsFrom(@Nonnull ClassLoader cl) {
            //don't you love Java generics? ;)
            @SuppressWarnings("rawtypes")
            Iterable trs = ServiceLoader.load(DifferenceTransform.class, cl);

            @SuppressWarnings("unchecked")
            Iterable<DifferenceTransform<?>> rtrs = (Iterable<DifferenceTransform<?>>) trs;

            return withTransforms(rtrs);
        }

        @Nonnull
        public Builder withTransforms(DifferenceTransform<?>... transforms) {
            return withTransforms(Arrays.asList(transforms));
        }

        @Nonnull
        public Builder withTransforms(@Nonnull Iterable<? extends DifferenceTransform<?>> transforms) {
            if (this.transforms == null) {
                this.transforms = new HashSet<>();
            }
            for (DifferenceTransform<?> t : transforms) {
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
            transforms = transforms == null ? Collections.<DifferenceTransform<?>>emptySet() : transforms;
            filters = filters == null ? Collections.<ElementFilter>emptySet() : filters;

            return new Revapi(analyzers, reporters, transforms, filters);
        }
    }

    private static class CompoundFilter implements ElementFilter, Iterable<ElementFilter> {
        private final Collection<? extends ElementFilter> filters;
        private final String[] allConfigRoots;
        private final Map<String, ElementFilter> rootsToFilters;

        private CompoundFilter(Collection<? extends ElementFilter> filters) {
            this.filters = filters;

            rootsToFilters = new HashMap<>();
            List<String> tmp = new ArrayList<>();

            for (ElementFilter f : filters) {
                String[] roots = f.getConfigurationRootPaths();
                if (roots != null) {
                    for (String root : roots) {
                        tmp.add(root);
                        rootsToFilters.put(root, f);
                    }
                }
            }

            allConfigRoots = tmp.toArray(new String[tmp.size()]);
        }

        @Override
        public void close() throws Exception {
            Exception thrown = null;
            for (ElementFilter f : filters) {
                try {
                    f.close();
                } catch (Exception e) {
                    if (thrown == null) {
                        thrown = new Exception("Failed to close some element filters");
                    }

                    thrown.addSuppressed(e);
                }
            }

            if (thrown != null) {
                throw thrown;
            }
        }

        @Nullable
        @Override
        public String[] getConfigurationRootPaths() {
            return allConfigRoots;
        }

        @Nullable
        @Override
        public Reader getJSONSchema(@Nonnull String configurationRootPath) {
            ElementFilter f = rootsToFilters.get(configurationRootPath);

            return f == null ? null : f.getJSONSchema(configurationRootPath);
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
            for (ElementFilter f : filters) {
                f.initialize(analysisContext);
            }
        }

        @Override
        public boolean applies(@Nullable Element element) {
            for (ElementFilter f : filters) {
                if (!f.applies(element)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean shouldDescendInto(@Nullable Object element) {
            Iterator<? extends ElementFilter> it = filters.iterator();
            boolean hasNoFilters = !it.hasNext();

            while (it.hasNext()) {
                if (it.next().shouldDescendInto(element)) {
                    return true;
                }
            }

            return hasNoFilters;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<ElementFilter> iterator() {
            return (Iterator<ElementFilter>) filters.iterator();
        }
    }

    private final Set<ApiAnalyzer> availableApiAnalyzers;
    private final Set<Reporter> availableReporters;
    private final Set<DifferenceTransform<?>> availableTransforms;
    private final CompoundFilter availableFilters;
    private final ConfigurationValidator configurationValidator;

    private final Map<String, List<DifferenceTransform<?>>> matchingTransformsCache = new HashMap<>();

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Use the {@link #builder()} instead.
     *
     * @param availableApiAnalyzers the set of analyzers to use
     * @param availableReporters the set of reporters to use
     * @param availableTransforms the set of transforms to use
     * @param elementFilters the set of element filters to use
     *
     * @throws java.lang.IllegalArgumentException if any of the parameters is null
     */
    public Revapi(@Nonnull Set<ApiAnalyzer> availableApiAnalyzers, @Nonnull Set<Reporter> availableReporters,
        @Nonnull Set<DifferenceTransform<?>> availableTransforms, @Nonnull Set<ElementFilter> elementFilters) {

        this.availableApiAnalyzers = availableApiAnalyzers;
        this.availableReporters = availableReporters;
        this.availableTransforms = availableTransforms;
        this.availableFilters = new CompoundFilter(elementFilters);
        this.configurationValidator = new ConfigurationValidator();
    }

    public void analyze(@Nonnull AnalysisContext analysisContext) throws Exception {
        ValidationResult validation = ValidationResult.success();

        validation = initialize(analysisContext, validation, availableFilters);
        validation = initialize(analysisContext, validation, availableReporters);
        validation = initialize(analysisContext, validation, availableApiAnalyzers);
        validation = initialize(analysisContext, validation, availableTransforms);

        matchingTransformsCache.clear();

        if (!validation.isSuccessful()) {
            throw new ConfigurationException(validation.toString());
        }

        try {
            for (ApiAnalyzer analyzer : availableApiAnalyzers) {
                analyzeWith(analyzer, analysisContext.getOldApi(), analysisContext.getNewApi());
            }
        } finally {
            closeAll(availableTransforms, "problem transform");
            closeAll(availableFilters, "element filters");
            closeAll(availableApiAnalyzers, "api analyzer");
            closeAll(availableReporters, "reporter");
        }
    }

    private ValidationResult initialize(@Nonnull AnalysisContext analysisContext, ValidationResult validationResult,
        Iterable<? extends Configurable> configurables) {
        for (Configurable c : configurables) {
            ValidationResult partial = configurationValidator.validate(analysisContext.getConfiguration(), c);
            validationResult = validationResult.merge(partial);

            if (validationResult.isSuccessful()) {
                c.initialize(analysisContext);
            }
        }

        return validationResult;
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

    private void analyzeWith(ApiAnalyzer apiAnalyzer, API oldApi, API newApi)
        throws Exception {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting analysis using " + apiAnalyzer + " on:\nOld API:\n" + oldApi + "\n\nNew API:\n"
                + newApi);
        }

        ArchiveAnalyzer oldAnalyzer = apiAnalyzer.getArchiveAnalyzer(oldApi);
        ArchiveAnalyzer newAnalyzer = apiAnalyzer.getArchiveAnalyzer(newApi);

        ElementForest oldTree = oldAnalyzer.analyze();
        ElementForest newTree = newAnalyzer.analyze();

        DifferenceAnalyzer elementDifferenceAnalyzer = apiAnalyzer.getDifferenceAnalyzer(oldAnalyzer, newAnalyzer);

        SortedSet<? extends Element> as = oldTree.getRoots();
        SortedSet<? extends Element> bs = newTree.getRoots();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Old tree: {}", oldTree);
            LOG.debug("New tree: {}", newTree);
        }

        elementDifferenceAnalyzer.open();
        analyze(elementDifferenceAnalyzer, as, bs);
        elementDifferenceAnalyzer.close();
    }

    private void analyze(DifferenceAnalyzer elementDifferenceAnalyzer,
        SortedSet<? extends Element> as, SortedSet<? extends Element> bs) {

        CoIterator<Element> it = new CoIterator<>(as.iterator(), bs.iterator(),
            elementDifferenceAnalyzer.getCorrespondenceComparator());

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

            ListIterator<Difference> it = report.getDifferences().listIterator();
            while (it.hasNext()) {
                Difference d = it.next();
                for (DifferenceTransform<?> t : getTransformsForDifference(d)) {
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
                            " while processing difference " + d + " on old element " + report.getOldElement() + " and" +
                            " new element " + report.getNewElement(), e);
                    }

                    // ignore if transformation returned null, meaning that it "swallowed" the difference..
                    // once the changes are done, we'll loop through once more and remove the differences that the
                    // transforms want removed.
                    // This prevents 1 transformation from disallowing other transformation to do what it needs
                    // if both apply to the same difference.
                    if (td != null && !d.equals(td)) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Difference transform {} transforms {} to {}", t.getClass(), d, td);
                        }
                        it.set(td);
                        changed = true;
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
        } while (changed);

        //now remove the differences that the transforms want removed
        ListIterator<Difference> it = report.getDifferences().listIterator();
        while (it.hasNext()) {
            Difference d = it.next();
            for (DifferenceTransform<?> t : getTransformsForDifference(d)) {
                @SuppressWarnings("unchecked")
                DifferenceTransform<Element> tt = (DifferenceTransform<Element>) t;
                Difference td = tt.transform(report.getOldElement(), report.getNewElement(), d);
                if (td == null) {
                    it.remove();
                    break;
                }
            }
        }

        if (!report.getDifferences().isEmpty()) {
            for (Reporter reporter : availableReporters) {
                reporter.report(report);
            }
        }
    }

    private List<DifferenceTransform<?>> getTransformsForDifference(Difference diff) {
        List<DifferenceTransform<?>> ret = matchingTransformsCache.get(diff.code);
        if (ret == null) {
            ret = new ArrayList<>();
            for (DifferenceTransform<?> t : availableTransforms) {
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
}
