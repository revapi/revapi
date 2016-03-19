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

package org.revapi.maven;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.dmr.ModelNode;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.configuration.JSONUtil;
import org.revapi.maven.utils.ArtifactResolver;
import org.revapi.maven.utils.ScopeDependencySelector;
import org.revapi.maven.utils.ScopeDependencyTraverser;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class Analyzer {
    private final String analysisConfiguration;

    private final Object[] analysisConfigurationFiles;

    private final String[] oldArtifacts;

    private final String[] newArtifacts;

    private final MavenProject project;

    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession repositorySystemSession;

    private final Reporter reporter;
    private final Locale locale;

    private final Log log;

    private final boolean failOnMissingConfigurationFiles;

    private final boolean failOnMissingArchives;

    private final boolean failOnMissingSupportArchives;

    private final Supplier<Revapi.Builder> revapiConstructor;

    private API resolvedOldApi;
    private API resolvedNewApi;

    Analyzer(String analysisConfiguration, Object[] analysisConfigurationFiles, String[] oldArtifacts,
             String[] newArtifacts, MavenProject project, RepositorySystem repositorySystem,
             RepositorySystemSession repositorySystemSession, Reporter reporter, Locale locale, Log log,
             boolean failOnMissingConfigurationFiles, boolean failOnMissingArchives,
             boolean failOnMissingSupportArchives, boolean alwaysUpdate) {

        this(analysisConfiguration, analysisConfigurationFiles, oldArtifacts, newArtifacts, project, repositorySystem,
                repositorySystemSession, reporter, locale, log, failOnMissingConfigurationFiles, failOnMissingArchives,
                failOnMissingSupportArchives, alwaysUpdate,
                new Supplier<Revapi.Builder>() {
                    @Override public Revapi.Builder get() {
                        return Revapi.builder().withAllExtensionsFromThreadContextClassLoader();
                    }
                });
    }

    Analyzer(String analysisConfiguration, Object[] analysisConfigurationFiles, String[] oldArtifacts,
             String[] newArtifacts, MavenProject project, RepositorySystem repositorySystem,
             RepositorySystemSession repositorySystemSession, Reporter reporter, Locale locale, Log log,
             boolean failOnMissingConfigurationFiles, boolean failOnMissingArchives,
             boolean failOnMissingSupportArchives, boolean alwaysUpdate, Supplier<Revapi.Builder> revapiConstructor) {

        this.analysisConfiguration = analysisConfiguration;
        this.analysisConfigurationFiles = analysisConfigurationFiles;
        this.oldArtifacts = oldArtifacts;
        this.newArtifacts = newArtifacts;
        this.project = project;
        this.repositorySystem = repositorySystem;

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySystemSession);
        session.setDependencySelector(new ScopeDependencySelector("compile", "provided"));
        session.setDependencyTraverser(new ScopeDependencyTraverser("compile", "provided"));

        if (alwaysUpdate) {
            session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }

        this.repositorySystemSession = session;

        this.reporter = reporter;
        this.locale = locale;
        this.log = log;
        this.failOnMissingConfigurationFiles = failOnMissingConfigurationFiles;
        this.failOnMissingArchives = failOnMissingArchives;
        this.failOnMissingSupportArchives = failOnMissingSupportArchives;
        this.revapiConstructor = revapiConstructor;
    }

    public static String getProjectArtifactCoordinates(MavenProject project, RepositorySystemSession session,
        String versionOverride) {

        org.apache.maven.artifact.Artifact artifact = project.getArtifact();

        String extension = session.getArtifactTypeRegistry().get(artifact.getType()).getExtension();

        String version = versionOverride == null ? project.getVersion() : versionOverride;

        if (artifact.hasClassifier()) {
            return project.getGroupId() + ":" + project.getArtifactId() + ":" + extension + ":" +
                    artifact.getClassifier() + ":" + version;
        } else {
            return project.getGroupId() + ":" + project.getArtifactId() + ":" + extension + ":" +
                    version;
        }
    }

    void validateConfiguration() throws MojoExecutionException {
        try {
            Revapi revapi = Revapi.builder().withAllExtensionsFromThreadContextClassLoader().build();

            AnalysisContext.Builder ctxBuilder = AnalysisContext.builder().withLocale(locale);
            gatherConfig(ctxBuilder);

            revapi.validateConfiguration(ctxBuilder.build());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to validate analysis configuration.", e);
        }
    }

    @SuppressWarnings("unchecked")
    void resolveArtifacts() {
        if (resolvedOldApi == null) {
            final ArtifactResolver resolver = new ArtifactResolver(repositorySystem, repositorySystemSession,
                    project.getRemoteProjectRepositories());

            //Ok, what on Earth is this?
            //We're building with Java8 and I really like lambdas but Maven in the version we're using doesn't like
            //lambdas in the bytecode of the plugin - it'll actually fail to scan the archive for the annotations and
            //therefore will not properly build the maven plugin we're implementing here.

            //Sooo, we're building with Java8, so we have access to Java8 classes but cannot use Java8 features at all here.
            //Hence, instead of a lambda, we use these Function instances and further below we need to make unsafe casts
            //that Java8 would have correctly figured out on its own. Yay.

            Function<String, MavenArchive> toFileArchive = new Function<String, MavenArchive>() {
                @Override
                public MavenArchive apply(String gav) {
                    try {
                        Artifact a = resolver.resolveArtifact(gav);
                        return MavenArchive.of(a);
                    } catch (ArtifactResolutionException | IllegalArgumentException e) {
                        throw new MarkerException(e.getMessage(), e);
                    }
                }
            };

            List<MavenArchive> oldArchives;
            try {
                oldArchives = (List) Arrays.asList(oldArtifacts).stream().map(toFileArchive).collect(toList());
            } catch (MarkerException e) {
                String message = "Failed to resolve old artifacts: " + e.getMessage() + ".";

                if (failOnMissingArchives) {
                    throw new IllegalStateException(message, e);
                } else {
                    log.warn(message + " The API analysis will not proceed.", e);
                    return;
                }
            }

            List<MavenArchive> newArchives;
            try {
                newArchives = (List) Arrays.asList(newArtifacts).stream().map(toFileArchive).collect(toList());
            } catch (MarkerException e) {
                String message = "Failed to resolve new artifacts: " + e.getMessage() + ".";

                if (failOnMissingArchives) {
                    throw new IllegalStateException(message, e);
                } else {
                    log.warn(message + " The API analysis will not proceed.", e);
                    return;
                }
            }

            Set<MavenArchive> oldTransitiveDeps = collectDeps("old", resolver, oldArtifacts);
            Set<MavenArchive> newTransitiveDeps = collectDeps("new", resolver, newArtifacts);

            resolvedOldApi = API.of(oldArchives).supportedBy(oldTransitiveDeps).build();
            resolvedNewApi = API.of(newArchives).supportedBy(newTransitiveDeps).build();
        }
    }

    @SuppressWarnings("unchecked")
    private Set<MavenArchive> collectDeps(String depDescription, ArtifactResolver resolver, String... gavs) {
        Set<MavenArchive> ret = null;
        try {
            ArtifactResolver.CollectionResult res = resolver.collectTransitiveDeps(gavs);

            ret = new HashSet<>();
            for (Artifact a : res.getResolvedArtifacts()) {
                try {
                    ret.add(MavenArchive.of(a));
                } catch (IllegalArgumentException e) {
                    res.getFailures().add(e);
                }
            }

            if (!res.getFailures().isEmpty()) {
                StringBuilder bld = new StringBuilder();
                for (Exception e : res.getFailures()) {
                    bld.append(e.getMessage()).append(", ");
                }
                bld.replace(bld.length() - 2, bld.length(), "");
                throw new MarkerException("Resolution of some artifacts failed: " + bld.toString());
            } else {
                return ret;
            }
        } catch (RepositoryException | MarkerException e) {
            String message = "Failed to resolve dependencies of " + depDescription + " artifacts: " + e.getMessage() +
                    ".";
            if (failOnMissingSupportArchives) {
                throw new IllegalArgumentException(message, e);
            } else {
                if (log.isDebugEnabled()) {
                    log.warn(message + ". The API analysis might produce unexpected results.", e);
                } else {
                    log.warn(message + ". The API analysis might produce unexpected results.");
                }
                return ret == null ? Collections.<MavenArchive>emptySet() : ret;
            }
        }
    }

    @SuppressWarnings("unchecked")
    void analyze() throws MojoExecutionException {
        //This is useful so that users know what RELEASE and BUILD actually resolved to.
        Function<MavenArchive, String> extractName = new Function<MavenArchive, String>() {
            @Override public String apply(MavenArchive mavenArchive) {
                return mavenArchive.getName();
            }
        };

        resolveArtifacts();

        if (resolvedOldApi == null || resolvedNewApi == null) {
            return;
        }

        List<?> oldArchives = StreamSupport.stream(
                (Spliterator<MavenArchive>) resolvedOldApi.getArchives().spliterator(), false)
                .map(extractName).collect(toList());

        List<?> newArchives =  StreamSupport.stream(
                (Spliterator<MavenArchive>) resolvedNewApi.getArchives().spliterator(), false)
                .map(extractName).collect(toList());

        log.info("Comparing " + oldArchives + " against " + newArchives +
                " (including their transitive dependencies).");

        try {
            Revapi revapi = revapiConstructor.get().withReporters(reporter).build();

            AnalysisContext.Builder ctxBuilder = AnalysisContext.builder().withOldAPI(resolvedOldApi)
                    .withNewAPI(resolvedNewApi).withLocale(locale);
            gatherConfig(ctxBuilder);

            revapi.analyze(ctxBuilder.build());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze archives", e);
        }
    }

    public API getResolvedNewApi() {
        return resolvedNewApi;
    }

    public API getResolvedOldApi() {
        return resolvedOldApi;
    }

    @SuppressWarnings("unchecked")
    private void gatherConfig(AnalysisContext.Builder ctxBld) throws MojoExecutionException {
        if (analysisConfigurationFiles != null && analysisConfigurationFiles.length > 0) {
            for (Object pathOrConfigFile : analysisConfigurationFiles) {
                ConfigurationFile configFile;
                if (pathOrConfigFile instanceof String) {
                    configFile = new ConfigurationFile();
                    configFile.setPath((String) pathOrConfigFile);
                } else {
                    configFile = (ConfigurationFile) pathOrConfigFile;
                }

                String path = configFile.getPath();

                File f = new File(path);
                if (!f.isAbsolute()) {
                    f = new File(project.getBasedir(), path);
                }

                if (!f.isFile() || !f.canRead()) {
                    String message = "Could not locate analysis configuration file '" + f.getAbsolutePath() + "'.";
                    if (failOnMissingConfigurationFiles) {
                        throw new MojoExecutionException(message);
                    } else {
                        log.debug(message);
                        continue;
                    }
                }

                try (FileInputStream in = new FileInputStream(f)) {
                    ModelNode config = ModelNode.fromJSONStream(JSONUtil.stripComments(in, Charset.forName("UTF-8")));

                    String[] roots = configFile.getRoots();

                    if (roots == null) {
                        ctxBld.mergeConfiguration(config);
                    } else {
                        for (String r : roots) {
                            String[] rootPath = r.split("/");
                            ModelNode root = config.get(rootPath);

                            if (!root.isDefined()) {
                                continue;
                            }

                            ctxBld.mergeConfiguration(root);
                        }
                    }
                } catch (IOException e) {
                    throw new MojoExecutionException("Could not load configuration from '" + f.getAbsolutePath() + "': " + e.getMessage());
                }
            }
        }

        if (analysisConfiguration != null) {
            ctxBld.mergeConfigurationFromJSON(analysisConfiguration);
        }
    }

    private static class MarkerException extends RuntimeException {
        public MarkerException(String message) {
            super(message);
        }

        public MarkerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
