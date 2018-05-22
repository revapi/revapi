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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.ximpleware.AutoPilot;
import com.ximpleware.ModifyException;
import com.ximpleware.NavException;
import com.ximpleware.TranscodeException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;
import com.ximpleware.XMLModifier;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.revapi.AnalysisResult;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
class AbstractVersionModifyingMojo extends AbstractRevapiMojo {

    @Component
    protected MavenSession mavenSession;

    /**
     * Set to true if all the projects in the current build should share a single version (the default is false).
     * This is useful if your distribution consists of several modules that make up one logical unit that is always
     * versioned the same.
     * <p>
     * In this case all the projects in the current build are API-checked and the version is determined by the
     * "biggest" API change found in all the projects. I.e. if just a single module breaks API then all of the
     * modules will get the major version incremented.
     */
    @Parameter(property = Props.singleVersionForAllModules.NAME, defaultValue = Props.singleVersionForAllModules.DEFAULT_VALUE)
    private boolean singleVersionForAllModules;

    /**
     * A comma-separated list of extensions (fully-qualified class names thereof) that are not taken into account during
     * API analysis for versioning purposes. By default, only the semver-ignore transform is not taken into account so
     * that it does not interfere with the purpose of modifying the version based on the semver rules.
     * <p>
     * You can modify this set if you use another extensions that change the found differences in a way that the
     * determined new version would not correspond to what it should be.
     */
    @Parameter(property = Props.disallowedExtensionsInVersioning.NAME, defaultValue = Props.disallowedExtensionsInVersioning.DEFAULT_VALUE)
    protected String disallowedExtensions;

    private boolean preserveSuffix;
    private String replacementSuffix;

    public boolean isPreserveSuffix() {
        return preserveSuffix;
    }

    void setPreserveSuffix(boolean preserveSuffix) {
        this.preserveSuffix = preserveSuffix;
    }

    public String getReplacementSuffix() {
        return replacementSuffix;
    }

    void setReplacementSuffix(String replacementSuffix) {
        this.replacementSuffix = replacementSuffix;
    }

    public boolean isSingleVersionForAllModules() {
        return singleVersionForAllModules;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            return;
        }
        AnalysisResults analysisResults;

        if (!initializeComparisonArtifacts()) {
            //we've got non-file artifacts, for which there is no reason to run analysis
            DefaultArtifact oldArtifact = new DefaultArtifact(oldArtifacts[0]);
            analysisResults = new AnalysisResults(ApiChangeLevel.NO_CHANGE, oldArtifact.getVersion());
        } else {
            analysisResults = analyzeProject(project);
        }

        if (analysisResults == null) {
            return;
        }

        ApiChangeLevel changeLevel = analysisResults.apiChangeLevel;

        if (singleVersionForAllModules) {
            File changesFile = getChangesFile();

            try (PrintWriter out = new PrintWriter(new FileOutputStream(changesFile, true))) {
                out.println(project.getArtifact().toString() + "=" + changeLevel + "," + analysisResults.baseVersion);
            } catch (IOException e) {
                throw new MojoExecutionException("Failure while updating the changes tracking file.", e);
            }
        } else {
            Version v = nextVersion(analysisResults.baseVersion, changeLevel);
            updateProjectVersion(project, v);
        }

