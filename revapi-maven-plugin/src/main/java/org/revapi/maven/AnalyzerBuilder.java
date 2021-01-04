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
package org.revapi.maven;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.revapi.ApiAnalyzer;
import org.revapi.DifferenceTransform;
import org.revapi.ElementFilter;
import org.revapi.ElementMatcher;
import org.revapi.PipelineConfiguration;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.ServiceTypeLoader;
import org.revapi.TreeFilterProvider;

/**
 * Common {@link Analyzer} instantiation logic for mojos.
 *
 * @author Lukas Krejci
 * @since 0.8.0
 */
class AnalyzerBuilder {

    private Log log;
    private Locale locale;
    private MavenProject project;
    private boolean skip;
    private String[] oldGavs;
    private String[] newGavs;
    private final Artifact[] oldArtifacts;
    private final Artifact[] newArtifacts;
    private String oldVersion;
    private String newVersion;
    private String disallowedExtensions;
    private Class<? extends Reporter> reporterType;
    private PipelineConfiguration.Builder pipelineConfiguration;
    private PlexusConfiguration analysisConfiguration;
    private Object[] analysisConfigurationFiles;
    private RepositorySystem repositorySystem;
    private RepositorySystemSession repositorySystemSession;
    private boolean failOnMissingConfigurationFiles;
    private boolean failOnUnresolvedArtifacts;
    private boolean failOnUnresolvedDependencies;
    private boolean alwaysCheckForReleaseVersion;
    private boolean checkDependencies;
    private boolean resolveProvidedDependencies;
    private boolean resolveTransitiveProvidedDependencies;
    private boolean expandProperties;
    private String versionFormat;
    private Revapi revapi;
    private Map<String, Object> contextData = new HashMap<>(2);

    static AnalyzerBuilder forGavs(String[] oldGavs, String[] newGavs) {
        return new AnalyzerBuilder(oldGavs, newGavs, null, null);
    }

    static AnalyzerBuilder forArtifacts(Artifact[] oldArtifacts, Artifact[] newArtifacts) {
        return new AnalyzerBuilder(null, null, oldArtifacts, newArtifacts);
    }

    private AnalyzerBuilder(String[] oldGavs, String[] newGavs, Artifact[] oldArtifacts, Artifact[] newArtifacts) {
        this.oldGavs = oldGavs;
        this.newGavs = newGavs;
        this.oldArtifacts = oldArtifacts;
        this.newArtifacts = newArtifacts;
    }

    AnalyzerBuilder withProject(MavenProject project) {
        this.project = project;
        return this;
    }

    AnalyzerBuilder withSkip(boolean skip) {
        this.skip = skip;
        return this;
    }

    AnalyzerBuilder withOldVersion(String oldVersion) {
        this.oldVersion = oldVersion;
        return this;
    }

    AnalyzerBuilder withNewVersion(String newVersion) {
        this.newVersion = newVersion;
        return this;
    }

    AnalyzerBuilder withDisallowedExtensions(String disallowedExtensions) {
        this.disallowedExtensions = disallowedExtensions;
        return this;
    }

    AnalyzerBuilder withReporter(Class<? extends Reporter> reporter) {
        this.reporterType = reporter;
        return this;
    }

    AnalyzerBuilder withLocale(Locale locale) {
        this.locale = locale;
        return this;
    }

    AnalyzerBuilder withPipelineConfiguration(PipelineConfiguration.Builder pipelineConfiguration) {
        this.pipelineConfiguration = pipelineConfiguration;
        return this;
    }

    AnalyzerBuilder withAnalysisConfiguration(PlexusConfiguration analysisConfiguration) {
        this.analysisConfiguration = analysisConfiguration;
        return this;
    }

    AnalyzerBuilder withAnalysisConfigurationFiles(Object[] analysisConfigurationFiles) {
        this.analysisConfigurationFiles = analysisConfigurationFiles;
        return this;
    }

    AnalyzerBuilder withRepositorySystem(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
        return this;
    }

    AnalyzerBuilder withRepositorySystemSession(RepositorySystemSession repositorySystemSession) {
        this.repositorySystemSession = repositorySystemSession;
        return this;
    }

    AnalyzerBuilder withFailOnMissingConfigurationFiles(boolean failOnMissingConfigurationFiles) {
        this.failOnMissingConfigurationFiles = failOnMissingConfigurationFiles;
        return this;
    }

    AnalyzerBuilder withFailOnUnresolvedDependencies(boolean failOnUnresolvedDependencies) {
        this.failOnUnresolvedDependencies = failOnUnresolvedDependencies;
        return this;
    }

    AnalyzerBuilder withFailOnUnresolvedArtifacts(boolean failOnUnresolvedArtifacts) {
        this.failOnUnresolvedArtifacts = failOnUnresolvedArtifacts;
        return this;
    }

    AnalyzerBuilder withAlwaysCheckForReleasedVersion(boolean alwaysCheckForReleaseVersion) {
        this.alwaysCheckForReleaseVersion = alwaysCheckForReleaseVersion;
        return this;
    }

    AnalyzerBuilder withCheckDependencies(boolean checkDependencies) {
        this.checkDependencies = checkDependencies;
        return this;
    }

