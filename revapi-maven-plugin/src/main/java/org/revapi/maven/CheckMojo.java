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

package org.revapi.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
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
import org.revapi.Revapi;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractMojo {

    private static final String BUILD_COORDINATES = "BUILD";

    /**
     * The JSON configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     * <p/>
     * These settings take precendence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    @Parameter
    private String analysisConfiguration;

    /**
     * The list of files containing the configuration of various analysis options.
     * The available options depend on what analyzers are present on the plugins classpath through the
     * {@code &lt;dependencies&gt;}.
     * <p/>
     * The {@code analysisConfiguration} can override the settings present in the files.
     */
    @Parameter
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private String[] analysisConfigurationFiles;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the
     * current
     * project.
     * <p/>
     * If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    @Parameter(defaultValue = "${project.groupId}:${project.artifactId}:RELEASE")
    private String[] oldArtifacts;

    /**
     * The coordinates of the new artifacts. Defaults to single artifact with the artifacts from the build.
     * If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    @Parameter(defaultValue = BUILD_COORDINATES)
    private String[] newArtifacts;

    @Parameter
    private boolean skip;

    /**
     * The severity of found problems at which to break the build. Defaults to API breaking changes.
     * Possible values: nonBreaking, potentiallyBreaking, breaking.
     */
    @Parameter(defaultValue = "breaking")
    private FailSeverity failSeverity;

    @Component
    private MavenProject project;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        List<FileArchive> oldArchives;
        try {
            oldArchives = resolveArtifacts(oldArtifacts);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve old artifacts", e);
        }

        List<FileArchive> newArchives;
        try {
            newArchives = resolveArtifacts(newArtifacts);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve new artifacts", e);
        }

        List<FileArchive> oldTransitiveDeps;
        try {
            oldTransitiveDeps = collectTransitiveDeps(oldArtifacts);
        } catch (DependencyCollectionException | ArtifactResolutionException | DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve transitive dependencies of old artifacts", e);
        }

        List<FileArchive> newTransitiveDeps;
        try {
            newTransitiveDeps = collectTransitiveDeps(newArtifacts);
        } catch (DependencyCollectionException | ArtifactResolutionException | DependencyResolutionException e) {
            throw new MojoExecutionException("Failed to resolve transitive dependencies of new artifacts", e);
        }

        try {
            MavenReporter reporter = new MavenReporter(failSeverity.toChangeSeverity());

            Revapi revapi = Revapi.builder().withAllExtensionsFromThreadContextClassLoader().withReporters(reporter)
                .build();

            AnalysisContext.Builder ctxBuilder = AnalysisContext.builder()
                .withOldAPI(API.of(oldArchives).supportedBy(oldTransitiveDeps).build())
                .withNewAPI(API.of(newArchives).supportedBy(newTransitiveDeps).build());
            gatherConfig(ctxBuilder);

            revapi.analyze(ctxBuilder.build());

            if (reporter.hasBreakingProblems()) {
                throw new MojoFailureException(reporter.getAllProblemsMessage());
            } else {
                getLog().info("API checks completed without failures.");
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to analyze archives", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void gatherConfig(AnalysisContext.Builder ctxBld) throws MojoExecutionException {
        if (analysisConfigurationFiles != null && analysisConfigurationFiles.length > 0) {
            for (String path : analysisConfigurationFiles) {
                File f = new File(path);
                if (!f.isFile() || !f.canRead()) {
                    throw new MojoExecutionException("Could not locate analysis configuration file in '" + path + "'.");
                }

                try (FileInputStream in = new FileInputStream(f)) {
                    ctxBld.mergeConfigurationFromJSONStream(in);
                } catch (IOException ignored) {
                    throw new MojoExecutionException("Could not load configuration from '" + path + "'.");
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
            return toAetherArtifact(project.getArtifact());
        }

        DefaultArtifact artifact = new DefaultArtifact(coordinates);
        ArtifactRequest request = new ArtifactRequest().setArtifact(artifact)
            .setRepositories(project.getRemoteProjectRepositories());

        ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, request);
        return result.getArtifact();
    }

    private Artifact toAetherArtifact(org.apache.maven.artifact.Artifact artifact) {
        return new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), null,
            artifact.getVersion());
    }

    private List<FileArchive> collectTransitiveDeps(String[] coordinates)
        throws DependencyCollectionException, ArtifactResolutionException, DependencyResolutionException {
        List<FileArchive> results = new ArrayList<>();

        for (String coord : coordinates) {
            collectTransitiveDeps(coord, results);
        }

        return results;
    }

    private void collectTransitiveDeps(String coordinates, final List<FileArchive> resolvedArchives)
        throws ArtifactResolutionException, DependencyCollectionException, DependencyResolutionException {

        if (BUILD_COORDINATES.equals(coordinates)) {
            addProjectDeps(resolvedArchives);
            return;
        }

        final Artifact rootArtifact = resolveArtifact(coordinates);

        CollectRequest collectRequest = new CollectRequest(new Dependency(resolveArtifact(coordinates), "compile"),
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
                Artifact a = node.getDependency().getArtifact();

                if (!a.equals(rootArtifact)) {
                    resolvedArchives.add(new FileArchive(a.getFile()));
                }

                return true;
            }
        }));
    }

    private List<FileArchive> resolveBuildArtifacts() {
        FileArchive archive = new FileArchive(project.getArtifact().getFile());
        return Collections.singletonList(archive);
    }

    private void addProjectDeps(List<FileArchive> resolvedArchives)
        throws ArtifactResolutionException, DependencyCollectionException, DependencyResolutionException {
        for (org.apache.maven.model.Dependency dep : project.getDependencies()) {
            String scope = dep.getScope();
            if (scope == null || "compile".equals(scope)) {
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
