package org.revapi.java;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

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

    public AnalysisConfiguration(MissingClassReporting missingClassReporting,
        Set<String> useReportingCodes, Set<File> oldApiBootstrapClasspath,
        Set<File> newApiBootstrapClasspath) {
        this.missingClassReporting = missingClassReporting;
        this.useReportingCodes = useReportingCodes;
        this.oldApiBootstrapClasspath = oldApiBootstrapClasspath;
        this.newApiBootstrapClasspath = newApiBootstrapClasspath;
    }

    public static AnalysisConfiguration fromModel(ModelNode node) {
        MissingClassReporting reporting = readMissingClassReporting(node);
        Set<String> useReportingCodes = readUseReportingCodes(node);
        Set<File> oldApiBootstrapClasspath = readBootstrapClasspath(node, "old");
        Set<File> newApiBootstrapClasspath = readBootstrapClasspath(node, "new");

        return new AnalysisConfiguration(reporting, useReportingCodes, oldApiBootstrapClasspath,
            newApiBootstrapClasspath);
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

    private static MissingClassReporting readMissingClassReporting(ModelNode analysisConfig) {
        ModelNode config = analysisConfig.get("revapi", "java", "missing-classes");
        if (config.isDefined()) {
            switch (config.asString()) {
            case "report":
                return MissingClassReporting.REPORT;
            case "ignore":
                return MissingClassReporting.IGNORE;
            }
        }

        return MissingClassReporting.ERROR;
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

    public static enum MissingClassReporting {
        IGNORE, ERROR, REPORT
    }
}
