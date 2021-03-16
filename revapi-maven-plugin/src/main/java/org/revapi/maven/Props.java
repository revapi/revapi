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

/**
 * @author Lukas Krejci
 * 
 * @since 0.6.0
 */
final class Props {
    private static final String PREFIX = "revapi.";

    static final class pipelineConfiguration {
        static final String NAME = "";
        static final String DEFAULT_VALUE = "";
    }

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

    static final class resolveProvidedDependencies {
        static final String NAME = PREFIX + "resolveProvidedDependencies";
        static final String DEFAULT_VALUE = "true";
    }

    static final class resolveTransitiveProvidedDependencies {
        static final String NAME = PREFIX + "resolveTransitiveProvidedDependencies";
        static final String DEFAULT_VALUE = "false";
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
        static final String DEFAULT_VALUE = "";
    }

    static final class disallowedExtensionsInVersioning {
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

    static final class convertPomXml {
        static final String NAME = PREFIX + "convertPomXml";
        static final String DEFAULT_VALUE = "true";
    }

    static final class convertAnalysisConfigurationFiles {
        static final String NAME = PREFIX + "convertAnalysisConfigurationFiles";
        static final String DEFAULT_VALUE = "false";
    }

    static final class outputIgnoreSuggestions {
        static final String NAME = PREFIX + "outputIgnoreSuggestions";
        static final String DEFAULT_VALUE = "true";
    }

    static final class ignoreSuggestionsFormat {
        static final String NAME = PREFIX + "ignoreSuggestionsFormat";
        static final String DEFAULT_VALUE = "json";
    }

    static final class outputNonIdentifyingDifferenceInfo {
        static final String NAME = PREFIX + "outputNonIdentifyingDifferenceInfo";
        static final String DEFAULT_VALUE = "true";
    }

    static final class forceVersionUpdate {
        static final String NAME = PREFIX + "forceVersionUpdate";
        static final String DEFAULT_VALUE = "false";
    }

    static final class expandProperties {
        static final String NAME = PREFIX + "expandProperties";
        static final String DEFAULT_VALUE = "false";
    }

    static final class ignoreSuggestionsFile {
        static final String NAME = PREFIX + "ignoreSuggestionsFile";
        static final String DEFAULT_VALUE = "";
    }

    static final class failCriticality {
        static final String NAME = PREFIX + "maximumCriticality";
    }

    static final class reportCriticality {
        static final String NAME = PREFIX + "minimumCriticality";
    }

    static final class preserveNewerVersion {
        static final String NAME = PREFIX + "preserveNewerVersion";
        static final String DEFAULT_VALUE = "true";
    }
}
