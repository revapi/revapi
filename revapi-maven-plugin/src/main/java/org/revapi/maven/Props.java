package org.revapi.maven;
/**
 * @author Lukas Krejci
 * @since 0.6.0
 */
final class Props {
    private static final String PREFIX = "revapi.";

    static final class analysisConfiguration {
        static final String NAME = "";
        static final String DEFAULT_VALUE = "";
    }
    static final class analysisConfigurationFiles {
        static final String NAME = PREFIX + "analysisConfigurationFiles";
        static final String DEFAULT_VALUE = "";
    }

    static final class failOnMissingConfigurationFiles {
        static final String NAME = PREFIX + "failOnMissingConfigurationFiles";
        static final String DEFAULT_VALUE = "true";
    }

    static final class oldArtifacts {
        static final String NAME = PREFIX + "oldArtifacts";
        static final String DEFAULT_VALUE = "";
    }

    static final class oldVersion {
        static final String NAME = PREFIX + "oldVersion";
        static final String DEFAULT_VALUE = "RELEASE";
    }

    static final class newArtifacts {
        static final String NAME = PREFIX + "newArtifacts";
        static final String DEFAULT_VALUE = "";
    }

    static final class newVersion {
        static final String NAME = PREFIX + "newVersion";
        static final String DEFAULT_VALUE = "${project.version}";
    }

    static final class skip {
        static final String NAME = PREFIX + "skip";
        static final String DEFAULT_VALUE = "false";
    }

    static final class failSeverity {
        static final String NAME = PREFIX + "failSeverity";
        static final String DEFAULT_VALUE = "potentiallyBreaking";
    }

    static final class alwaysCheckForReleaseVersion {
        static final String NAME = PREFIX + "alwaysCheckForReleaseVersion";
        static final String DEFAULT_VALUE = "true";
    }

    static final class failBuildOnProblemsFound {
        static final String NAME = PREFIX + "failBuildOnProblemsFound";
        static final String DEFAULT_VALUE = "true";
    }

    static final class failOnUnresolvedArtifacts {
        static final String NAME = PREFIX + "failOnUnresolvedArtifacts";
        static final String DEFAULT_VALUE = "false";
    }

    static final class failOnUnresolvedDependencies {
        static final String NAME = PREFIX + "failOnUnresolvedDependencies";
        static final String DEFAULT_VALUE = "false";
    }

    static final class checkDependencies {
        static final String NAME = PREFIX + "checkDependencies";
        static final String DEFAULT_VALUE = "true";
    }

    static final class versionFormat {
        static final String NAME = PREFIX + "versionFormat";
        static final String DEFAULT_VALUE = "";
    }

    static final class singleVersionForAllModules {
        static final String NAME = PREFIX + "singleVersionForAllModules";
        static final String DEFAULT_VALUE = "false";
    }

    static final class disallowedExtensions {
        static final String NAME = PREFIX + "disallowedExtensions";
        static final String DEFAULT_VALUE = "org.revapi.basic.SemverIgnoreTransform";
    }

    static final class generateSiteReport {
        static final String NAME = PREFIX + "generateSiteReport";
        static final String DEFAULT_VALUE = "true";
    }

    static final class reportSeverity {
        static final String NAME = PREFIX + "reportSeverity";
        static final String DEFAULT_VALUE = "potentiallyBreaking";
    }

    static final class useBuildConfiguration {
        static final String NAME = PREFIX + "useBuildConfiguration";
        static final String DEFAULT_VALUE = "false";
    }
}
