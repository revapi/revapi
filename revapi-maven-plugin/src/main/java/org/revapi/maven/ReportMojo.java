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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.siterenderer.Renderer;
import org.apache.maven.plugin.MojoExecutionException;
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
@Mojo(name = "report", defaultPhase = LifecyclePhase.SITE, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ReportMojo extends AbstractMavenReport {

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
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
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
     * current
     * project.
     *
     * <p>If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    @Parameter(property = "revapi.oldArtifacts")
    private String[] oldArtifacts;

    /**
     * The coordinates of the new artifacts. Defaults to single artifact with the artifacts from the build.
     * If the coordinates are exactly "BUILD" (without quotes) the build artifacts are used.
     */
    @Parameter(defaultValue = AbstractRevapiMojo.BUILD_COORDINATES, property = "revapi.newArtifacts")
    private String[] newArtifacts;

    /**
     * Problems with this or higher severity will be included in the report.
     * Possible values: nonBreaking, potentiallyBreaking, breaking.
     */
    @Parameter(defaultValue = "potentiallyBreaking", property = "revapi.reportSeverity")
    private FailSeverity reportSeverity;

    /**
     * Whether to skip the mojo execution.
     */
    @Parameter
    private boolean skip;

    @Parameter(property = "revapi.outputDirectory", defaultValue = "${project.reporting.outputDirectory}",
        required = true, readonly = true)
    private String outputDirectory;

    @Component
    private Renderer siteRenderer;

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repositorySystemSession;

    /**
     * If true (the default) revapi will always download the information about the latest version from the remote
     * repositories (instead of using locally cached info). This will respect the offline settings.
     */
    @Parameter(defaultValue = "true", property = "revapi.alwaysCheckForReleaseVersion")
    private boolean alwaysCheckForReleaseVersion;

    /**
     * If true, the build will fail if one of the old or new artifacts fails to be resolved. Defaults to false.
     */
    @Parameter(defaultValue = "false", property = "revapi.failOnUnresolvedArtifacts")
    private boolean failOnUnresolvedArtifacts;

    /**
     * If true, the build will fail if some of the dependencies of the old or new artifacts fail to be resolved.
     * Defaults to false.
     */
    @Parameter(defaultValue = "false", property = "revapi.failOnUnresolvedDependencies")
    private boolean failOnUnresolvedDependencies;

    private API oldAPI;
    private API newAPI;
    private ReportTimeReporter reporter;

    @Override
    protected Renderer getSiteRenderer() {
        return siteRenderer;
    }

    @Override
    protected String getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    protected MavenProject getProject() {
        return project;
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        if (skip) {
            return;
        }

        ensureAnalyzed(locale);

        Sink sink = getSink();
        ResourceBundle bundle = getBundle(locale);

        sink.head();
        sink.title();
        sink.text(bundle.getString("report.revapi.title"));
        sink.title_();
        sink.head_();

        sink.body();

        sink.section1();
        sink.sectionTitle1();
        sink.rawText(bundle.getString("report.revapi.title"));
        sink.sectionTitle1_();
        sink.paragraph();
        sink.text(getDescription(locale));
        sink.paragraph_();

        reportDifferences(reporter.reportsBySeverity.get(DifferenceSeverity.BREAKING), sink, bundle,
            "report.revapi.changes.breaking");
        reportDifferences(reporter.reportsBySeverity.get(DifferenceSeverity.POTENTIALLY_BREAKING), sink, bundle,
            "report.revapi.changes.potentiallyBreaking");
        reportDifferences(reporter.reportsBySeverity.get(DifferenceSeverity.NON_BREAKING), sink, bundle,
            "report.revapi.changes.nonBreaking");

        sink.section1_();
        sink.body_();
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
        String message = getBundle(locale).getString("report.revapi.description");
        return MessageFormat.format(message, niceList(oldAPI.getArchives()), niceList(newAPI.getArchives()));
    }

    private void ensureAnalyzed(Locale locale) {
        if (reporter == null) {
            if (oldArtifacts == null || oldArtifacts.length == 0) {
                oldArtifacts = new String[]{Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession,
                        "RELEASE")};
            }

            reporter = new ReportTimeReporter(reportSeverity.asDifferenceSeverity());

            Analyzer analyzer = new Analyzer(analysisConfiguration, analysisConfigurationFiles, oldArtifacts,
                    newArtifacts, project, repositorySystem, repositorySystemSession, reporter, locale, getLog(),
                    failOnMissingConfigurationFiles, failOnUnresolvedArtifacts, failOnUnresolvedDependencies,
                    alwaysCheckForReleaseVersion);

            try {
                analyzer.analyze();

                oldAPI = analyzer.getResolvedOldApi();
                newAPI = analyzer.getResolvedNewApi();
            } catch (MojoExecutionException e) {
                throw new IllegalStateException("Failed to generate report.", e);
            }
        }
    }
    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("revapi-report", locale, this.getClass().getClassLoader());
    }

    private String niceList(Iterable<? extends Archive> archives) {
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

        Collections.sort(diffs, new Comparator<ReportTimeReporter.DifferenceReport>() {
            @Override
            public int compare(ReportTimeReporter.DifferenceReport d1, ReportTimeReporter.DifferenceReport d2) {
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
            }
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