    AnalyzerBuilder withResolveProvidedDependencies(boolean resolveProvidedDependencies) {
        this.resolveProvidedDependencies = resolveProvidedDependencies;
        return this;
    }

    AnalyzerBuilder withResolveTransitiveProvidedDependencies(boolean resolveTransitiveProvidedDependencies) {
        this.resolveTransitiveProvidedDependencies = resolveTransitiveProvidedDependencies;
        return this;
    }

    AnalyzerBuilder withExpandProperties(boolean expandProperties) {
        this.expandProperties = expandProperties;
        return this;
    }

    AnalyzerBuilder withVersionFormat(String versionFormat) {
        this.versionFormat = versionFormat;
        return this;
    }

    AnalyzerBuilder withLog(Log log) {
        this.log = log;
        return this;
    }

    AnalyzerBuilder withRevapiInstance(Revapi revapi) {
        this.revapi = revapi;
        return this;
    }

    AnalyzerBuilder withContextData(Map<String, Object> contextData) {
        if (contextData != null) {
            this.contextData.putAll(contextData);
        }
        return this;
    }

    public Result build() {
        Result res = new Result();

        initializeComparisonArtifacts();

        res.newArtifacts = newGavs;
        res.oldArtifacts = oldGavs;

        res.skip = skip;

        res.analyzer = prepareAnalyzer();

        return res;
    }

    private Analyzer prepareAnalyzer() {
        if (skip) {
            return null;
        }


        final List<String> disallowedExtensions = this.disallowedExtensions == null
                ? Collections.emptyList()
                : Arrays.asList(this.disallowedExtensions.split("\\s*,\\s*"));

        Consumer<PipelineConfiguration.Builder> pipelineModifier =
                applyDisallowedExtensionsToPipeline(disallowedExtensions);

        return new Analyzer(pipelineConfiguration, analysisConfiguration, analysisConfigurationFiles, oldArtifacts,
                newArtifacts, oldGavs, newGavs, project, repositorySystem, repositorySystemSession, reporterType,
                contextData, locale, log, failOnMissingConfigurationFiles, failOnUnresolvedArtifacts,
                failOnUnresolvedDependencies, alwaysCheckForReleaseVersion, checkDependencies,
                resolveProvidedDependencies, resolveTransitiveProvidedDependencies, expandProperties, versionFormat,
                pipelineModifier, revapi);
    }

    private void initializeComparisonArtifacts() {
        if (oldArtifacts == null) {
            initializeComparisonGavs();
        }
    }

    private void initializeComparisonGavs() {
        if (newGavs != null && newGavs.length == 1 && "BUILD".equals(newGavs[0])) {
            log.warn("\"BUILD\" coordinates are deprecated. Just leave \"newArtifacts\" undefined and specify" +
                    " \"${project.version}\" as the value for \"newVersion\" (which is the default, so you don't" +
                    " actually have to do that either).");
            oldGavs = null;
        }

        if (oldGavs == null || oldGavs.length == 0) {
            //non-intuitively, we need to initialize the artifacts even if we will not proceed with the analysis itself
            //that's because we need know the versions when figuring out the version modifications -
            //see AbstractVersionModifyingMojo
            oldGavs = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, oldVersion)};
        }

        if (newGavs == null || newGavs.length == 0) {
            newGavs = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, newVersion)};
        }
   }

    private static Consumer<PipelineConfiguration.Builder>
    applyDisallowedExtensionsToPipeline(List<String> disallowedExtensions) {
        return (bld) -> {
            List<Class<? extends ApiAnalyzer>> analyzers = new ArrayList<>();
            List<Class<? extends TreeFilterProvider>> filters = new ArrayList<>();
            List<Class<? extends DifferenceTransform>> transforms = new ArrayList<>();
            List<Class<? extends Reporter>> reporters = new ArrayList<>();
            List<Class<? extends ElementFilter>> legacyFilters = new ArrayList<>();
            List<Class<? extends ElementMatcher>> matchers = new ArrayList<>();

            addAllAllowed(analyzers, ServiceTypeLoader.load(ApiAnalyzer.class), disallowedExtensions);
            addAllAllowed(filters, ServiceTypeLoader.load(TreeFilterProvider.class), disallowedExtensions);
            addAllAllowed(transforms, ServiceTypeLoader.load(DifferenceTransform.class), disallowedExtensions);
            addAllAllowed(reporters, ServiceTypeLoader.load(Reporter.class), disallowedExtensions);
            addAllAllowed(legacyFilters, ServiceTypeLoader.load(ElementFilter.class), disallowedExtensions);
            addAllAllowed(matchers, ServiceTypeLoader.load(ElementMatcher.class), disallowedExtensions);

            filters.addAll(legacyFilters);

            bld.withAnalyzers(analyzers).withFilters(filters).withTransforms(transforms).withReporters(reporters)
                .withMatchers(matchers);
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> void addAllAllowed(List<Class<? extends T>> list, Iterable<Class<? extends T>> candidates,
                                          List<String> disallowedClassNames) {
        for (Class<? extends T> c : candidates) {
            if (c != null && !disallowedClassNames.contains(c.getName())) {
                list.add(c);
            }
        }
    }

    static class Result {
        boolean skip;
        String[] oldArtifacts;
        String[] newArtifacts;
        Analyzer analyzer;

        private Result() {}
    }
}
