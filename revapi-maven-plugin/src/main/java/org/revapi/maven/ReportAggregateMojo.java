/*
 * Copyright 2016 Lukas Krejci
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
 *
 */

package org.revapi.maven;

import static org.apache.maven.plugins.annotations.LifecyclePhase.PACKAGE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.SITE;

import java.io.File;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.revapi.API;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.maven.utils.ArtifactResolver;
import org.revapi.maven.utils.ScopeDependencySelector;
import org.revapi.maven.utils.ScopeDependencyTraverser;

/**
 * Uses the configuration supplied at the top level aggregator project to run analysis on all sub-projects.
 * <p>
 * The artifacts to compare are taken from the configurations of the child projects while the configuration of Revapi
 * and the extensions to use are taken from the aggregator project. The analyses are run in succession using a single
 * instance of Revapi. Therefore you need to configure your custom Revapi reporter(s) to somehow not overwrite their
 * reports, but append to it. The default site page generator can do this and the {@code revapi-reporter-text} reporter
 * has an {@code append} boolean parameter for this. If you're using some other reporter, consult its documentation on
 * how to append to a report instead of overwriting it.
 *
 * @author Lukas Krejci
 * @since 0.5.0
 */
@Mojo(name = "report-aggregate", aggregator = true, defaultPhase = SITE)
@Execute(phase = PACKAGE)
public class ReportAggregateMojo extends ReportMojo {

    @Component
    private MavenSession mavenSession;

    @Override public String getOutputName() {
        return "revapi-aggregate-report";
    }

    @Override
    public File getReportOutputDirectory() {
        return new File(mavenSession.getTopLevelProject().getBasedir(), "target/site");
    }

    @Override
    protected String getOutputDirectory() {
        return getReportOutputDirectory().getAbsolutePath();
    }

    @Override
    public void setReportOutputDirectory(File reportOutputDirectory) {
        //this is called by the site plugin to set the output directory. We grandiously ignore what it wants and output
        //in the top level project's site dir.
        super.setReportOutputDirectory(getReportOutputDirectory());
    }

    @Override public String getDescription(Locale locale) {
        return null;
    }

    @Override public boolean canGenerateReport() {
        //aggregate report makes sense only for POM
        return "pom".equals(project.getArtifact().getArtifactHandler().getPackaging());
    }

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        if (skip) {
            return;
        }

        if (!canGenerateReport()) {
            return;
        }

        List<MavenProject> dependents = mavenSession.getProjectDependencyGraph().getDownstreamProjects(project, true);
        Collections.sort(dependents, (a, b) -> {
            String as = a.getArtifact().toString();
            String bs = b.getArtifact().toString();
            return as.compareTo(bs);
        });

        Map<MavenProject, ProjectVersions> projectVersions = dependents.stream().collect(
                Collectors.toMap(Function.identity(), this::getRunConfig));
        projectVersions.put(project, getRunConfig(project));

        ResourceBundle messages = getBundle(locale);
        Sink sink = getSink();

        ReportTimeReporter reporter = null;
        if (generateSiteReport) {
            startReport(sink, messages);
            reporter = new ReportTimeReporter(reportSeverity.asDifferenceSeverity());
        }

