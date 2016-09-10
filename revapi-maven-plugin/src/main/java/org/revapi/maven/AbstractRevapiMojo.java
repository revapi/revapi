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

import java.util.Locale;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.3.11
 */
abstract class AbstractRevapiMojo extends AbstractMojo {
    /**
     * The JSON configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     *
     * <p>These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    @Parameter
    protected String analysisConfiguration;

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
    protected Object[] analysisConfigurationFiles;

    /**
     * Set to false if you want to tolerate files referenced in the {@code analysisConfigurationFiles} missing on the
     * filesystem and therefore not contributing to the analysis configuration.
     *
     * <p>The default is {@code true}, which means that a missing analysis configuration file will fail the build.
     */
    @Parameter(property = "revapi.failOnMissingConfigurationFiles", defaultValue = "true")
    protected boolean failOnMissingConfigurationFiles;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the
     * current project.
     * <p/>
     * If the this property is null, the {@link #oldVersion} property is checked for a value of the old version of the
     * artifact being built.
     *
     * @see #oldVersion
     */
    @Parameter(property = "revapi.oldArtifacts")
    protected String[] oldArtifacts;

    /**
     * If you don't want to compare a different artifact than the one being built, specifying the just the old version
     * is simpler way of specifying the old artifact.
     * <p/>
     * The default value is "RELEASE" meaning that the old version is the last released version of the artifact being
     * built.
     */
    @Parameter(defaultValue = "RELEASE", property = "revapi.oldVersion")
    protected String oldVersion;

    /**
     * The coordinates of the new artifacts. These are the full GAVs of the artifacts, which means that you can compare
     * different artifacts than the one being built. If you merely want to specify the artifact being built, use
     * {@link #newVersion} property instead.
     */
    @Parameter(property = "revapi.newArtifacts")
    protected String[] newArtifacts;

    /**
     * The new version of the artifact. Defaults to "${project.version}".
     */
    @Parameter(defaultValue = "${project.version}", property = "revapi.newVersion")
    protected String newVersion;

    /**
     * Whether to skip the mojo execution.
     */
    @Parameter(defaultValue = "false", property = "revapi.skip")
    protected boolean skip;

    /**
     * The severity of found problems at which to break the build. Defaults to API breaking changes.
     * Possible values: nonBreaking, potentiallyBreaking, breaking.
     */
    @Parameter(defaultValue = "breaking", property = "revapi.failSeverity")
    protected FailSeverity failSeverity;

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Component
    protected RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected RepositorySystemSession repositorySystemSession;

    /**
     * If true (the default) revapi will always download the information about the latest version from the remote
     * repositories (instead of using locally cached info). This will respect the offline settings.
     */
    @Parameter(defaultValue = "true", property = "revapi.alwaysCheckForReleaseVersion")
    protected boolean alwaysCheckForReleaseVersion;

    /**
     * If true (the default), the maven plugin will fail the build when it finds API problems.
     */
    @Parameter(defaultValue = "true", property="revapi.failBuildOnProblemsFound")
    protected boolean failBuildOnProblemsFound;

    /**
     * If true, the build will fail if one of the old or new artifacts fails to be resolved. Defaults to false.
     */
    @Parameter(defaultValue = "false", property = "revapi.failOnUnresolvedArtifacts")
    protected boolean failOnUnresolvedArtifacts;

    /**
     * If true, the build will fail if some of the dependencies of the old or new artifacts fail to be resolved.
     * Defaults to false.
     */
    @Parameter(defaultValue = "false", property = "revapi.failOnUnresolvedDependencies")
    protected boolean failOnUnresolvedDependencies;

    /**
     * Whether to include the dependencies in the API checks. This is the default thing to do because your API might
     * be exposing classes from the dependencies and thus classes from your dependencies could become part of your API.
     * <p>
     * However, setting this to false might be useful in situations where you have checked your dependencies in another
     * module and don't want do that again. In that case, you might want to configure Revapi to ignore missing classes
     * because it might find the classes from your dependencies as used in your API and would complain that it could not
     * find it. See <a href="http://revapi.org/modules/revapi-java/extensions/java.html">the docs</a>.
     */
    @Parameter(defaultValue = "true", property = "revapi.checkDependencies")
    protected boolean checkDependencies;

    /**
     * If set, this property demands a format of the version string when the {@link #oldVersion} or {@link #newVersion}
     * parameters are set to {@code RELEASE} or {@code LATEST} special version strings.
     * <p>
     * Because Maven will report the newest non-snapshot version as the latest release, we might end up comparing a
     * {@code .Beta} or other pre-release versions with the new version. This might not be what you want and setting the
     * versionFormat will make sure that a newest version conforming to the version format is used instead of the one
     * resolved by Maven by default.
     * <p>
     * This parameter is a regular expression pattern that the version string needs to match in order to be considered
     * a {@code RELEASE}.
     */
    @Parameter(property = "revapi.versionFormat")
    protected String versionFormat;

    protected void analyze(Reporter reporter) throws MojoExecutionException, MojoFailureException {
        try (Analyzer analyzer = prepareAnalyzer(reporter)) {
            if (analyzer != null) {
                analyzer.analyze();
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to close the API analyzer.", e);
        }
    }

    protected Analyzer prepareAnalyzer(Reporter reporter) {
        if (skip) {
            return null;
        }

        if (!initializeComparisonArtifacts()) {
            return null;
        }

        return new Analyzer(analysisConfiguration, analysisConfigurationFiles, oldArtifacts,
                newArtifacts, project, repositorySystem, repositorySystemSession, reporter, Locale.getDefault(), getLog(),
                failOnMissingConfigurationFiles, failOnUnresolvedArtifacts, failOnUnresolvedDependencies,
                alwaysCheckForReleaseVersion, checkDependencies, versionFormat);
    }

    /**
     * @return true if artifacts are initialized, false if not and the analysis should not proceed
     */
    protected boolean initializeComparisonArtifacts() {
        if (newArtifacts != null && newArtifacts.length == 1 && "BUILD".equals(newArtifacts[0])) {
            getLog().warn("\"BUILD\" coordinates are deprecated. Just leave \"newArtifacts\" undefined and specify" +
                    " \"${project.version}\" as the value for \"newVersion\" (which is the default, so you don't" +
                    " actually have to do that either).");
            oldArtifacts = null;
        }

        if (oldArtifacts == null || oldArtifacts.length == 0) {
            //non-intuitively, we need to initialize the artifacts even if we will not proceed with the analysis itself
            //that's because we need know the versions when figuring out the version modifications -
            //see AbstractVersionModifyingMojo
            oldArtifacts = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession, oldVersion)};

            //bail out quickly for POM artifacts (or any other packaging without a file result) - there's nothing we can
            //analyze there
            //only do it here, because oldArtifacts might point to another artifact.
            //if we end up here in this branch, we know we'll be comparing the current artifact with something.
            if (project.getArtifact().getFile() == null) {
                return false;
            }
        }

        if (newArtifacts == null || newArtifacts.length == 0) {
            newArtifacts = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession, newVersion)};

            //bail out quickly for POM artifacts (or any other packaging without a file result) - there's nothing we can
            //analyze there
            //again, do this check only here, because oldArtifact might point elsewhere. But if we end up here, it
            //means that oldArtifacts would be compared against the current artifact (in some version). Comparing
            //against a POM artifact is always no-op.
            if (project.getArtifact().getFile() == null) {
                return false;
            }
        }

        return true;
    }
}
