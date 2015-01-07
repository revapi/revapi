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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.revapi.API;
import org.revapi.AnalysisContext;
import org.revapi.Reporter;
import org.revapi.Revapi;
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

    void analyze() throws MojoExecutionException {
        List<FileArchive> oldArchives;
        try {
            oldArchives = resolveArtifacts(oldArtifacts);
        } catch (ArtifactResolutionException e) {
            log.warn("Failed to resolve old artifacts: " + e.getMessage() + ". The API analysis will not proceed.");
            return;
        }

        List<FileArchive> newArchives;
        try {
            newArchives = resolveArtifacts(newArtifacts);
        } catch (ArtifactResolutionException e) {
            log.warn("Failed to resolve new artifacts: " + e.getMessage() + ". The API analysis will not proceed.");
            return;
        }

        Set<FileArchive> oldTransitiveDeps = Collections.emptySet();
        try {
            oldTransitiveDeps = collectTransitiveDeps(oldArtifacts);
        } catch (DependencyCollectionException | ArtifactResolutionException | DependencyResolutionException e) {
            log.warn("Failed to resolve dependencies of old artifacts: " + e.getMessage() +
                ". The API analysis might produce unexpected results.");
        }

        Set<FileArchive> newTransitiveDeps = Collections.emptySet();
        try {
            newTransitiveDeps = collectTransitiveDeps(newArtifacts);
        } catch (DependencyCollectionException | ArtifactResolutionException | DependencyResolutionException e) {
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
                    ModelNode config = ModelNode.fromJSONStream(in);

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
                } catch (IOException ignored) {
                    throw new MojoExecutionException("Could not load configuration from '" + f.getAbsolutePath() + "'.");
                }
            }
        }

        if (analysisConfiguration != null) {
            ctxBld.mergeConfigurationFromJSON(analysisConfiguration);
        }
    }

    private List<FileArchive> resolveArtifacts(String[] coordinates) throws ArtifactResolutionException {

        if (coordinates.length == 1 && BUILD_COORDINATES.equals(coordinates[0])) {
            return resolveBuildArtifacts();
        }

        List<ArtifactRequest> requests = new ArrayList<>();
        for (String coord : coordinates) {
            DefaultArtifact artifact = new DefaultArtifact(coord);
            ArtifactRequest request = new ArtifactRequest().setArtifact(artifact)
                .setRepositories(project.getRemoteProjectRepositories());

            requests.add(request);
        }

        List<ArtifactResult> results = repositorySystem.resolveArtifacts(repositorySystemSession, requests);

        List<FileArchive> archives = new ArrayList<>();
        for (ArtifactResult res : results) {
            archives.add(new FileArchive(res.getArtifact().getFile()));
        }
        return archives;
    }

    private Artifact resolveArtifact(String coordinates) throws ArtifactResolutionException {
        if (BUILD_COORDINATES.equals(coordinates)) {
            return toAetherArtifact(project.getArtifact(), repositorySystemSession);
        }

        DefaultArtifact artifact = new DefaultArtifact(coordinates);
        ArtifactRequest request = new ArtifactRequest().setArtifact(artifact)
            .setRepositories(project.getRemoteProjectRepositories());

        ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, request);
        return result.getArtifact();
    }

    private static Artifact toAetherArtifact(org.apache.maven.artifact.Artifact artifact, RepositorySystemSession session) {
        String extension = session.getArtifactTypeRegistry().get(artifact.getType()).getExtension();
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), extension,
            artifact.getVersion());
    }

    private Set<FileArchive> collectTransitiveDeps(String[] coordinates)
        throws DependencyCollectionException, ArtifactResolutionException, DependencyResolutionException {
        Set<FileArchive> results = new LinkedHashSet<>(); //so that it is easier to compare the differences - randomized
                                                          //order of the HashSet wouldn't help...

        for (String coord : coordinates) {
            collectTransitiveDeps(coord, results);
        }

        return results;
    }

    private void collectTransitiveDeps(String coordinates, final Set<FileArchive> resolvedArchives)
        throws ArtifactResolutionException, DependencyCollectionException, DependencyResolutionException {

        if (BUILD_COORDINATES.equals(coordinates)) {
            addProjectDeps(resolvedArchives);
            return;
        }

        final Artifact rootArtifact = resolveArtifact(coordinates);

        CollectRequest collectRequest = new CollectRequest(new Dependency(rootArtifact, null),
            project.getRemoteProjectRepositories());

        DependencyRequest request = new DependencyRequest(collectRequest, null);

        DependencyResult result = repositorySystem.resolveDependencies(repositorySystemSession, request);
        result.getRoot().accept(new TreeDependencyVisitor(new DependencyVisitor() {
            @Override
            public boolean visitEnter(DependencyNode node) {
                return true;
            }

            @Override
            public boolean visitLeave(DependencyNode node) {
                Dependency dep = node.getDependency();
                if (dep == null || dep.getArtifact().equals(rootArtifact)) {
                    return true;
                }

                resolvedArchives.add(new FileArchive(dep.getArtifact().getFile()));

                return true;
            }
        }));
    }

    private List<FileArchive> resolveBuildArtifacts() {
        FileArchive archive;

        //project.getArtifact().getFile() returns null for pom-packaged projects
        if ("pom".equals(project.getArtifact().getType())) {
            archive = new FileArchive(new File(project.getBasedir(), "pom.xml"));
        } else {
            archive = new FileArchive(project.getArtifact().getFile());
        }

        return Collections.singletonList(archive);
    }

    private void addProjectDeps(Set<FileArchive> resolvedArchives)
        throws ArtifactResolutionException, DependencyCollectionException, DependencyResolutionException {
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

                resolvedArchives.add(new FileArchive(a.getFile()));

                collectTransitiveDeps(coords, resolvedArchives);
            }
        }
    }
}
