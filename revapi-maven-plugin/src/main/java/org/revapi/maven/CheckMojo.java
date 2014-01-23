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

package org.revapi.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.RepositorySystemSession;
import org.sonatype.aether.resolution.ArtifactRequest;
import org.sonatype.aether.resolution.ArtifactResolutionException;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import org.revapi.Revapi;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractMojo {

    private static final String BUILD_COORDINATES = "BUILD";

    /**
     * The configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     */
    @Parameter
    private Map<String, String> analysisConfiguration;

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

    @Component
    private MavenProject project;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private RepositorySystemSession repositorySystemSession;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        Locale locale = Locale.getDefault();

        Revapi revapi = new Revapi(locale, analysisConfiguration);

        List<FileArchive> oldArchives = null;
        try {
            oldArchives = resolveArtifact(oldArtifacts);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve old artifacts", e);
        }

        List<FileArchive> newArchives = null;
        try {
            newArchives = resolveArtifact(newArtifacts);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve new artifacts", e);
        }

        try {
            //TODO transitive deps as supplementary archives
            revapi.analyze(oldArchives, null, newArchives, null);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to analyze archives", e);
        }
    }

    private List<FileArchive> resolveArtifact(String[] coordinates) throws ArtifactResolutionException {

        if (BUILD_COORDINATES.equals(coordinates)) {
            return resolveBuildArtifacts();
        }

        List<ArtifactRequest> requests = new ArrayList<ArtifactRequest>();
        for (String coord : coordinates) {
            DefaultArtifact artifact = new DefaultArtifact(coord);
            ArtifactRequest request = new ArtifactRequest().setArtifact(artifact)
                .setRepositories(project.getRemoteProjectRepositories());

            requests.add(request);
        }

        List<ArtifactResult> results = repositorySystem.resolveArtifacts(repositorySystemSession, requests);

        List<FileArchive> archives = new ArrayList<FileArchive>();
        for (ArtifactResult res : results) {
            archives.add(new FileArchive(res.getArtifact().getFile()));
        }
        return archives;
    }

    private List<FileArchive> resolveBuildArtifacts() {
        Artifact artifact = project.getArtifact();
        FileArchive archive = new FileArchive(artifact.getFile());
        return Collections.singletonList(archive);
    }
}
