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

package org.revapi.java;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jboss.dmr.ModelNode;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class AnalysisConfiguration {

    private final MissingClassReporting missingClassReporting;
    private final Set<String> useReportingCodes;
    private final Set<File> oldApiBootstrapClasspath;
    private final Set<File> newApiBootstrapClasspath;
    private final boolean ignoreMissingAnnotations;
    private final boolean deepUseChainAnalysis;
    private final Set<Pattern> classInclusionFilters;
    private final Set<Pattern> classExclusionFilters;
    private final Set<Pattern> packageInclusionFilters;
    private final Set<Pattern> packageExclusionFilters;

    public AnalysisConfiguration(MissingClassReporting missingClassReporting, Set<String> useReportingCodes,
            Set<File> oldApiBootstrapClasspath, Set<File> newApiBootstrapClasspath, boolean ignoreMissingAnnotations,
            boolean deepUseChainAnalysis, Set<Pattern> classInclusionFilters, Set<Pattern> classExclusionFilters,
            Set<Pattern> packageInclusionFilters, Set<Pattern> packageExclusionFilters) {
        this.missingClassReporting = missingClassReporting;
        this.useReportingCodes = useReportingCodes;
        this.oldApiBootstrapClasspath = oldApiBootstrapClasspath;
        this.newApiBootstrapClasspath = newApiBootstrapClasspath;
        this.ignoreMissingAnnotations = ignoreMissingAnnotations;
        this.deepUseChainAnalysis = deepUseChainAnalysis;
        this.classInclusionFilters = classInclusionFilters;
        this.classExclusionFilters = classExclusionFilters;
        this.packageInclusionFilters = packageInclusionFilters;
        this.packageExclusionFilters = packageExclusionFilters;
    }

    public static AnalysisConfiguration fromModel(ModelNode node) {
        MissingClassReporting reporting = readMissingClassReporting(node);
        Set<String> useReportingCodes = readUseReportingCodes(node);
        Set<File> oldApiBootstrapClasspath = readBootstrapClasspath(node, "old");
        Set<File> newApiBootstrapClasspath = readBootstrapClasspath(node, "new");
        boolean ignoreMissingAnnotations = readIgnoreMissingAnnotations(node);
        boolean deepUseChainAnalysis = readDeepUseChainAnalysis(node);

        ModelNode classesRegex = node.get("revapi", "java", "filter", "classes", "regex");
        ModelNode packagesRegex = node.get("revapi", "java", "filter", "packages", "regex");

        Set<Pattern> classInclusionFilters = readFilter(node.get("revapi", "java", "filter", "classes", "include"),
                classesRegex);
        Set<Pattern> classExclusionFilters = readFilter(node.get("revapi", "java", "filter", "classes", "exclude"),
                classesRegex);
        Set<Pattern> packageInclusionFilters = readFilter(node.get("revapi", "java", "filter", "packages", "include"),
                packagesRegex);
        Set<Pattern> packageExclusionFilters = readFilter(node.get("revapi", "java", "filter", "packages", "exclude"),
                packagesRegex);

        return new AnalysisConfiguration(reporting, useReportingCodes, oldApiBootstrapClasspath,
            newApiBootstrapClasspath, ignoreMissingAnnotations, deepUseChainAnalysis, classInclusionFilters,
                classExclusionFilters, packageInclusionFilters, packageExclusionFilters);
    }

    public MissingClassReporting getMissingClassReporting() {
        return missingClassReporting;
    }

    public Set<String> getUseReportingCodes() {
        return useReportingCodes;
    }

    public Set<File> getOldApiBootstrapClasspath() {
        return oldApiBootstrapClasspath;
    }

    public Set<File> getNewApiBootstrapClasspath() {
        return newApiBootstrapClasspath;
    }

    public boolean isIgnoreMissingAnnotations() {
        return ignoreMissingAnnotations;
    }

    public boolean isDeepUseChainAnalysis() {
        return deepUseChainAnalysis;
    }

    public Set<Pattern> getClassExclusionFilters() {
        return classExclusionFilters;
    }

    public Set<Pattern> getClassInclusionFilters() {
        return classInclusionFilters;
    }

    public Set<Pattern> getPackageExclusionFilters() {
        return packageExclusionFilters;
    }

    public Set<Pattern> getPackageInclusionFilters() {
        return packageInclusionFilters;
    }

    private static MissingClassReporting readMissingClassReporting(ModelNode analysisConfig) {
        ModelNode config = analysisConfig.get("revapi", "java", "missing-classes", "behavior");
        if (config.isDefined()) {
            switch (config.asString()) {
            case "report":
                return MissingClassReporting.REPORT;
            case "ignore":
                return MissingClassReporting.IGNORE;
            case "error":
                return MissingClassReporting.ERROR;
            default:
                throw new IllegalArgumentException("Unsupported value of revapi.java.missing-classes.behavior: '" +
                        config.asString() + "'. Only 'report', 'ignore' and 'error' are recognized.");
            }
        }

        return MissingClassReporting.REPORT;
    }

    private static boolean readIgnoreMissingAnnotations(ModelNode analysisConfig) {
        ModelNode config = analysisConfig.get("revapi", "java", "missing-classes", "ignoreMissingAnnotations");
        if (config.isDefined()) {
            return config.asBoolean();
        }

        return false;
    }

    private static Set<String> readUseReportingCodes(ModelNode analysisConfig) {
        Set<String> ret = new HashSet<>();
        ModelNode config = analysisConfig.get("revapi", "java", "reportUsesFor");
        if (config.isDefined()) {
            for (ModelNode code : config.asList()) {
                ret.add(code.asString());
            }
        } else {
            ret.add("java.missing.oldClass");
            ret.add("java.missing.newClass");
            ret.add("java.class.nonPublicPartOfAPI");
            ret.add("java.class.externalClassExposedInAPI");
            ret.add("java.class.externalClassNoLongerExposedInAPI");
        }

        return ret;
    }

    private static Set<File> readBootstrapClasspath(ModelNode analysisConfig, String api) {
        Set<File> ret = new HashSet<>();
        ModelNode config = analysisConfig.get("revapi", "java", "bootstrap-classpath", api);

        String javaHome = getJavaHome(config);

        if (config.isDefined()) {
            ModelNode jars = config.get("jars");
            if (jars.isDefined()) {
                for (ModelNode jar : jars.asList()) {
                    File f = new File(javaHome, jar.asString());

                    if (f.exists() && f.canRead()) {
                        ret.add(f);
                    }
                }
            } else {
                addDefaultBootstrapJars(ret, javaHome);
            }
        } else {
            addDefaultBootstrapJars(ret, javaHome);
        }

        return ret;
    }

    private static void addDefaultBootstrapJars(Set<File> result, String javaHome) {
        addBootstrapJar(result, javaHome, "resources.jar");
        addBootstrapJar(result, javaHome, "rt.jar");
        addBootstrapJar(result, javaHome, "sunrsasign.jar");
        addBootstrapJar(result, javaHome, "jsse.jar");
        addBootstrapJar(result, javaHome, "jce.jar");
        addBootstrapJar(result, javaHome, "charsets.jar");
        addBootstrapJar(result, javaHome, "jfr.jar");
    }

    private static void addBootstrapJar(Set<File> result, String javaHome, String jar) {
        File f = new File(javaHome, jar);
        if (f.exists() && f.canRead()) {
            result.add(f);
        }
    }

    private static String getJavaHome(ModelNode bootstrapClasspathConfiguration) {
        ModelNode javaHomeNode =
            bootstrapClasspathConfiguration.isDefined() ? bootstrapClasspathConfiguration.get("java-home") : null;

        String javaHome = javaHomeNode != null && javaHomeNode.isDefined() ? javaHomeNode.asString() :
            System.getProperty("java.home");

        javaHome += File.separator + File.separator + "lib";

        return javaHome;
    }

    private static boolean readDeepUseChainAnalysis(ModelNode analysisConfig) {
        ModelNode config = analysisConfig.get("revapi", "java", "deepUseChainAnalysis");
        return config.isDefined() && config.asBoolean();
    }

    private static Set<Pattern> readFilter(ModelNode filterNode, ModelNode regexNode) {
        if (!filterNode.isDefined()) {
            return Collections.emptySet();
        }

        boolean isRegex = regexNode.isDefined() && regexNode.asBoolean();

        return filterNode.asList().stream()
                .map(filter -> {
                    if (isRegex) {
                        return Pattern.compile(filter.asString());
                    } else {
                        return Pattern.compile(Pattern.quote(filter.asString()));
                    }
                })
                .collect(Collectors.toSet());
    }

    public enum MissingClassReporting {
        IGNORE, ERROR, REPORT
    }
}
