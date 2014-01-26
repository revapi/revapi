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
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Revapi {
    private final Set<ApiAnalyzer> availableApiAnalyzers;
    private final Set<Reporter> availableReporters;
    private final Set<ProblemTransform> availableProblemTransforms;
    private final Configuration configuration;

    private static void usage() {
        System.out.println("Revapi <oldArchive> <newArchive>");
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
        Revapi revapi = new Revapi(Locale.getDefault(),
            (Map<String, String>) (Map<?, ?>) System.getProperties());

        revapi.analyze(
            Arrays.<Archive>asList(new FileArchive(new File(oldArchiveName))), null,
            Arrays.<Archive>asList(new FileArchive(new File(newArchiveName))), null);
    }

    public Revapi(Locale locale, Map<String, String> configurationProperties) {
        this(Thread.currentThread().getContextClassLoader(), locale,
            configurationProperties);
    }

    @SuppressWarnings("unchecked")
    public Revapi(ClassLoader classLoader, Locale locale,
        Map<String, String> configurationProperties) {

        this(loadServices(classLoader, ApiAnalyzer.class), loadServices(classLoader, Reporter.class),
            loadServices(classLoader, ProblemTransform.class), locale, configurationProperties);
    }

    public Revapi(Set<ApiAnalyzer> availableApiAnalyzers, Set<Reporter> availableReporters,
        Set<ProblemTransform> availableProblemTransforms, Locale locale,
        Map<String, String> configurationProperties) {

        this.availableApiAnalyzers = availableApiAnalyzers;
        this.availableReporters = availableReporters;
        this.availableProblemTransforms = availableProblemTransforms;
        this.configuration = new Configuration(locale, configurationProperties);
    }

    private static <T> Set<T> loadServices(ClassLoader classLoader, Class<T> serviceClass) {
        Set<T> services = new HashSet<>();
        for (T service : ServiceLoader.load(serviceClass, classLoader)) {
            services.add(service);
        }

        return services;
    }

    public void analyze(Iterable<? extends Archive> oldArchives, Iterable<? extends Archive> oldSupplementaryArchives,
        Iterable<? extends Archive> newArchives, Iterable<? extends Archive> newSupplementaryArchives)
        throws IOException {
        initReporters();
        initAnalyzers();
        initProblemFilters();

        for (ApiAnalyzer analyzer : availableApiAnalyzers) {
            analyzeWith(analyzer, oldArchives, oldSupplementaryArchives, newArchives, newSupplementaryArchives);
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

    private void initProblemFilters() {
        for (ProblemTransform f : availableProblemTransforms) {
            f.initialize(configuration);
        }
    }

    private void analyzeWith(ApiAnalyzer apiAnalyzer, Iterable<? extends Archive> oldArchives,
        Iterable<? extends Archive> oldSupplementaryArchives, Iterable<? extends Archive> newArchives,
        Iterable<? extends Archive> newSupplementaryArchives)
    throws IOException {
        ArchiveAnalyzer oldAnalyzer = apiAnalyzer.getArchiveAnalyzer(oldArchives, oldSupplementaryArchives);
        ArchiveAnalyzer newAnalyzer = apiAnalyzer.getArchiveAnalyzer(newArchives, newSupplementaryArchives);

        Tree oldTree = oldAnalyzer.analyze();
        Tree newTree = newAnalyzer.analyze();

        ElementAnalyzer elementAnalyzer = apiAnalyzer.getElementAnalyzer(oldAnalyzer, newAnalyzer);

        SortedSet<? extends Element> as = oldTree.getRoots();
        SortedSet<? extends Element> bs = newTree.getRoots();

        elementAnalyzer.setup();
        analyze(elementAnalyzer, as, bs);
        elementAnalyzer.tearDown();
    }

    private void analyze(ElementAnalyzer elementAnalyzer,
        SortedSet<? extends Element> as, SortedSet<? extends Element> bs) {

        CoIterator<Element> it = new CoIterator<>(as.iterator(), bs.iterator());

        while (it.hasNext()) {
            it.next();

            Element a = it.getLeft();
            Element b = it.getRight();

            elementAnalyzer.beginAnalysis(a, b);

            if (a != null && b != null) {
                analyze(elementAnalyzer, a.getChildren(), b.getChildren());
            }

            report(elementAnalyzer.endAnalysis(a, b));
        }
    }

    private void report(MatchReport matchReport) {
        if (matchReport == null) {
            return;
        }

        for (ProblemTransform t : availableProblemTransforms) {
            ListIterator<MatchReport.Problem> it = matchReport.getProblems().listIterator();
            while (it.hasNext()) {
                MatchReport.Problem p = it.next();
                MatchReport.Problem tp = t.transform(matchReport.getOldElement(), matchReport.getNewElement(), p);
                if (tp == null) {
                    it.remove();
                } else if (tp != p) { //yes, reference equality is OK here
                    it.set(tp);
                }
            }
        }

        for (Reporter reporter : availableReporters) {
            reporter.report(matchReport);
        }
    }
}
