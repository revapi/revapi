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

import java.util.Locale;

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

/**
 * Runs the API check of old and new artifacts using the specified configuration of extensions declared as dependencies
 * of the plugin.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CheckMojo extends AbstractMojo {

    public static final String BUILD_COORDINATES = "BUILD";

    /**
     * The JSON configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     *
     * <p>These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    @Parameter
    private String analysisConfiguration;

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
    @Parameter(property = "revapi.analysisConfigurationFiles")
    private Object[] analysisConfigurationFiles;

    /**
     * Set to false if you want to tolerate files referenced in the {@code analysisConfigurationFiles} missing on the
     * filesystem and therefore not contributing to the analysis configuration.
     *
     * <p>The default is {@code true}, which means that a missing analysis configuration file will fail the build.
     */
    @Parameter(property = "revapi.failOnMissingConfigurationFiles", defaultValue = "true")
    private boolean failOnMissingConfigurationFiles;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the
     * current project.
     *
     * <p>If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    @Parameter(property = "revapi.oldArtifacts")
    private String[] oldArtifacts;

    /**
     * The coordinates of the new artifacts. Defaults to single artifact with the artifacts from the build.
     * If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    @Parameter(defaultValue = BUILD_COORDINATES, property = "revapi.newArtifacts")
    private String[] newArtifacts;

    /**
     * Whether to skip the mojo execution.
     */
    @Parameter(defaultValue = "false", property = "revapi.skip")
    private boolean skip;

    /**
     * The severity of found problems at which to break the build. Defaults to API breaking changes.
     * Possible values: nonBreaking, potentiallyBreaking, breaking.
     */
    @Parameter(defaultValue = "breaking", property = "revapi.failSeverity")
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

        BuildTimeReporter reporter = new BuildTimeReporter(failSeverity.asDifferenceSeverity());

        if (oldArtifacts == null || oldArtifacts.length == 0) {
            oldArtifacts = new String[]{Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession)};
        }

        Analyzer analyzer = new Analyzer(analysisConfiguration, analysisConfigurationFiles, oldArtifacts,
            newArtifacts, project, repositorySystem, repositorySystemSession, reporter, Locale.getDefault(), getLog(),
            failOnMissingConfigurationFiles);

        analyzer.analyze();

        if (reporter.hasBreakingProblems()) {
            throw new MojoFailureException(reporter.getAllProblemsMessage());
        } else {
            getLog().info("API checks completed without failures.");
        }
    }
}
