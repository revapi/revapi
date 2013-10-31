/*
 * Copyright 2013 Lukas Krejci
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

package org.revapi.core;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.SortedSet;

import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementAnalyzer;
import org.revapi.Language;
import org.revapi.MatchReport;
import org.revapi.Reporter;
import org.revapi.Tree;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class Revapi {
    private final Set<Language> availableLanguages;
    private final Set<Reporter> availableReporters;
    private final Map<String, String> configurationProperties;
    private final PrintStream output;
    private final Archive oldArchive;
    private final Archive newArchive;

    private static void usage() {
        System.out.println("Revapi <oldArchive> <newArchive>");
    }

    public static void main(String[] args) throws Exception {
        if (args == null || args.length != 2) {
            usage();
            return;
        }

        String oldArchiveName = args[0];
        String newArchiveName = args[1];

        @SuppressWarnings("unchecked") Revapi revapi = new Revapi(new FileArchive(new File(oldArchiveName)),
            new FileArchive(new File(newArchiveName)), System.out,
            (Map<String, String>) (Map<?, ?>) System.getProperties());

        revapi.analyze();
    }

    public Revapi(Archive oldArchive, Archive newArchive, PrintStream output,
        Map<String, String> configurationProperties) {
        this(oldArchive, newArchive, output, Thread.currentThread().getContextClassLoader(), configurationProperties);
    }

    @SuppressWarnings("unchecked")
    public Revapi(Archive oldArchive, Archive newArchive, PrintStream output, ClassLoader classLoader,
        Map<String, String> configurationProperties) {
        this.oldArchive = oldArchive;
        this.newArchive = newArchive;
        this.availableLanguages = Revapi.loadServices(classLoader, (Class<Language>) (Class<?>) Language.class);
        this.availableReporters = loadServices(classLoader, Reporter.class);
        this.output = output;
        this.configurationProperties = configurationProperties;
    }

    private static <T> Set<T> loadServices(ClassLoader classLoader, Class<T> serviceClass) {
        Set<T> services = new HashSet<>();
        for (T service : ServiceLoader.load(serviceClass, classLoader)) {
            services.add(service);
        }

        return services;
    }

    public void analyze() throws IOException {
        initReporters();
        for (Language lang : availableLanguages) {
            analyzeWith(lang);
        }
    }

    @SuppressWarnings("unchecked")
    private void initReporters() {
        for (Reporter r : availableReporters) {
            r.initialize(configurationProperties);
        }
    }


    private <T> void analyzeWith(Language lang) throws IOException {
        try (
            ArchiveAnalyzer oldAnalyzer = lang.getArchiveAnalyzer(oldArchive);
            ArchiveAnalyzer newAnalyzer = lang.getArchiveAnalyzer(newArchive);
        ) {
            Tree oldTree = oldAnalyzer.analyze();
            Tree newTree = newAnalyzer.analyze();

            ElementAnalyzer elementAnalyzer = lang.getElementAnalyzer();
            analyze(elementAnalyzer, oldTree.getRoots(), newTree.getRoots());
        }
    }

    private <T> void analyze(ElementAnalyzer elementAnalyzer,
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

        for (Reporter reporter : availableReporters) {
            reporter.report(matchReport, output);
        }
    }
}