        if (singleVersionForAllModules
                && project.equals(mavenSession.getProjects().get(mavenSession.getProjects().size() - 1))) {

            try (BufferedReader rdr = new BufferedReader(new FileReader(getChangesFile()))) {

                Map<String, AnalysisResults> projectChanges = new HashMap<>();

                String line;
                while ((line = rdr.readLine()) != null) {
                    int equalsIdx = line.indexOf('=');
                    String projectGav = line.substring(0, equalsIdx);
                    String changeAndBaseVersion = line.substring(equalsIdx + 1);
                    int commaIdx = changeAndBaseVersion.indexOf(',');
                    String change = changeAndBaseVersion.substring(0, commaIdx);

                    String baseVersion = changeAndBaseVersion.substring(commaIdx + 1);
                    changeLevel = ApiChangeLevel.valueOf(change);

                    projectChanges.put(projectGav, new AnalysisResults(changeLevel, baseVersion));
                }

                //establish the tree hierarchy of the projects
                Set<MavenProject> roots = new HashSet<>();
                Map<MavenProject, Set<MavenProject>> children = new HashMap<>();
                Deque<MavenProject> unprocessed = new ArrayDeque<>(mavenSession.getProjects());

                while (!unprocessed.isEmpty()) {
                    MavenProject pr = unprocessed.pop();
                    if (!projectChanges.containsKey(pr.getArtifact().toString())) {
                        continue;
                    }
                    MavenProject pa = pr.getParent();
                    if (roots.contains(pa)) {
                        roots.remove(pr);
                        AnalysisResults paR = projectChanges.get(pa.getArtifact().toString());
                        AnalysisResults prR = projectChanges.get(pr.getArtifact().toString());
                        if (prR.apiChangeLevel.ordinal() > paR.apiChangeLevel.ordinal()) {
                            paR.apiChangeLevel = prR.apiChangeLevel;
                        }
                        children.get(pa).add(pr);
                    } else {
                        roots.add(pr);
                    }

                    children.put(pr, new HashSet<MavenProject>());
                }

                Iterator<MavenProject> it = roots.iterator();
                while (it.hasNext()) {
                    Deque<MavenProject> tree = new ArrayDeque<>();
                    MavenProject p = it.next();
                    tree.add(p);
                    it.remove();

                    AnalysisResults results = projectChanges.get(p.getArtifact().toString());
                    Version v = nextVersion(results.baseVersion, results.apiChangeLevel);

                    while (!tree.isEmpty()) {
                        MavenProject current = tree.pop();
                        updateProjectVersion(current, v);
                        Set<MavenProject> c = children.get(current);
                        if (c != null) {
                            for (MavenProject cp : c) {
                                updateProjectParentVersion(cp, v);
                            }
                            tree.addAll(c);
                        }
                    }
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failure while reading the changes tracking file.", e);
            }
        }
    }

    private File getChangesFile() throws MojoExecutionException {
        File targetDir = new File(mavenSession.getExecutionRootDirectory(), "target");
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new MojoExecutionException("Failed to create the target directory: " + targetDir);
        }

        File updateVersionsDir = new File(targetDir, "revapi-update-versions");
        if (!updateVersionsDir.exists() && !updateVersionsDir.mkdirs()) {
            throw new MojoExecutionException("Failed to create the revapi-update-versions directory: " + targetDir);
        }

