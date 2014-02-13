/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi;

import java.io.File;
import java.io.IOException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.query.CompoundFilter;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Revapi {
    private static final Logger LOG = LoggerFactory.getLogger(Revapi.class);

    public static final class Builder {
        private Set<ApiAnalyzer> analyzers = Collections.emptySet();
        private Set<Reporter> reporters = Collections.emptySet();
        private Set<ProblemTransform> transforms = Collections.emptySet();
        private Set<ElementFilter> filters = Collections.emptySet();
        private Locale locale = Locale.getDefault();
        private Map<String, String> configuration = Collections.emptyMap();

        public Builder withAnalyzersOnClassPath() {
            return withAnalyzers(ServiceLoader.load(ApiAnalyzer.class));
        }

        public Builder withAnalyzersOnClassPath(ClassLoader cl) {
            return withAnalyzers(ServiceLoader.load(ApiAnalyzer.class, cl));
        }

        public Builder withAnalyzers(ApiAnalyzer... analyzers) {
            return withAnalyzers(Arrays.asList(analyzers));
        }

        public Builder withAnalyzers(Iterable<? extends ApiAnalyzer> analyzers) {
            this.analyzers = new HashSet<>();
            for (ApiAnalyzer a : analyzers) {
                this.analyzers.add(a);
            }

            return this;
        }

        public Builder withReportersOnClassPath() {
            return withReporters(ServiceLoader.load(Reporter.class));
        }

        public Builder withReportersOnClassPath(ClassLoader cl) {
            return withReporters(ServiceLoader.load(Reporter.class, cl));
        }

        public Builder withReporters(Reporter... reporters) {
            return withReporters(Arrays.asList(reporters));
        }

        public Builder withReporters(Iterable<? extends Reporter> reporters) {
            this.reporters = new HashSet<>();
            for (Reporter r : reporters) {
                this.reporters.add(r);
            }

            return this;
        }

        public Builder withTransformsOnClassPath() {
            return withTransforms(ServiceLoader.load(ProblemTransform.class));
        }

        public Builder withTransformsOnClassPath(ClassLoader cl) {
            return withTransforms(ServiceLoader.load(ProblemTransform.class, cl));
        }

        public Builder withTransforms(ProblemTransform... transforms) {
            return withTransforms(Arrays.asList(transforms));
        }

        public Builder withTransforms(Iterable<? extends ProblemTransform> transforms) {
            this.transforms = new HashSet<>();
            for (ProblemTransform t : transforms) {
                this.transforms.add(t);
            }

            return this;
        }

        public Builder withFiltersOnClassPath() {
            return withFilters(ServiceLoader.load(ElementFilter.class));
        }

        public Builder withFiltersOnClassPath(ClassLoader cl) {
            return withFilters(ServiceLoader.load(ElementFilter.class, cl));
        }

        public Builder withFilters(ElementFilter... filters) {
            return withFilters(Arrays.asList(filters));
        }

        public Builder withFilters(Iterable<? extends ElementFilter> filters) {
            this.filters = new HashSet<>();
            for (ElementFilter f : filters) {
                this.filters.add(f);
            }

            return this;
        }

        public Builder withDefaultLocale() {
            this.locale = Locale.getDefault();
            return this;
        }

        public Builder withLocale(Locale locale) {
            this.locale = locale;
            return this;
        }

        public Builder withConfiguration(Map<String, String> configuration) {
            this.configuration = configuration;
            return this;
        }

        @SuppressWarnings("unchecked")
        public Builder withConfiguration(Properties properties) {
            this.configuration = (Map<String, String>) (Map) properties;
            return this;
        }

        public Builder withAllExtensionsOnClassPath() {
            return withAllExtensionsOnClassPath(Thread.currentThread().getContextClassLoader());
        }

        public Builder withAllExtensionsOnClassPath(ClassLoader cl) {
            return withAnalyzersOnClassPath(cl).withFiltersOnClassPath(cl).withReportersOnClassPath(cl)
                .withTransformsOnClassPath(cl);
        }

        public Revapi build() {
            return new Revapi(analyzers, reporters, transforms, filters, locale, configuration);
        }
    }

    private final Set<ApiAnalyzer> availableApiAnalyzers;
    private final Set<Reporter> availableReporters;
    private final Set<ProblemTransform> availableProblemTransforms;
    private final CompoundFilter<Element> availableFilters;
    private final Map<String, String> configurationProperties;
    private final Locale locale;
    private Configuration configuration;

    private static void usage() {
        System.out.println("Revapi <oldArchive> <newArchive>");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static void main(String[] args) throws Exception {
        //TODO beef this up
        if (args == null || args.length != 2) {
            usage();
            return;
        }

        String oldArchiveName = args[0];
        String newArchiveName = args[1];

        @SuppressWarnings("unchecked")
        Revapi revapi = Revapi.builder().withAllExtensionsOnClassPath().withConfiguration(System.getProperties())
            .build();

        revapi.analyze(
            Arrays.<Archive>asList(new FileArchive(new File(oldArchiveName))), null,
            Arrays.<Archive>asList(new FileArchive(new File(newArchiveName))), null);
    }

    /**
     * Use the {@link #builder()} instead.
     *
     * @throws java.lang.IllegalArgumentException if any of the parameters is null
     */
    public Revapi(Set<ApiAnalyzer> availableApiAnalyzers, Set<Reporter> availableReporters,
        Set<ProblemTransform> availableProblemTransforms, Set<ElementFilter> elementFilters, Locale locale,
        Map<String, String> configurationProperties) {

        if (availableApiAnalyzers == null) {
            throw new IllegalArgumentException("availableApiAnanlyzers");
        }

        if (availableReporters == null) {
            throw new IllegalArgumentException("availableReporters");
        }

        if (availableProblemTransforms == null) {
            throw new IllegalArgumentException("availableProblemTransforms");
        }

        if (elementFilters == null) {
            throw new IllegalArgumentException("elementFilters");
        }

        if (locale == null) {
            throw new IllegalArgumentException("locale");
        }

        if (configurationProperties == null) {
            throw new IllegalArgumentException("configurationProperties");
        }

        this.availableApiAnalyzers = availableApiAnalyzers;
        this.availableReporters = availableReporters;
        this.availableProblemTransforms = availableProblemTransforms;
        this.availableFilters = new CompoundFilter<>(elementFilters);
        this.configurationProperties = configurationProperties;
        this.locale = locale;
    }

    public void analyze(Iterable<? extends Archive> oldArchives, Iterable<? extends Archive> oldSupplementaryArchives,
        Iterable<? extends Archive> newArchives, Iterable<? extends Archive> newSupplementaryArchives)
        throws IOException {

        this.configuration = new Configuration(locale, configurationProperties,
            new API(oldArchives, oldSupplementaryArchives),
            new API(newArchives, newSupplementaryArchives));

        initReporters();
        initAnalyzers();
        initProblemTransforms();

        for (ApiAnalyzer analyzer : availableApiAnalyzers) {
            analyzeWith(analyzer, configuration.getOldApi(), configuration.getNewApi());
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
        for (ProblemTransform f : availableProblemTransforms) {
            f.initialize(configuration);
        }
    }

    private void analyzeWith(ApiAnalyzer apiAnalyzer, API oldApi, API newApi)
    throws IOException {
        ArchiveAnalyzer oldAnalyzer = apiAnalyzer.getArchiveAnalyzer(oldApi);
        ArchiveAnalyzer newAnalyzer = apiAnalyzer.getArchiveAnalyzer(newApi);

        Tree oldTree = oldAnalyzer.analyze();
        Tree newTree = newAnalyzer.analyze();

        ElementDifferenceAnalyzer elementDifferenceAnalyzer = apiAnalyzer.getElementAnalyzer(oldAnalyzer, newAnalyzer);

        SortedSet<? extends Element> as = oldTree.getRoots();
        SortedSet<? extends Element> bs = newTree.getRoots();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Old tree: {}", oldTree);
            LOG.trace("New tree: {}", newTree);
        }

        elementDifferenceAnalyzer.setup();
        analyze(elementDifferenceAnalyzer, as, bs);
        elementDifferenceAnalyzer.tearDown();
    }

    private void analyze(ElementDifferenceAnalyzer elementDifferenceAnalyzer,
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

    private void transformAndReport(MatchReport matchReport) {
        if (matchReport == null) {
            return;
        }

        int iteration = 0;
        boolean changed;
        do {
            changed = false;

            for (ProblemTransform t : availableProblemTransforms) {
                ListIterator<MatchReport.Problem> it = matchReport.getProblems().listIterator();
                while (it.hasNext()) {
                    MatchReport.Problem p = it.next();
                    MatchReport.Problem tp = t.transform(matchReport.getOldElement(), matchReport.getNewElement(), p);
                    if (tp == null) {
                        //ignore it now.. we detecting changes transforms want to do... once the changes are done,
                        //we'll loop through once more and remove the problems that the transforms want removed
                    } else if (tp != p) { //yes, reference equality is OK here
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
                LOG.warn("Transformation of problems in match report " + matchReport + " has cycled " + iteration +
                    " times. Maybe we're in an infinite loop with problems transforming back and forth?");
            }
        } while (changed);

        //now remove the problems that the transforms want removed
        for (ProblemTransform t : availableProblemTransforms) {
            ListIterator<MatchReport.Problem> it = matchReport.getProblems().listIterator();
            while (it.hasNext()) {
                MatchReport.Problem p = it.next();
                MatchReport.Problem tp = t.transform(matchReport.getOldElement(), matchReport.getNewElement(), p);
                if (tp == null) {
                    it.remove();
                }
            }
        }

        if (!matchReport.getProblems().isEmpty()) {
            for (Reporter reporter : availableReporters) {
                reporter.report(matchReport);
            }
        }
    }
}
