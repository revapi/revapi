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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.regex.Pattern;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.VersionRangeResolutionException;
import org.revapi.API;
import org.revapi.Reporter;
import org.revapi.Revapi;
import org.revapi.maven.utils.ArtifactResolver;

/**
 * Uses the configuration supplied at the top level aggregator project to run analysis on all sub-projects.
 * <p>
 * The artifacts to compare are taken from the configurations of the child projects while the configuration of Revapi
 * and the extensions to use are taken from the aggregator project. The analyses are run in succession using a single
 * instance of Revapi. Therefore you need to configure your reporter(s) to somehow not overwrite the report, but append
 * to it (the {@code revapi-reporter-text} reporter has an {@code append} boolean parameter for this, for example).
 *
 * @author Lukas Krejci
 * @since 0.5.0
 */
@Mojo(name = "report-aggregate", aggregator = true)
public class ReportAggregateMojo extends ReportMojo {

    @Component
    private MavenSession mavenSession;

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

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {
        if (skip) {
            return;
        }

        //we need to resolve the old and new artifacts at the build time of the individual projects, but do the report
        //generation only after all the projects have been built.
        //so, for each project we record the resolved versions and then, when the last project is being built we trigger
        //the full report generation
        storeRunConfig();

        //we need to execute as the very last thing in this reactor
        List<MavenProject> projects = mavenSession.getProjects();
        MavenProject project = mavenSession.getCurrentProject();

        if (!projects.get(projects.size() - 1).equals(project)) {
            getLog().info("Skipping aggregate report generation. Some projects have not been built yet.");
            return;
        }

        //ok, we're at the last project in the reactor.. we have all the information to run the analyses.
        Properties runConfig = loadRunConfig();

        MavenProject topProject = mavenSession.getTopLevelProject();

        getLog().info("Generating aggregate report using the configuration from the top level project: "
                + topProject.getArtifact());

        ResourceBundle messages = getBundle(locale);
        Sink sink = getSink();

        ReportTimeReporter reporter = null;
        if (generateSiteReport) {
            startReport(sink, messages);
            reporter = new ReportTimeReporter(reportSeverity.asDifferenceSeverity());
        }

        try (Analyzer topAnalyzer = prepareAnalyzer(null, topProject, locale, topProject, reporter, runConfig)) {
            Revapi sharedRevapi = topAnalyzer == null ? null : topAnalyzer.getRevapi();

            for (MavenProject p : projects) {
                try (Analyzer projectAnalyzer = prepareAnalyzer(sharedRevapi, p, locale, topProject, null, runConfig)) {
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
        sink.section2();
        sink.sectionTitle2();
        String title = messages.getString("report.revapi.aggregate.subTitle");
        sink.rawText(MessageFormat.format(title, niceList(oldAPI.getArchives()), niceList(newAPI.getArchives())));
        sink.sectionTitle2_();

        super.reportBody(reporterWithResults, oldAPI, newAPI, sink, messages);

        sink.section2_();
    }

    private void storeRunConfig() {
        Plugin revapiPlugin = findRevapi(project);
        if (revapiPlugin == null) {
            return;
        }

        Properties runConfig = loadRunConfig();
        Xpp3Dom pluginConfig = (Xpp3Dom) revapiPlugin.getConfiguration();

        String[] oldArtifacts = getArtifacts(pluginConfig, "oldArtifacts");
        String[] newArtifacts = getArtifacts(pluginConfig, "newArtifacts");
        String oldVersion = getValueOfChild(pluginConfig, "oldVersion");
        if (oldVersion == null) {
            oldVersion = "RELEASE";
        }
        String newVersion = getValueOfChild(pluginConfig, "newVersion");
        if (newVersion == null) {
            newVersion = project.getVersion();
        }

        if (oldArtifacts == null || oldArtifacts.length == 0) {
            //we'd be analyzing a non-file artifact (pom packaging), bail out quickly...
            if (project.getArtifact().getFile() == null) {
                return;
            }
            oldArtifacts = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession, oldVersion)};
        }
        if (newArtifacts == null || newArtifacts.length == 0) {
            //we'd be analyzing a non-file artifact (pom packaging), bail out quickly...
            if (project.getArtifact().getFile() == null) {
                return;
            }
            newArtifacts = new String[]{
                    Analyzer.getProjectArtifactCoordinates(project, repositorySystemSession, newVersion)};
        }
        String versionRegexString = getValueOfChild(pluginConfig, "versionFormat");
        Pattern versionRegex = versionRegexString == null ? null : Pattern.compile(versionRegexString);

        ArtifactResolver resolver = new ArtifactResolver(repositorySystem, repositorySystemSession,
                project.getRemotePluginRepositories());

        final String[] os = oldArtifacts;
        final String[] ns = newArtifacts;

        Arrays.setAll(os, i -> {
            try {
                return Analyzer.resolve(os[i], versionRegex, resolver).toString();
            } catch (VersionRangeResolutionException | ArtifactResolutionException e) {
                throw new IllegalStateException("Could not resolve artifact " + os[i]);
            }
        });

        Arrays.setAll(ns, i -> {
            try {
                return Analyzer.resolve(ns[i], versionRegex, resolver).toString();
            } catch (VersionRangeResolutionException | ArtifactResolutionException e) {
                throw new IllegalStateException("Could not resolve artifact " + ns[i]);
            }
        });

        addValues(runConfig, getKey(project) + ".old", oldArtifacts);
        addValues(runConfig, getKey(project) + ".new", newArtifacts);

        try (Writer wrt = new OutputStreamWriter(new FileOutputStream(getRunConfigFile()), "UTF-8")) {
            runConfig.store(wrt, null);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store resolved artifacts versions file.", e);
        }
    }

    private void addValues(Properties props, String key, String[] values) {
        for (int i = 0; i < values.length; ++i) {
            props.put(key + "." + i, values[i]);
        }
    }

    private String[] readValues(Properties props, String key) {
        ArrayList<String> values = new ArrayList<>(1);
        for(String prop : props.stringPropertyNames()) {
            if (!prop.startsWith(key)) {
                continue;
            }

            if (prop.charAt(key.length()) != '.') {
                continue;
            }

            String idx = prop.length() > key.length() + 1 ? prop.substring(key.length() + 1) : null;
            if (idx == null) {
                continue;
            }

            boolean isNumeric = true;
            for (int i = 0; i < idx.length(); ++i) {
                if (!Character.isDigit(idx.charAt(i))) {
                    isNumeric = false;
                    break;
                }
            }
            if (!isNumeric) {
                continue;
            }

            values.add(props.getProperty(prop));
        }

        return values.toArray(new String[values.size()]);
    }

    private Properties loadRunConfig() {
        if (!getRunConfigFile().exists()) {
            return new Properties();
        }

        try (Reader rdr = new InputStreamReader(new FileInputStream(getRunConfigFile()), "UTF-8")) {
            Properties ret = new Properties();
            ret.load(rdr);
            return ret;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to access resolved artifact versions file.", e);
        }
    }

    private File getRunConfigFile() {
        return new File(mavenSession.getTopLevelProject().getBasedir(),
                "target" + File.separator + "revapi-report-aggregate-resolved-versions.properties");
    }

    private Analyzer prepareAnalyzer(Revapi revapi, MavenProject project, Locale locale, MavenProject configProject,
            Reporter defaultReporter, Properties storedVersions) {

        Plugin runPluginConfig = findRevapi(project);

        Plugin configPluginConfig = findRevapi(configProject);

        if (runPluginConfig == null) {
            return null;
        }

        Xpp3Dom runConfig = (Xpp3Dom) runPluginConfig.getConfiguration();
        Xpp3Dom configConfig = (Xpp3Dom) configPluginConfig.getConfiguration();

        String analysisConfiguration = getValueOfChild(configConfig, "analysisConfiguration");
        Object[] analysisConfigurationFiles = extractConfigFiles(configConfig);

        String[] oldArtifacts = readValues(storedVersions, getKey(project) + ".old");
        String[] newArtifacts = readValues(storedVersions, getKey(project) + ".new");

        if (oldArtifacts.length == 0 || newArtifacts.length == 0) {
            return null;
        }

        boolean failOnMissingConfigurationFiles = false;
        boolean failOnMissingArchives = false;
        boolean failOnMissingSupportArchives = false;
        boolean alwaysUpdate = true;
        boolean resolveDependencies = true;
        String versionRegex = getValueOfChild(runConfig, "versionFormat");

        return revapi == null
                ? new Analyzer(analysisConfiguration, analysisConfigurationFiles, oldArtifacts, newArtifacts,
                project, repositorySystem, repositorySystemSession, defaultReporter, locale, getLog(),
                failOnMissingConfigurationFiles, failOnMissingArchives, failOnMissingSupportArchives, alwaysUpdate,
                resolveDependencies, versionRegex)
                : new Analyzer(analysisConfiguration, analysisConfigurationFiles, oldArtifacts, newArtifacts,
                project, repositorySystem, repositorySystemSession, null, locale, getLog(),
                failOnMissingConfigurationFiles, failOnMissingArchives, failOnMissingSupportArchives, alwaysUpdate,
                resolveDependencies, versionRegex, revapi);
    }

    private Object[] extractConfigFiles(Xpp3Dom pluginConfiguration) {
        Xpp3Dom files = pluginConfiguration.getChild("analysisConfigurationFiles");

        if (files == null) {
            return new Object[0];
        }

        Object[] ret = new Object[files.getChildCount()];

        for (int i = 0; i < ret.length; ++i) {
            Xpp3Dom file = files.getChild(i);
            if (file.getChildCount() == 0) {
                ret[i] = file.getValue();
            } else {
                ConfigurationFile f = new ConfigurationFile();
                Xpp3Dom path = file.getChild("path");
                if (path != null) {
                    f.setPath(path.getValue());
                }

                Xpp3Dom roots = file.getChild("roots");
                if (roots != null) {
                    if (roots.getChildCount() == 0) {
                        f.setRoots(new String[] {roots.getValue()});
                    } else {
                        String[] rs = new String[roots.getChildCount()];
                        for (int j = 0; j < rs.length; ++j) {
                            rs[j] = roots.getChild(i).getValue();
                        }

                        f.setRoots(rs);
                    }
                }

                ret[i] = f;
            }
        }

        return ret;
    }

    private static String[] getArtifacts(Xpp3Dom config, String artifactTag) {
        Xpp3Dom oldArtifactsXml = config.getChild(artifactTag);

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
        Xpp3Dom child = element.getChild(childName);
        return child == null ? null : child.getValue();
    }

    private static Plugin findRevapi(MavenProject project) {
        return project.getBuildPlugins().stream()
                .filter(p -> "org.revapi:revapi-maven-plugin".equals(p.getKey()))
                .findAny().orElse(null);
    }

    private static String getKey(MavenProject p) {
        return p.getGroupId() + ":" + p.getArtifact() + ":" + p.getVersion();
    }
}
