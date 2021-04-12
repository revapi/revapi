/*
 * Copyright 2014-2021 Lukas Krejci
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

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.revapi.API;
import org.revapi.AnalysisResult;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ReportMojo extends AbstractMavenReport {
    /**
     * The JSON or XML configuration of the extensions pipeline. This enables the users easily specify which extensions
     * should be included/excluded in the Revapi analysis pipeline and also to define transformation blocks - a way of
     * grouping transforms together to enable more fine grained control over how differences are transformed.
     *
     * @since 0.11.0
     */
    @Parameter(property = Props.pipelineConfiguration.NAME, defaultValue = Props.pipelineConfiguration.DEFAULT_VALUE)
    protected PlexusConfiguration pipelineConfiguration;

    /**
     * The JSON or XML configuration of various analysis options. The available options depend on what analyzers are
     * present on the plugin classpath through the {@code &lt;dependencies&gt;}. Consult
     * <a href="examples/configuration.html">configuration documentation</a> for more details.
     *
     * <p>
     * These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    @Parameter(property = Props.analysisConfiguration.NAME, defaultValue = Props.analysisConfiguration.DEFAULT_VALUE)
    protected PlexusConfiguration analysisConfiguration;

    /**
     * The list of files containing the configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     *
     * <p>
     * The {@code analysisConfiguration} can override the settings present in the files.
     *
     * <p>
     * The list is either a list of strings or has the following form:
     *
     * <pre>
     * <code>
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
     * </code>
     * </pre>
     *
     * where
     * <ul>
     * <li>{@code path} is the path on the filesystem,</li>
     * <li>{@code resource} is the path to the resource file in one of the artifacts the plugin depends on</li>
     * <li>{@code roots} is optional and specifies the subtrees of the JSON/XML config that should be used for
     * configuration. If not specified, the whole file is taken into account.</li>
     * </ul>
     * Either {@code path} or {@code resource} has to be specified but not both. The {@code configuration/root1} and
     * {@code configuration/root2} are paths to the roots of the configuration inside that JSON/XML config file. This
     * might be used in cases where multiple configurations are stored within a single file and you want to use a
     * particular one.
     *
     * <p>
     * An example of this might be a config file which contains API changes to be ignored in all past versions of a
     * library. The classes to be ignored are specified in a configuration that is specific for each version:
     *
     * <pre>
     * <code>
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
     * </code>
     * </pre>
     */
    @Parameter(property = Props.analysisConfigurationFiles.NAME, defaultValue = Props.analysisConfigurationFiles.DEFAULT_VALUE)
    protected Object[] analysisConfigurationFiles;

    /**
     * Set to false if you want to tolerate files referenced in the {@code analysisConfigurationFiles} missing on the
     * filesystem and therefore not contributing to the analysis configuration.
     *
     * <p>
     * The default is {@code true}, which means that a missing analysis configuration file will fail the build.
     */
    @Parameter(property = Props.failOnMissingConfigurationFiles.NAME, defaultValue = Props.failOnMissingConfigurationFiles.DEFAULT_VALUE)
    protected boolean failOnMissingConfigurationFiles;

    /**
     * A list of dependencies of both the old and new artifact(s) that should be considered part of the old/new API.
     * This is a convenience property if you just need to specify a set of dependencies to promote into the API and that
     * set can be specified in a way common to both old and new APIs. If you need to specify different sets for the old
     * and new, use {@link #oldPromotedDependencies} or {@link #newPromotedDependencies} respectively. If
     * {@link #oldPromotedDependencies} or {@link #newPromotedDependencies} are specified, they override whatever is
     * specified using this property.
     * <p>
     * The individual properties of the dependency (e.g. {@code groupId}, {@code artifactId}, {@code version},
     * {@code type} or {@code classifier}) are matched exactly. If you enclose the value in forward slashes, they are
     * matched as regular expressions instead.
     * <p>
     * E.g. {@code <groupId>com.acme</groupId>} will only match dependencies with that exact {@code groupId}, while
     * <code>&lt;groupId&gt;/com\.acme(\..&#42;)?/&lt;/groupId&gt;</code> will match "com.acme" {@code groupId} or any
     * "sub-groupId" thereof (e.g. "com.acme.utils", etc.) using a regular expression.
     *
     * @since 0.13.6
     */
    @Parameter(property = Props.promotedDependencies.NAME, defaultValue = Props.promotedDependencies.DEFAULT_VALUE)
    protected PromotedDependency[] promotedDependencies;

    /**
     * The coordinates of the old artifacts. Defaults to single artifact with the latest released version of the current
     * project.
     * <p>
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
     * <p>
     * The default value is "RELEASE" meaning that the old version is the last released version of the artifact being
     * built.
     */
    @Parameter(property = Props.oldVersion.NAME, defaultValue = Props.oldVersion.DEFAULT_VALUE)
    protected String oldVersion;

    /**
     * A list of dependencies of the old artifact(s) that should be considered part of the old API.
     *
     * @since 0.13.6
     *
     * @see #promotedDependencies
     */
    @Parameter(property = Props.oldPromotedDependencies.NAME, defaultValue = Props.oldPromotedDependencies.DEFAULT_VALUE)
    protected PromotedDependency[] oldPromotedDependencies;

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
     * A list of dependencies of the new artifact(s) that should be considered part of the new API.
     *
     * @since 0.13.6
     *
     * @see #promotedDependencies
     */
    @Parameter(property = Props.newPromotedDependencies.NAME, defaultValue = Props.newPromotedDependencies.DEFAULT_VALUE)
    protected PromotedDependency[] newPromotedDependencies;

    /**
     * Problems with this or higher severity will be included in the report. Possible values: equivalent, nonBreaking,
     * potentiallyBreaking, breaking.
     *
     * @deprecated use {@link #reportCriticality} instead
     */
    @Deprecated
    @Parameter(property = Props.reportSeverity.NAME, defaultValue = Props.reportSeverity.DEFAULT_VALUE)
    protected FailSeverity reportSeverity;

    /**
     * The minimum criticality of the differences that should be included in the report. This has to be one of the
     * criticalities configured in the pipeline configuration (if the pipeline configuration doesn't define any, the
     * following are the default ones: {@code allowed}, {@code documented}, {@code highlight}, {@code error}).
     *
     * If not defined, the value is derived from {@link #reportSeverity} using the severity-to-criticality mapping
     * (which is again configured in the pipeline configuration. If not defined in the pipeline configuration
     * explicitly, the default mapping is the following: {@code EQUIVALENT} = {@code allowed}, {@code NON_BREAKING} =
     * {@code documented}, {@code POTENTIALLY_BREAKING} = {@code error}, {@code BREAKING} = error.
     */
    @Parameter(property = Props.reportCriticality.NAME)
    protected String reportCriticality;

    /**
     * Whether to skip the mojo execution.
     */
    @Parameter(property = Props.skip.NAME, defaultValue = Props.skip.DEFAULT_VALUE)
    protected boolean skip;

    @Parameter(property = "revapi.outputDirectory", defaultValue = "${project.reporting.outputDirectory}", required = true, readonly = true)
    protected String outputDirectory;

    /**
     * Whether to include the dependencies in the API checks. This is the default thing to do because your API might be
     * exposing classes from the dependencies and thus classes from your dependencies could become part of your API.
     * <p>
     * However, setting this to false might be useful in situations where you have checked your dependencies in another
     * module and don't want do that again. In that case, you might want to configure Revapi to ignore missing classes
     * because it might find the classes from your dependencies as used in your API and would complain that it could not
     * find it. See <a href="http://revapi.org/modules/revapi-java/extensions/java.html">the docs</a>.
     */
    @Parameter(property = Props.checkDependencies.NAME, defaultValue = Props.checkDependencies.DEFAULT_VALUE)
    protected boolean checkDependencies;

    /**
     * When establishing the API classes, Revapi by default also looks through the {@code provided} dependencies. The
     * reason for this is that even though such dependencies do not appear in the transitive dependency set established
     * by maven, they need to be present both on the compilation and runtime classpath of the module. Therefore, the
     * classes in the module are free to expose classes from a provided dependency as API elements.
     *
     * <p>
     * In rare circumstances this is not a desired behavior though. It is undesired if for example the classes from the
     * provided dependency are used only for establishing desired build order or when they are used in some non-standard
     * scenarios during the build and actually not needed at runtime.
     *
     * <p>
     * Note that this property only influences the resolution of provided dependencies of the main artifacts, not the
     * transitively reachable provided dependencies. For those, use the {@link #resolveTransitiveProvidedDependencies}
     * parameter.
     */
    @Parameter(property = Props.resolveProvidedDependencies.NAME, defaultValue = Props.resolveProvidedDependencies.DEFAULT_VALUE)
    protected boolean resolveProvidedDependencies;

    /**
     * In addition to {@link #resolveProvidedDependencies} this property further controls how provided dependencies are
     * resolved. Using this property you can control how the indirect, transitively reachable, provided dependencies are
     * treated. The default is to not consider them, which is almost always the right thing to do. It might be necessary
     * to set this property to {@code true} in the rare circumstances where the API of the main artifacts includes types
     * from such transitively included provided dependencies. Such occurrence will manifest itself by Revapi considering
     * such types as missing (which is by default reported as a potentially breaking change). When you then resolve the
     * transitive provided dependencies (by setting this parameter to true), Revapi will be able to find such types and
     * do a proper analysis of them.
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
     * This parameter is a regular expression pattern that the version string needs to match in order to be considered a
     * {@code RELEASE}.
     */
    @Parameter(property = Props.versionFormat.NAME, defaultValue = Props.versionFormat.DEFAULT_VALUE)
    protected String versionFormat;

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
     * Set this to false if you want to use the goal to generate other kind of output than the default report for the
     * Maven-generated site. You can generate such output by using different reporting extensions (like
     * revapi-reporter-text).
     */
    @Parameter(property = Props.generateSiteReport.NAME, defaultValue = Props.generateSiteReport.DEFAULT_VALUE)
    protected boolean generateSiteReport;

    /**
     * A comma-separated list of extensions (fully-qualified class names thereof) that are not taken into account during
     * API analysis. By default, all extensions that are found on the classpath are used.
     * <p>
     * You can modify this set if you use another extensions that change the found differences in a way that the
     * determined new version would not correspond to what it should be.
     */
    @Parameter(property = Props.disallowedExtensions.NAME, defaultValue = Props.disallowedExtensions.DEFAULT_VALUE)
    protected String disallowedExtensions;

    /**
     * If set to true, the Maven properties will be expanded in the configuration before it is supplied to Revapi. I.e.
     * any {@code ${var}} appearing in the configuration <b>values</b> will be replaced with the value of the
     * {@code var} property as known to Maven. If the property is not defined, the expansion doesn't take place.
     *
     * @since 0.11.6
     */
    @Parameter(property = Props.expandProperties.NAME, defaultValue = Props.expandProperties.DEFAULT_VALUE)
    protected boolean expandProperties;

    private API oldAPI;
    private API newAPI;
    private AnalysisResult analysisResult;

    @Override
    protected String getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override
    public boolean canGenerateReport() {
        return project.getArtifact().getArtifactHandler().isAddedToClasspath();
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        ensureAnalyzed(locale);

        if (skip) {
            getLog().info("Skipping execution");
            return;
        }

        if (oldAPI == null || newAPI == null) {
            getLog().warn("Could not determine the artifacts to compare. If you're comparing the"
                    + " currently built version, have you run the package goal?");
            return;
        }

        if (generateSiteReport) {
            Sink sink = getSink();
            ResourceBundle bundle = getBundle(locale);

            ReportTimeReporter reporter = analysisResult.getExtensions().getFirstExtension(ReportTimeReporter.class,
                    null);

            startReport(sink, bundle);
            reportBody(reporter, oldAPI, newAPI, sink, bundle);
            endReport(sink);
        }
    }

    protected void startReport(Sink sink, ResourceBundle messages) {
        sink.head();
        sink.title();
        sink.text(messages.getString("report.revapi.title"));
        sink.title_();
        sink.head_();

        sink.body();

        sink.section1();
        sink.sectionTitle1();
        sink.rawText(messages.getString("report.revapi.title"));
        sink.sectionTitle1_();
    }

    protected void endReport(Sink sink) {
        sink.section1_();
        sink.body_();
    }

    protected void reportBody(ReportTimeReporter reporterWithResults, API oldAPI, API newAPI, Sink sink,
            ResourceBundle messages) {
        sink.paragraph();
        sink.text(getDescription(messages, oldAPI, newAPI));
        sink.paragraph_();

        reportDifferences(reporterWithResults.reportsBySeverity.get(DifferenceSeverity.BREAKING), sink, messages,
                "report.revapi.changes.breaking");
        reportDifferences(reporterWithResults.reportsBySeverity.get(DifferenceSeverity.POTENTIALLY_BREAKING), sink,
                messages, "report.revapi.changes.potentiallyBreaking");
        reportDifferences(reporterWithResults.reportsBySeverity.get(DifferenceSeverity.NON_BREAKING), sink, messages,
                "report.revapi.changes.nonBreaking");
        reportDifferences(reporterWithResults.reportsBySeverity.get(DifferenceSeverity.EQUIVALENT), sink, messages,
                "report.revapi.changes.equivalent");
    }

    @Override
    public String getOutputName() {
        return "revapi-report";
    }

    @Override
    public String getName(Locale locale) {
        return getBundle(locale).getString("report.revapi.name");
    }

    @Override
    public String getDescription(Locale locale) {
        ensureAnalyzed(locale);

        if (oldAPI == null || newAPI == null) {
            getLog().debug("Was unable to determine the old and new artifacts to compare while determining"
                    + " the report description.");
            return null;
        } else {
            return getDescription(getBundle(locale), oldAPI, newAPI);
        }
    }

    private String getDescription(ResourceBundle messages, API oldAPI, API newAPI) {
        String message = messages.getString("report.revapi.description");
        return MessageFormat.format(message, niceList(oldAPI.getArchives()), niceList(newAPI.getArchives()));
    }

    protected Analyzer prepareAnalyzer(Locale locale) {
        Map<String, Object> contextData = null;
        if (generateSiteReport) {
            contextData = new HashMap<>();
            contextData.put(ReportTimeReporter.MIN_SEVERITY_KEY, reportSeverity.asDifferenceSeverity());
        }

        AnalyzerBuilder.Result res = AnalyzerBuilder.forGavs(this.oldArtifacts, this.newArtifacts)
                .withAlwaysCheckForReleasedVersion(this.alwaysCheckForReleaseVersion)
                .withPipelineConfiguration(PipelineConfigurationParser.parse(this.pipelineConfiguration))
                .withAnalysisConfiguration(this.analysisConfiguration)
                .withAnalysisConfigurationFiles(this.analysisConfigurationFiles)
                .withCheckDependencies(this.checkDependencies)
                .withResolveProvidedDependencies(this.resolveProvidedDependencies)
                .withResolveTransitiveProvidedDependencies(this.resolveTransitiveProvidedDependencies)
                .withDisallowedExtensions(this.disallowedExtensions)
                .withFailOnMissingConfigurationFiles(this.failOnMissingConfigurationFiles)
                .withFailOnUnresolvedArtifacts(this.failOnUnresolvedArtifacts)
                .withFailOnUnresolvedDependencies(this.failOnUnresolvedDependencies).withLocale(locale)
                .withLog(getLog()).withNewVersion(this.newVersion).withOldVersion(this.oldVersion)
                .withProject(this.project).withReporter(generateSiteReport ? ReportTimeReporter.class : null)
                .withRepositorySystem(this.repositorySystem).withRepositorySystemSession(this.repositorySystemSession)
                .withSkip(this.skip).withVersionFormat(this.versionFormat).withContextData(contextData)
                .withExpandProperties(expandProperties)
                .withOldPromotedDependencies(
                        oldPromotedDependencies == null ? promotedDependencies : oldPromotedDependencies)
                .withNewPromotedDependencies(
                        newPromotedDependencies == null ? promotedDependencies : newPromotedDependencies)
                .build();

        if (res.skip) {
            this.skip = true;
        }

        this.oldArtifacts = res.oldArtifacts;
        this.newArtifacts = res.newArtifacts;

        return res.analyzer;
    }

    private void ensureAnalyzed(Locale locale) {
        if (!skip && analysisResult == null) {
            Analyzer analyzer = prepareAnalyzer(locale);
            if (analyzer == null) {
                return;
            }

            try (AnalysisResult res = analyzer.analyze()) {
                res.throwIfFailed();

                oldAPI = analyzer.getResolvedOldApi();
                newAPI = analyzer.getResolvedNewApi();

                analysisResult = res;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to generate report.", e);
            }
        }
    }

    protected ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("revapi-report", locale, this.getClass().getClassLoader());
    }

    protected String niceList(Iterable<? extends Archive> archives) {
        StringBuilder bld = new StringBuilder();

        Iterator<? extends Archive> it = archives.iterator();

        if (it.hasNext()) {
            bld.append(it.next().getName());
        } else {
            return "";
        }

        while (it.hasNext()) {
            bld.append(", ").append(it.next().getName());
        }

        return bld.toString();
    }

    private void reportDifferences(EnumMap<CompatibilityType, List<ReportTimeReporter.DifferenceReport>> diffsPerType,
            Sink sink, ResourceBundle bundle, String typeKey) {

        if (diffsPerType == null || diffsPerType.isEmpty()) {
            return;
        }

        sink.section2();
        sink.sectionTitle2();
        sink.text(bundle.getString(typeKey));
        sink.sectionTitle2_();

        reportDifferences(diffsPerType.get(CompatibilityType.BINARY), sink, bundle,
                "report.revapi.compatibilityType.binary");
        reportDifferences(diffsPerType.get(CompatibilityType.SOURCE), sink, bundle,
                "report.revapi.compatibilityType.source");
        reportDifferences(diffsPerType.get(CompatibilityType.SEMANTIC), sink, bundle,
                "report.revapi.compatibilityType.semantic");
        reportDifferences(diffsPerType.get(CompatibilityType.OTHER), sink, bundle,
                "report.revapi.compatibilityType.other");

        sink.section2_();
    }

    private void reportDifferences(List<ReportTimeReporter.DifferenceReport> diffs, Sink sink, ResourceBundle bundle,
            String typeKey) {

        if (diffs == null || diffs.isEmpty()) {
            return;
        }

        sink.section3();
        sink.sectionTitle3();
        sink.text(bundle.getString(typeKey));
        sink.sectionTitle3_();

        sink.table();

        sink.tableRow();

        sink.tableHeaderCell();
        sink.text(bundle.getString("report.revapi.difference.code"));
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text(bundle.getString("report.revapi.difference.element"));
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text(bundle.getString("report.revapi.difference.description"));
        sink.tableHeaderCell_();

        sink.tableHeaderCell();
        sink.text(bundle.getString("report.revapi.difference.justification"));
        sink.tableHeaderCell_();

        sink.tableRow_();

        diffs.sort((d1, d2) -> {
            String c1 = d1.difference.code;
            String c2 = d2.difference.code;

            int cmp = c1.compareTo(c2);
            if (cmp != 0) {
                return cmp;
            }

            Element e1 = d1.newElement == null ? d1.oldElement : d1.newElement;
            Element e2 = d2.newElement == null ? d2.oldElement : d2.newElement;

            cmp = e1.getClass().getName().compareTo(e2.getClass().getName());
            if (cmp != 0) {
                return cmp;
            }

            return e1.getFullHumanReadableString().compareTo(e2.getFullHumanReadableString());
        });

        for (ReportTimeReporter.DifferenceReport d : diffs) {
            String element = d.oldElement == null ? (d.newElement.getFullHumanReadableString())
                    : d.oldElement.getFullHumanReadableString();

            sink.tableRow();

            sink.tableCell();
            sink.monospaced();
            sink.text(d.difference.code);
            sink.monospaced_();
            sink.tableCell_();

            sink.tableCell();
            sink.monospaced();
            sink.bold();
            sink.text(element);
            sink.bold_();
            sink.monospaced_();

            sink.tableCell();
            sink.text(d.difference.description);
            sink.tableCell_();

            sink.tableCell();
            sink.text(d.difference.justification);
            sink.tableCell_();

            sink.tableRow_();
        }

        sink.table_();

        sink.section3_();
    }
}
