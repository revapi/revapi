/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.maven;

import java.io.Reader;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
@Mojo(name = "detectVersion", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class DetectVersionMojo extends AbstractRevapiMojo {

    @Parameter(name = "writeToPom", defaultValue = "false", property = "revapi.writeVersionToPom")
    private boolean writeToPom;

    @Parameter(name = "writeToReleaseProperties", defaultValue = "false",
            property = "revapi.writeVersionToReleaseProperties")
    private boolean writeToReleaseProperties;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }

        if (newArtifacts.length > 1) {
            throw new MojoExecutionException("Cannot determine new versions of more than 1 artifact at a time.");
        }

        VersionHintingReporter reporter = new VersionHintingReporter();

        if (oldArtifacts == null || oldArtifacts.length == 0) {
            oldArtifacts = new String[]{Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession, "RELEASE")};
        }

        Analyzer analyzer = new Analyzer(analysisConfiguration, analysisConfigurationFiles, oldArtifacts,
                newArtifacts, project, repositorySystem, repositorySystemSession, reporter, Locale.getDefault(), getLog(),
                failOnMissingConfigurationFiles, alwaysCheckForReleaseVersion);

        analyzer.analyze();

        //TODO implement
        if (reporter.breaking) {
            //report X+1.0.0
            getLog().info("X+1.0.0");
        } else if (reporter.newApis) {
            //report X.Y+1.0
            getLog().info("X.Y+1.0");
        } else {
            //if content hashes of artifacts differ, report X.Y.Z+1 otherwise X.Y.Z
            getLog().info("X.Y.Z+1");
        }
    }

    private String getVersionOf(String gav, Analyzer analyzer) throws ArtifactResolutionException {
        Analyzer.BuildAwareArtifactResolver resolver = analyzer.new BuildAwareArtifactResolver();
        Artifact a = resolver.resolveArtifact(gav);
        return a.getVersion();
    }

    private static class VersionHintingReporter implements Reporter {

        private boolean breaking;
        private boolean newApis;

        @Override
        public void report(@Nonnull Report report) {
            if (breaking) {
                return;
            }

            LOOP:
            for (Difference diff : report.getDifferences()) {
                for (DifferenceSeverity s : diff.classification.values()) {
                    newApis = true;
                    breaking |= s == DifferenceSeverity.BREAKING;
                    if (breaking) {
                        break LOOP;
                    }
                }
            }
        }

        @Override
        public void close() throws Exception {
        }

        @Override
        public @Nullable String[] getConfigurationRootPaths() {
            return null;
        }

        @Override
        public @Nullable Reader getJSONSchema(@Nonnull String configurationRootPath) {
            return null;
        }

        @Override
        public void initialize(@Nonnull AnalysisContext analysisContext) {
        }
    }
}
