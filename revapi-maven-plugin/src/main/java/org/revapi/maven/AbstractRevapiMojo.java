/*
 * Copyright 2014-2018 Lukas Krejci
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.revapi.AnalysisResult;
import org.revapi.Reporter;

/**
 * @author Lukas Krejci
 * @since 0.3.11
 */
abstract class AbstractRevapiMojo extends AbstractMojo {
    /**
     * The JSON or XML configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugin classpath through the {@code &lt;dependencies&gt;}.
     * Consult <a href="examples/configuration.html">configuration documentation</a> for more details.
     *
     * <p>These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    @Parameter(property = Props.analysisConfiguration.NAME, defaultValue = Props.analysisConfiguration.DEFAULT_VALUE)
    protected PlexusConfiguration analysisConfiguration;

    /**
     * Set to false if you want to tolerate files referenced in the {@code analysisConfigurationFiles} missing on the
     * filesystem and therefore not contributing to the analysis configuration.
     *
     * <p>The default is {@code true}, which means that a missing analysis configuration file will fail the build.
     */
    @Parameter(property = Props.failOnMissingConfigurationFiles.NAME, defaultValue = Props.failOnMissingConfigurationFiles.DEFAULT_VALUE)
    protected boolean failOnMissingConfigurationFiles;

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
     *            &lt;resource&gt;path/to/the/file/in/one/of/the/dependencies&lt;/resource&gt;
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
     *     <li>{@code path} is the path on the filesystem,</li>
     *     <li>{@code resource} is the path to the resource file in one of the artifacts the plugin depends on</li>
     *     <li>{@code roots} is optional and specifies the subtrees of the JSON/XML config that should be used for
     *     configuration. If not specified, the whole file is taken into account.</li>
     * </ul>
     * Either {@code path} or {@code resource} has to be specified but not both. The {@code configuration/root1} and
     * {@code configuration/root2} are paths to the roots of the configuration inside that JSON/XML config file. This
     * might be used in cases where multiple configurations are stored within a single file and you want to use
     * a particular one.
     *
     * <p>An example of this might be a config file which contains API changes to be ignored in all past versions of a
     * library. The classes to be ignored are specified in a configuration that is specific for each version:
     * <pre><code>
     *     {
     *         "0.1.0" : [
     *             {
     *                 "extension": "revapi.ignore",
     *                 "configuration": [
     *                     {
     *                         "code" : "java.method.addedToInterface",
     *                         "new" : "method void com.example.MyInterface::newMethod()",
     *                         "justification" : "This interface is not supposed to be implemented by clients."
     *                     },
     *                     ...
     *                 ]
     *             }
     *         ],
     *         "0.2.0" : [
     *             ...
     *         ]
     *     }
     * </code></pre>
     */
    @Parameter(property = Props.analysisConfigurationFiles.NAME, defaultValue = Props.analysisConfigurationFiles.DEFAULT_VALUE)
    protected Object[] analysisConfigurationFiles;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the
     * current project.
     * <p/>
     * If the this property is null, the {@link #oldVersion} property is checked for a value of the old version of the
     * artifact being built.
     *
     * @see #oldVersion
     */
    @Parameter(property = Props.oldArtifacts.NAME, defaultValue = Props.oldArtifacts.DEFAULT_VALUE)
    protected String[] oldArtifacts;

    /**
     * If you don't want to compare a different artifact than the one being built, specifying the just the old version
     * is simpler way of specifying the old artifact.
     * <p/>
     * The default value is "RELEASE" meaning that the old version is the last released version of the artifact being
     * built (either remote or found locally (to account for artifacts installed into the local repo that are not
     * available in some public remote repository)). The version of the compared artifact will be strictly older than
     * the version of the new artifact.
     * <p/>
     * If you specify "LATEST", the old version will be resolved to the newest version available remotely, including
     * snapshots (if found in one of the repositories active in the build). The version of the compared artifact will
     * be either older or equal to the version of the new artifact in this case to account for comparing a locally built
     * snapshot against the latest published snapshot.
     */
    @Parameter(property = Props.oldVersion.NAME, defaultValue = Props.oldVersion.DEFAULT_VALUE)
    protected String oldVersion;

    /**
     * The coordinates of the new artifacts. These are the full GAVs of the artifacts, which means that you can compare
     * different artifacts than the one being built. If you merely want to specify the artifact being built, use
     * {@link #newVersion} property instead.
     */
    @Parameter(property = Props.newArtifacts.NAME, defaultValue = Props.newArtifacts.DEFAULT_VALUE)
    protected String[] newArtifacts;

    /**
     * The new version of the artifact. Defaults to "${project.version}".
     */
    @Parameter(property = Props.newVersion.NAME, defaultValue = Props.newVersion.DEFAULT_VALUE)
    protected String newVersion;

    /**
     * Whether to skip the mojo execution.
     */
    @Parameter(property = Props.skip.NAME, defaultValue = Props.skip.DEFAULT_VALUE)
    protected boolean skip;

    /**
     * The severity of found problems at which to break the build. Defaults to API breaking changes.
     * Possible values: equivalent, nonBreaking, potentiallyBreaking, breaking.
     */
    @Parameter(property = Props.failSeverity.NAME, defaultValue = Props.failSeverity.DEFAULT_VALUE)
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
    @Parameter(property = Props.alwaysCheckForReleaseVersion.NAME, defaultValue = Props.alwaysCheckForReleaseVersion.DEFAULT_VALUE)
    protected boolean alwaysCheckForReleaseVersion;

    /**
     * If true (the default), the maven plugin will fail the build when it finds API problems.
     */
    @Parameter(property = Props.failBuildOnProblemsFound.NAME, defaultValue = Props.failBuildOnProblemsFound.DEFAULT_VALUE)
    protected boolean failBuildOnProblemsFound;

    /**
     * If true, the build will fail if one of the old or new artifacts fails to be resolved. Defaults to false.
     */
    @Parameter(property = Props.failOnUnresolvedArtifacts.NAME, defaultValue = Props.failOnUnresolvedArtifacts.DEFAULT_VALUE)
    protected boolean failOnUnresolvedArtifacts;

    /**
     * If true, the build will fail if some of the dependencies of the old or new artifacts fail to be resolved.
     * Defaults to false.
     */
    @Parameter(property = Props.failOnUnresolvedDependencies.NAME, defaultValue = Props.failOnUnresolvedDependencies.DEFAULT_VALUE)
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
    @Parameter(property = Props.checkDependencies.NAME, defaultValue = Props.checkDependencies.DEFAULT_VALUE)
    protected boolean checkDependencies;

    /**
     * When establishing the API classes, Revapi by default also looks through the {@code provided} dependencies.
     * The reason for this is that even though such dependencies do not appear in the transitive dependency set
     * established by maven, they need to be present both on the compilation and runtime classpath of the module.
     * Therefore, the classes in the module are free to expose classes from a provided dependency as API elements.
     *
     * <p>In rare circumstances this is not a desired behavior though. It is undesired if for example the classes from
     * the provided dependency are used only for establishing desired build order or when they are used in some
     * non-standard scenarios during the build and actually not needed at runtime.
     *
     * <p>Note that this property only influences the resolution of provided dependencies of the main artifacts, not
     * the transitively reachable provided dependencies. For those, use the {@link #resolveTransitiveProvidedDependencies}
     * parameter.
     */
    @Parameter(property = Props.resolveProvidedDependencies.NAME, defaultValue = Props.resolveProvidedDependencies.DEFAULT_VALUE)
    protected boolean resolveProvidedDependencies;

    /**
     * In addition to {@link #resolveProvidedDependencies} this property further controls how provided dependencies
     * are resolved. Using this property you can control how the indirect, transitively reachable, provided dependencies
     * are treated. The default is to not consider them, which is almost always the right thing to do. It might be
     * necessary to set this property to {@code true} in the rare circumstances where the API of the main artifacts
     * includes types from such transitively included provided dependencies. Such occurrence will manifest itself by
     * Revapi considering such types as missing (which is by default reported as a potentially breaking change). When
     * you then resolve the transitive provided dependencies (by setting this parameter to true), Revapi will be able to
     * find such types and do a proper analysis of them.
     */
    @Parameter(property = Props.resolveTransitiveProvidedDependencies.NAME, defaultValue = Props.resolveTransitiveProvidedDependencies.DEFAULT_VALUE)
    protected boolean resolveTransitiveProvidedDependencies;

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
    @Parameter(property = Props.versionFormat.NAME, defaultValue = Props.versionFormat.DEFAULT_VALUE)
    protected String versionFormat;

    /**
     * A comma-separated list of extensions (fully-qualified class names thereof) that are not taken into account during
     * API analysis. By default, all extensions that are found on the classpath are used.
     * <p>
     * You can modify this set if you use another extensions that change the found differences in a way that the
     * determined new version would not correspond to what it should be.
     */
    @Parameter(property = Props.disallowedExtensions.NAME, defaultValue = Props.disallowedExtensions.DEFAULT_VALUE)
    protected String disallowedExtensions;

    protected AnalysisResult analyze(Class<? extends Reporter> reporter, Object... contextDataKeyValues)
            throws MojoExecutionException, MojoFailureException {

        Analyzer analyzer = prepareAnalyzer(project, reporter, toContextData(contextDataKeyValues));
        if (analyzer != null) {
            return analyzer.analyze();
        } else {
            //a null analyzer means the current module doesn't have a jar output
            return AnalysisResult.fakeSuccess();
        }
    }

    protected Analyzer prepareAnalyzer(MavenProject project, Class<? extends Reporter> reporter,
                                       Map<String, Object> contextData) {
        AnalyzerBuilder.Result res = buildAnalyzer(project, reporter, contextData);

        if (res.skip) {
            this.skip = true;
        }

        this.oldArtifacts = res.oldArtifacts;
        this.newArtifacts = res.newArtifacts;

        return res.isOnClasspath ? res.analyzer : null;
    }

    protected Analyzer prepareAnalyzer(MavenProject project, Class<? extends Reporter> reporter,
            Map<String, Object> contextData, Map<String, Object> propertyOverrides) {
        AnalyzerBuilder.Result res = buildAnalyzer(project, reporter, contextData, propertyOverrides);

        if (res.skip) {
            this.skip = true;
        }

        this.oldArtifacts = res.oldArtifacts;
        this.newArtifacts = res.newArtifacts;

        return res.isOnClasspath ? res.analyzer : null;
    }

    AnalyzerBuilder.Result buildAnalyzer(MavenProject project, Class<? extends Reporter> reporter,
                                                   Map<String, Object> contextData) {
        return buildAnalyzer(project, reporter, contextData, Collections.emptyMap());
    }

    AnalyzerBuilder.Result buildAnalyzer(MavenProject project, Class<? extends Reporter> reporter,
            Map<String, Object> contextData, Map<String, Object> propertyOverrides) {
        return AnalyzerBuilder.forGavs(this.oldArtifacts, this.newArtifacts)
                .withAlwaysCheckForReleasedVersion(overrideOrDefault("alwaysCheckForReleaseVersion", this.alwaysCheckForReleaseVersion, propertyOverrides))
                .withAnalysisConfiguration(overrideOrDefault("analysisConfiguration", this.analysisConfiguration, propertyOverrides))
                .withAnalysisConfigurationFiles(overrideOrDefault("analysisConfigurationFiles", this.analysisConfigurationFiles, propertyOverrides))
                .withCheckDependencies(overrideOrDefault("checkDependencies", this.checkDependencies, propertyOverrides))
                .withResolveProvidedDependencies(overrideOrDefault("resolveProvidedDependencies", this.resolveProvidedDependencies, propertyOverrides))
                .withResolveTransitiveProvidedDependencies(overrideOrDefault("resolveTransitiveProvidedDependencies", this.resolveTransitiveProvidedDependencies, propertyOverrides))
                .withDisallowedExtensions(overrideOrDefault("disallowedExtensions", this.disallowedExtensions, propertyOverrides))
                .withFailOnMissingConfigurationFiles(overrideOrDefault("failOnMissingConfigurationFiles", this.failOnMissingConfigurationFiles, propertyOverrides))
                .withFailOnUnresolvedArtifacts(overrideOrDefault("failOnUnresolvedArtifacts", this.failOnUnresolvedArtifacts, propertyOverrides))
                .withFailOnUnresolvedDependencies(overrideOrDefault("failOnUnresolvedDependencies", this.failOnUnresolvedDependencies, propertyOverrides))
                .withLocale(Locale.getDefault())
                .withLog(getLog())
                .withNewVersion(overrideOrDefault("newVersion", this.newVersion, propertyOverrides))
                .withOldVersion(overrideOrDefault("oldVersion", this.oldVersion, propertyOverrides))
                .withProject(project)
                .withReporter(reporter)
                .withRepositorySystem(this.repositorySystem)
                .withRepositorySystemSession(this.repositorySystemSession)
                .withSkip(overrideOrDefault("skip", this.skip, propertyOverrides))
                .withVersionFormat(overrideOrDefault("versionFormat", this.versionFormat, propertyOverrides))
                .withContextData(contextData)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T overrideOrDefault(String propertyName, T defaultValue, Map<String, Object> overrides) {
        Object val = overrides.get(propertyName);
        if (val == null) {
            return defaultValue;
        } else {
            return (T) val;
        }
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
                    Analyzer.getProjectArtifactCoordinates(project, oldVersion)};

            //bail out quickly for POM artifacts (or any other packaging without a file result) - there's nothing we can
            //analyze there
            //only do it here, because oldArtifacts might point to another artifact.
            //if we end up here in this branch, we know we'll be comparing the current artifact with something.
            if (!project.getArtifact().getArtifactHandler().isAddedToClasspath()) {
                return false;
            }
        }

        if (newArtifacts == null || newArtifacts.length == 0) {
            newArtifacts = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, newVersion)};

            //bail out quickly for POM artifacts (or any other packaging without a file result) - there's nothing we can
            //analyze there
            //again, do this check only here, because oldArtifact might point elsewhere. But if we end up here, it
            //means that oldArtifacts would be compared against the current artifact (in some version). Comparing
            //against a POM artifact is always no-op.
            if (!project.getArtifact().getArtifactHandler().isAddedToClasspath()) {
                return false;
            }
        }

        return true;
    }

    private Map<String, Object> toContextData(Object... contextDataKeyValues) {
        if (contextDataKeyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs not balanced.");
        }

        Map<String, Object> ret = new HashMap<>(contextDataKeyValues.length / 2);

        boolean isKey = true;
        String key = null;
        for(Object kv : contextDataKeyValues) {
            if (isKey) {
                if (!(kv instanceof String)) {
                    throw new IllegalArgumentException("Found non-string key.");
                }

                key = (String) kv;
                isKey = false;
            } else {
                ret.put(key, kv);
                isKey = true;
            }
        }

        return ret;
    }
}