        return new File(updateVersionsDir, "per-project.changes");
    }

    void updateProjectVersion(MavenProject project, Version version) throws MojoExecutionException {
        try {

            int indentationSize;
            try (BufferedReader rdr = new BufferedReader(new FileReader(project.getFile()))) {
                indentationSize = XmlUtil.estimateIndentationSize(rdr);
            }

            VTDGen gen = new VTDGen();
            gen.enableIgnoredWhiteSpace(true);
            gen.parseFile(project.getFile().getAbsolutePath(), true);

            VTDNav nav = gen.getNav();
            AutoPilot ap = new AutoPilot(nav);
            XMLModifier mod = new XMLModifier(nav);

            ap.selectXPath("/project/version");
            if (ap.evalXPath() != -1) {
                //found the version
                int textPos = nav.getText();
                mod.updateToken(textPos, version.toString());
            } else {
                //place the version after the artifactId
                ap.selectXPath("/project/artifactId");
                if (ap.evalXPath() == -1) {
                    throw new MojoExecutionException("Failed to find artifactId element in the pom.xml of project "
                            + project.getArtifact().getId() + " when trying to insert a version tag after it.");
                } else {
                    StringBuilder versionLine = new StringBuilder();
                    versionLine.append('\n');
                    for (int i = 0; i < indentationSize; ++i) {
                        versionLine.append(' ');
                    }
                    versionLine.append("<version>").append(version.toString()).append("</version>");
                    mod.insertAfterElement(versionLine.toString());
                }
            }

            try (OutputStream out = new FileOutputStream(project.getFile())) {
                mod.output(out);
            }
        } catch (IOException | ModifyException | NavException | XPathParseException | XPathEvalException | TranscodeException e) {
            throw new MojoExecutionException("Failed to update the version of project " + project, e);
        }
    }

    void updateProjectParentVersion(MavenProject project, Version version) throws MojoExecutionException {
        try {
            VTDGen gen = new VTDGen();
            gen.enableIgnoredWhiteSpace(true);
            gen.parseFile(project.getFile().getAbsolutePath(), true);

            VTDNav nav = gen.getNav();
            AutoPilot ap = new AutoPilot(nav);
            ap.selectXPath("namespace-uri(.)");
            String ns = ap.evalXPathToString();

            nav.toElementNS(VTDNav.FIRST_CHILD, ns, "parent");
            nav.toElementNS(VTDNav.FIRST_CHILD, ns, "version");
            int pos = nav.getText();

            XMLModifier mod = new XMLModifier(nav);
            mod.updateToken(pos, version.toString());

            try (OutputStream out = new FileOutputStream(project.getFile())) {
                mod.output(out);
            }
        } catch (IOException | ModifyException | NavException | XPathParseException | TranscodeException e) {
            throw new MojoExecutionException("Failed to update the parent version of project " + project, e);
        }
    }

    private Version nextVersion(String baseVersion, ApiChangeLevel changeLevel) {
        Version v = Version.parse(project.getVersion());

        boolean isDev = v.getMajor() == 0;

        switch (changeLevel) {
            case NO_CHANGE:
                break;
            case NON_BREAKING_CHANGES:
                if (isDev) {
                    v.setPatch(v.getPatch() + 1);
                } else {
                    v.setMinor(v.getMinor() + 1);
                    v.setPatch(0);
                }
                break;
            case BREAKING_CHANGES:
                if (isDev) {
                    v.setMinor(v.getMinor() + 1);
                    v.setPatch(0);
                } else {
                    v.setMajor(v.getMajor() + 1);
                    v.setMinor(0);
                    v.setPatch(0);
                }
                break;
            default:
                throw new IllegalArgumentException("Unhandled API change level: " + changeLevel);
        }

        if (replacementSuffix != null) {
            String sep = replacementSuffix.substring(0, 1);
            String suffix = replacementSuffix.substring(1);
            v.setSuffixSeparator(sep);
            v.setSuffix(suffix);
        } else if (!preserveSuffix) {
            v.setSuffix(null);
            v.setSuffixSeparator(null);
        }

        return v;
    }

    private AnalysisResults analyzeProject(MavenProject project) throws MojoExecutionException {
        Analyzer analyzer = prepareAnalyzer(project, ApiBreakageHintingReporter.class, Collections.emptyMap());

        try {
            analyzer.resolveArtifacts();

            if (analyzer.getResolvedOldApi() == null) {
                return null;
            } else {
                try (AnalysisResult res = analyzer.analyze()) {
                    res.throwIfFailed();

                    ApiBreakageHintingReporter reporter =
                            res.getExtensions().getFirstExtension(ApiBreakageHintingReporter.class, null);

                    ApiChangeLevel level = reporter.getChangeLevel();
                    String baseVersion = ((MavenArchive) analyzer.getResolvedOldApi().getArchives().iterator().next())
                            .getVersion();

                    return new AnalysisResults(level, baseVersion);
                }

            }
        } catch (Exception e) {
            throw new MojoExecutionException("Analysis failure", e);
        }
    }

    private static final class AnalysisResults {
        ApiChangeLevel apiChangeLevel;
        final String baseVersion;

        AnalysisResults(ApiChangeLevel apiChangeLevel, String baseVersion) {
            this.apiChangeLevel = apiChangeLevel;
            this.baseVersion = baseVersion;
        }
    }

    private Map<String, Object> getPropertyOverrideMap() {
        Map<String, Object> ret = new HashMap<>(1);
        ret.put("disallowedExtensions", disallowedExtensions);
        return ret;
    }
}