        try (Analyzer topAnalyzer = prepareAnalyzer(null, project, locale, reporter,
                projectVersions.get(project))) {
            Revapi sharedRevapi = topAnalyzer == null ? null : topAnalyzer.getRevapi();

            for (MavenProject p : dependents) {
                try (Analyzer projectAnalyzer = prepareAnalyzer(sharedRevapi, p, locale, reporter,
                        projectVersions.get(p))) {
                    if (projectAnalyzer != null) {
                        projectAnalyzer.analyze();

                        if (generateSiteReport) {
                            reportBody(reporter, projectAnalyzer.getResolvedOldApi(),
                                    projectAnalyzer.getResolvedNewApi(), sink, messages);
                        }
                    }
                }
            }

            if (generateSiteReport) {
                endReport(sink);
            }
        } catch (Exception e) {
            throw new MavenReportException("Failed to generate the report.", e);
        }
    }

    @Override
    protected void reportBody(ReportTimeReporter reporterWithResults, API oldAPI, API newAPI, Sink sink,
            ResourceBundle messages) {
        if (oldAPI == null || newAPI == null) {
            return;
        }

        sink.section2();
        sink.sectionTitle2();
        String title = messages.getString("report.revapi.aggregate.subTitle");
        sink.rawText(MessageFormat.format(title, niceList(oldAPI.getArchives()), niceList(newAPI.getArchives())));
        sink.sectionTitle2_();

        super.reportBody(reporterWithResults, oldAPI, newAPI, sink, messages);

        sink.section2_();
    }

    private ProjectVersions getRunConfig(MavenProject project) {
        ProjectVersions ret = new ProjectVersions();
        Plugin revapiPlugin = findRevapi(project);
        if (revapiPlugin == null) {
            return ret;
        }

        Xpp3Dom pluginConfig = (Xpp3Dom) revapiPlugin.getConfiguration();

        String[] oldArtifacts = getArtifacts(pluginConfig, "oldArtifacts");
        String[] newArtifacts = getArtifacts(pluginConfig, "newArtifacts");
        String oldVersion = getValueOfChild(pluginConfig, "oldVersion");
        if (oldVersion == null) {
            oldVersion = System.getProperties().getProperty(Props.oldVersion.NAME, Props.oldVersion.DEFAULT_VALUE);
        }
        String newVersion = getValueOfChild(pluginConfig, "newVersion");
        if (newVersion == null) {
            newVersion = System.getProperties().getProperty(Props.newVersion.NAME, project.getVersion());
        }

        String defaultOldArtifact = Analyzer.getProjectArtifactCoordinates(project, oldVersion);
        String defaultNewArtifact = Analyzer.getProjectArtifactCoordinates(project, newVersion);

        if (oldArtifacts == null || oldArtifacts.length == 0) {
            if (!project.getArtifact().getArtifactHandler().isAddedToClasspath()) {
                return ret;
            }
            oldArtifacts = new String[]{defaultOldArtifact};
        }
        if (newArtifacts == null || newArtifacts.length == 0) {
            if (!project.getArtifact().getArtifactHandler().isAddedToClasspath()) {
                return ret;
            }
            newArtifacts = new String[]{defaultNewArtifact};
        }
        String versionRegexString = getValueOfChild(pluginConfig, "versionFormat");
        Pattern versionRegex = versionRegexString == null ? null : Pattern.compile(versionRegexString);

        DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(repositorySystemSession);
        session.setDependencySelector(new ScopeDependencySelector("compile", "provided"));
        session.setDependencyTraverser(new ScopeDependencyTraverser("compile", "provided"));

        if (alwaysCheckForReleaseVersion) {
            session.setUpdatePolicy(RepositoryPolicy.UPDATE_POLICY_ALWAYS);
        }

        ArtifactResolver resolver = new ArtifactResolver(repositorySystem, session,
                mavenSession.getCurrentProject().getRemoteProjectRepositories());

        Function<String, Artifact> resolve = gav -> {
            try {
                return Analyzer.resolve(project, gav, versionRegex, resolver);
            } catch (VersionRangeResolutionException | ArtifactResolutionException e) {
                getLog().warn("Could not resolve artifact '" + gav + "' with message: " + e.getMessage());
                return null;
            }
        };

        ret.oldGavs = Stream.of(oldArtifacts).map(resolve).filter(f -> f != null).toArray(Artifact[]::new);
        ret.newGavs = Stream.of(newArtifacts).map(resolve).filter(f -> f != null).toArray(Artifact[]::new);

        return ret;
    }

    private Analyzer prepareAnalyzer(Revapi revapi, MavenProject project, Locale locale,
                                     Reporter defaultReporter, ProjectVersions storedVersions) {

        Plugin runPluginConfig = findRevapi(project);

        if (runPluginConfig == null) {
            return null;
        }

        Xpp3Dom runConfig = (Xpp3Dom) runPluginConfig.getConfiguration();

        Artifact[] oldArtifacts = storedVersions.oldGavs;
        Artifact[] newArtifacts = storedVersions.newGavs;

        if (oldArtifacts == null || oldArtifacts.length == 0 || newArtifacts == null || newArtifacts.length == 0) {
            return null;
        }

        boolean failOnMissingConfigurationFiles = false;
        boolean failOnMissingArchives = false;
        boolean failOnMissingSupportArchives = false;
        boolean alwaysUpdate = true;
        boolean resolveDependencies = true;
        String versionRegex = getValueOfChild(runConfig, "versionFormat");

        return revapi == null
                ? new Analyzer(this.analysisConfiguration, this.analysisConfigurationFiles, oldArtifacts, newArtifacts,
                project, repositorySystem, repositorySystemSession, defaultReporter, locale, getLog(),
                failOnMissingConfigurationFiles, failOnMissingArchives, failOnMissingSupportArchives, alwaysUpdate,
                resolveDependencies, versionRegex)
                : new Analyzer(this.analysisConfiguration, this.analysisConfigurationFiles, oldArtifacts, newArtifacts,
                project, repositorySystem, repositorySystemSession, null, locale, getLog(),
                failOnMissingConfigurationFiles, failOnMissingArchives, failOnMissingSupportArchives, alwaysUpdate,
                resolveDependencies, versionRegex, revapi);
    }

    protected static Plugin findRevapi(MavenProject project) {
        return project.getBuildPlugins().stream()
                .filter(p -> "org.revapi:revapi-maven-plugin".equals(p.getKey()))
                .findAny().orElse(null);
    }

    protected static String[] getArtifacts(Xpp3Dom config, String artifactTag) {
        Xpp3Dom oldArtifactsXml = config == null ? null : config.getChild(artifactTag);

        if (oldArtifactsXml == null) {
            return new String[0];
        }

        if (oldArtifactsXml.getChildCount() == 0) {
            String artifact = oldArtifactsXml.getValue();
            return new String[]{artifact};
        } else {
            String[] ret = new String[oldArtifactsXml.getChildCount()];
            for (int i = 0; i < oldArtifactsXml.getChildCount(); ++i) {
                ret[i] = oldArtifactsXml.getChild(i).getValue();
            }

            return ret;
        }
    }

    private static String getValueOfChild(Xpp3Dom element, String childName) {
        Xpp3Dom child = element == null ? null : element.getChild(childName);
        return child == null ? null : child.getValue();
    }

    private static final class ProjectVersions {
        Artifact[] oldGavs;
        Artifact[] newGavs;
    }
}
