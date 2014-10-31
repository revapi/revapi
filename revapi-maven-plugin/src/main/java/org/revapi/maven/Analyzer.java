package org.revapi.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.maven.plugin.MojoExecutionException;
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
import org.revapi.Reporter;
import org.revapi.Revapi;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class Analyzer {
    private static final String BUILD_COORDINATES = "BUILD";

    /**
     * The JSON configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     * <p/>
     * These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    private final String analysisConfiguration;

    /**
     * The list of files containing the configuration of various analysis options.
     * The available options depend on what analyzers are present on the plugins classpath through the
     * {@code &lt;dependencies&gt;}.
     * <p/>
     * The {@code analysisConfiguration} can override the settings present in the files.
     */
    private final String[] analysisConfigurationFiles;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the
     * current
     * project.
     * <p/>
     * If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
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

    Analyzer(String analysisConfiguration, String[] analysisConfigurationFiles, String[] oldArtifacts,
        String[] newArtifacts, MavenProject project, RepositorySystem repositorySystem,
        RepositorySystemSession repositorySystemSession, Reporter reporter, Locale locale) {
        this.analysisConfiguration = analysisConfiguration;
        this.analysisConfigurationFiles = analysisConfigurationFiles;
        this.oldArtifacts = oldArtifacts;
        this.newArtifacts = newArtifacts;
        this.project = project;
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
        this.reporter = reporter;
        this.locale = locale;
    }

    void analyze() throws MojoExecutionException {
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
