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

import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.revapi.API;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ReportMojo extends AbstractMavenReport {

    /**
     * The JSON configuration of various analysis options. The available options depend on what
     * analyzers are present on the plugins classpath through the {@code &lt;dependencies&gt;}.
     *
     * <p>These settings take precedence over the configuration loaded from {@code analysisConfigurationFiles}.
     */
    @Parameter(property = Props.analysisConfiguration.NAME, defaultValue = Props.analysisConfiguration.DEFAULT_VALUE)
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
    @Parameter(property = Props.analysisConfigurationFiles.NAME, defaultValue = Props.analysisConfigurationFiles.DEFAULT_VALUE)
    protected Object[] analysisConfigurationFiles;

    /**
     * Set to false if you want to tolerate files referenced in the {@code analysisConfigurationFiles} missing on the
     * filesystem and therefore not contributing to the analysis configuration.
     *
     * <p>The default is {@code true}, which means that a missing analysis configuration file will fail the build.
     */
    @Parameter(property = Props.failOnMissingConfigurationFiles.NAME, defaultValue = Props.failOnMissingConfigurationFiles.DEFAULT_VALUE)
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
    @Parameter(property = Props.oldArtifacts.NAME, defaultValue = Props.oldArtifacts.DEFAULT_VALUE)
    protected String[] oldArtifacts;

    /**
     * If you don't want to compare a different artifact than the one being built, specifying the just the old version
     * is simpler way of specifying the old artifact.
     * <p/>
     * The default value is "RELEASE" meaning that the old version is the last released version of the artifact being
     * built.
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
     * Problems with this or higher severity will be included in the report.
     * Possible values: equivalent, nonBreaking, potentiallyBreaking, breaking.
     */
    @Parameter(property = Props.reportSeverity.NAME, defaultValue = Props.reportSeverity.DEFAULT_VALUE)
    protected FailSeverity reportSeverity;

    /**
     * Whether to skip the mojo execution.
     */
    @Parameter(property = Props.skip.NAME, defaultValue = Props.skip.DEFAULT_VALUE)
    protected boolean skip;

    @Parameter(property = "revapi.outputDirectory", defaultValue = "${project.reporting.outputDirectory}",
        required = true, readonly = true)
    protected String outputDirectory;

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

    private API oldAPI;
    private API newAPI;
    private ReportTimeReporter reporter;

    @Override
    protected String getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override public boolean canGenerateReport() {
        return project.getArtifact().getArtifactHandler().isAddedToClasspath();
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        ensureAnalyzed(locale);

        if (skip) {
            return;
        }

        if (oldAPI == null || newAPI == null) {
            getLog().warn("Could not determine the artifacts to compare. If you're comparing the" +
                    " currently built version, have you run the package goal?");
            return;
        }

        if (generateSiteReport) {
            Sink sink = getSink();
            ResourceBundle bundle = getBundle(locale);

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
            getLog().debug("Was unable to determine the old and new artifacts to compare while determining" +
                    " the report description.");
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
        if (generateSiteReport) {
            reporter = new ReportTimeReporter(reportSeverity.asDifferenceSeverity());
        }

        AnalyzerBuilder.Result res = AnalyzerBuilder.forGavs(this.oldArtifacts, this.newArtifacts)
                .withAlwaysCheckForReleasedVersion(this.alwaysCheckForReleaseVersion)
                .withAnalysisConfiguration(this.analysisConfiguration)
                .withAnalysisConfigurationFiles(this.analysisConfigurationFiles)
                .withCheckDependencies(this.checkDependencies)
                .withDisallowedExtensions(this.disallowedExtensions)
                .withFailOnMissingConfigurationFiles(this.failOnMissingConfigurationFiles)
                .withFailOnUnresolvedArtifacts(this.failOnUnresolvedArtifacts)
                .withFailOnUnresolvedDependencies(this.failOnUnresolvedDependencies)
                .withLocale(locale)
                .withLog(getLog())
                .withNewVersion(this.newVersion)
                .withOldVersion(this.oldVersion)
                .withProject(this.project)
                .withReporter(reporter)
                .withRepositorySystem(this.repositorySystem)
                .withRepositorySystemSession(this.repositorySystemSession)
                .withSkip(this.skip)
                .withVersionFormat(this.versionFormat)
                .build();

        if (res.skip || !res.isOnClasspath) {
            this.skip = true;
        }

        this.oldArtifacts = res.oldArtifacts;
        this.newArtifacts = res.newArtifacts;

        return res.isOnClasspath ? res.analyzer : null;
    }

    private void ensureAnalyzed(Locale locale) {
        if (!skip && reporter == null) {
            try (Analyzer analyzer = prepareAnalyzer(locale)) {
                if (analyzer == null) {
                    return;
                }

                analyzer.analyze();

                oldAPI = analyzer.getResolvedOldApi();
                newAPI = analyzer.getResolvedNewApi();
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

    private void reportDifferences(
        EnumMap<CompatibilityType, List<ReportTimeReporter.DifferenceReport>> diffsPerType, Sink sink,
        ResourceBundle bundle, String typeKey) {

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
            String element = d.oldElement == null ? (d.newElement.getFullHumanReadableString()) :
                d.oldElement.getFullHumanReadableString();

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

            sink.tableRow_();
        }

        sink.table_();

        sink.section3_();
    }
}
