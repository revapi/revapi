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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositoryException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.configuration.JSONUtil;
import org.revapi.maven.utils.ArtifactResolver;
import org.revapi.maven.utils.ScopeDependencySelector;
import org.revapi.maven.utils.ScopeDependencyTraverser;

import org.jboss.dmr.ModelNode;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class Analyzer {
    private static final String BUILD_COORDINATES = "BUILD";

    /**
     * The JSON configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     *
     * <p>These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    private final String analysisConfiguration;

    /**
     * The list of files containing the configuration of various analysis options.
     * The available options depend on what analyzers are present on the plugins classpath through the
     * {@code &lt;dependencies&gt;}.
     *
     * <p>The {@code analysisConfiguration} can override the settings present in the files.
     *
     * <p>The list is either a list of strings or has the following form:
     * <pre><code>
     *    &lt;analysisConfigurationFiles&gt;
     *        &lt;configurationFile&gt;
     *            &lt;path&gt;path/to/the/file/relative/to/project/base/dir&lt;/path&gt;
     *            &lt;roots&gt;
     *                &lt;root&gt;configuration/root1&lt;/root&gt;
     *                &lt;root&gt;configuration/root2&lt;/root&gt;
     *                ...
     *            &lt;/roots&gt;
     *        &lt;/configurationFile&gt;
     *        ...
     *    &lt;/analysisConfigurationFiles&gt;
     * </code></pre>
     *
     * where
     * <ul>
     *     <li>{@code path} is mandatory,</li>
     *     <li>{@code roots} is optional and specifies the subtrees of the JSON config that should be used for
     *     configuration. If not specified, the whole file is taken into account.</li>
     * </ul>
     * The {@code configuration/root1} and {@code configuration/root2} are JSON paths to the roots of the
     * configuration inside that JSON config file. This might be used in cases where multiple configurations are stored
     * within a single file and you want to use a particular one.
     *
     * <p>An example of this might be a config file which contains API changes to be ignored in all past versions of a
     * library. The classes to be ignored are specified in a configuration that is specific for each version:
     * <pre><code>
     *     {
     *         "0.1.0" : {
     *             "revapi" : {
     *                 "ignore" : [
     *                     {
     *                         "code" : "java.method.addedToInterface",
     *                         "new" : "method void com.example.MyInterface::newMethod()",
     *                         "justification" : "This interface is not supposed to be implemented by clients."
     *                     },
     *                     ...
     *                 ]
     *             }
     *         },
     *         "0.2.0" : {
     *             ...
     *         }
     *     }
     * </code></pre>
     */
    private final Object[] analysisConfigurationFiles;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the
     * current
     * project.
     *
     * <p>If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    private final String[] oldArtifacts;

    /**
     * The coordinates of the new artifacts. Defaults to single artifact with the artifacts from the build.
     * If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    private final String[] newArtifacts;

    private final MavenProject project;

    private final RepositorySystem repositorySystem;

    private final RepositorySystemSession repositorySystemSession;

    private final Reporter reporter;
    private final Locale locale;

    private final Log log;

    private final boolean failOnMissingConfigurationFiles;

    Analyzer(String analysisConfiguration, Object[] analysisConfigurationFiles, String[] oldArtifacts,
        String[] newArtifacts, MavenProject project, RepositorySystem repositorySystem,
        RepositorySystemSession repositorySystemSession, Reporter reporter, Locale locale, Log log,
        boolean failOnMissingConfigurationFiles) {

        this.analysisConfiguration = analysisConfiguration;
        this.analysisConfigurationFiles = analysisConfigurationFiles;
        this.oldArtifacts = oldArtifacts;
        this.newArtifacts = newArtifacts;
        this.project = project;
        this.repositorySystem = repositorySystem;

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySystemSession);
        session.setDependencySelector(new ScopeDependencySelector("compile", "provided"));
        session.setDependencyTraverser(new ScopeDependencyTraverser("compile", "provided"));

        this.repositorySystemSession = session;

        this.reporter = reporter;
        this.locale = locale;
        this.log = log;
        this.failOnMissingConfigurationFiles = failOnMissingConfigurationFiles;
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

    @SuppressWarnings("unchecked")
    void analyze() throws MojoExecutionException {
        final BuildAwareArtifactResolver resolver = new BuildAwareArtifactResolver();

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
                    return new MavenArchive(a);
                } catch (ArtifactResolutionException | IllegalArgumentException e) {
                    throw new MarkerException(e.getMessage());
                }
            }
        };

        Function<Artifact, MavenArchive> artifactToFileArchive = new Function<Artifact, MavenArchive>() {
            @Override
            public MavenArchive apply(Artifact artifact) {
                try {
                    return new MavenArchive(artifact);
                } catch (IllegalArgumentException e) {
                    throw new MarkerException(e.getMessage());
                }
            }
        };

        List<MavenArchive> oldArchives;
        try {
            oldArchives = (List) Arrays.asList(oldArtifacts).stream().map(toFileArchive).collect(Collectors.toList());
        } catch (MarkerException e) {
            log.warn("Failed to resolve old artifacts: " + e.getMessage() + ". The API analysis will not proceed.");
            return;
        }

        List<MavenArchive> newArchives;
        try {
            newArchives = (List) Arrays.asList(newArtifacts).stream().map(toFileArchive).collect(Collectors.toList());
        } catch (MarkerException e) {
            log.warn("Failed to resolve new artifacts: " + e.getMessage() + ". The API analysis will not proceed.");
            return;
        }

        Set<MavenArchive> oldTransitiveDeps = Collections.emptySet();
        try {
            oldTransitiveDeps = (Set) resolver.collectTransitiveDeps(oldArtifacts).stream()
                .map(artifactToFileArchive).collect(Collectors.toSet());

        } catch (RepositoryException | MarkerException e) {
            log.warn("Failed to resolve dependencies of old artifacts: " + e.getMessage() +
                ". The API analysis might produce unexpected results.");
        }

        Set<MavenArchive> newTransitiveDeps = Collections.emptySet();
        try {
            newTransitiveDeps = (Set) resolver.collectTransitiveDeps(newArtifacts).stream()
                .map(artifactToFileArchive).collect(Collectors.toSet());
        } catch (RepositoryException e) {
            log.warn("Failed to resolve dependencies of new artifacts: " + e.getMessage() +
                ". The API analysis might produce unexpected results.");
        }

        try {
            Revapi revapi = Revapi.builder().withAllExtensionsFromThreadContextClassLoader().withReporters(reporter)
                .build();

            AnalysisContext.Builder ctxBuilder = AnalysisContext.builder()
                .withOldAPI(API.of(oldArchives).supportedBy(oldTransitiveDeps).build())
                .withNewAPI(API.of(newArchives).supportedBy(newTransitiveDeps).build())
                .withLocale(locale);
            gatherConfig(ctxBuilder);

            revapi.analyze(ctxBuilder.build());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze archives", e);
        }
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
                        log.warn(message);
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

    private class BuildAwareArtifactResolver extends ArtifactResolver {

        public BuildAwareArtifactResolver() {
            super(repositorySystem, repositorySystemSession, project.getRemoteProjectRepositories());
        }

        @Override
        protected void collectTransitiveDeps(String gav, Set<Artifact> resolvedArtifacts) throws RepositoryException {

            if (BUILD_COORDINATES.equals(gav)) {
                addProjectDeps(resolvedArtifacts);
            } else {
                super.collectTransitiveDeps(gav, resolvedArtifacts);
            }
        }

        @Override
        public Artifact resolveArtifact(String gav) throws ArtifactResolutionException {
            if (BUILD_COORDINATES.equals(gav)) {
                Artifact ret = toAetherArtifact(project.getArtifact(), repositorySystemSession);

                //project.getArtifact().getFile() returns null for pom-packaged projects
                if ("pom".equals(project.getArtifact().getType())) {
                    ret = ret.setFile(new File(project.getBasedir(), "pom.xml"));
                } else {
                    ret = ret.setFile(project.getArtifact().getFile());
                }

                return ret;
            } else {
                return super.resolveArtifact(gav);
            }
        }

        private Artifact toAetherArtifact(org.apache.maven.artifact.Artifact artifact, RepositorySystemSession session) {
            String extension = session.getArtifactTypeRegistry().get(artifact.getType()).getExtension();
            return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), extension,
                artifact.getVersion());
        }

        private void addProjectDeps(Set<Artifact> resolvedArchives) throws RepositoryException {
            for (org.apache.maven.model.Dependency dep : project.getDependencies()) {
                String scope = dep.getScope();
                if (scope == null || "compile".equals(scope) || "provided".equals(scope)) {
                    String coords = dep.getGroupId() + ":" + dep.getArtifactId();
                    if (dep.getType() != null) {
                        coords += ":" + dep.getType();
                    }
                    if (dep.getClassifier() != null) {
                        coords += ":" + dep.getClassifier();
                    }
                    coords += ":" + dep.getVersion();

                    Artifact a = resolveArtifact(coords);

                    resolvedArchives.add(a);

                    collectTransitiveDeps(coords, resolvedArchives);
                }
            }
        }
    }

    private static class MarkerException extends RuntimeException {
        public MarkerException() {
        }

        public MarkerException(Throwable cause) {
            super(cause);
        }

        public MarkerException(String message) {
            super(message);
        }

        public MarkerException(String message, Throwable cause) {
            super(message, cause);
        }

        public MarkerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
