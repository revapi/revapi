/*
 * Copyright 2014-2020 Lukas Krejci
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
package org.revapi.basic;

import static java.util.stream.Collectors.toList;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.DifferenceTransform;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.3.7
 */
public class SemverIgnoreTransform implements DifferenceTransform<Element> {
    private boolean enabled;
    private DifferenceSeverity allowedSeverity;
    private List<String> passThroughDifferences;

    @Nonnull @Override public Pattern[] getDifferenceCodePatterns() {
        return enabled ? new Pattern[]{Pattern.compile(".*")} : new Pattern[0];
    }

    @Nullable @Override public Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
                                                    @Nonnull Difference difference) {
        if (!enabled) {
            return difference;
        }

        if (passThroughDifferences.contains(difference.code)) {
            return difference;
        }

        if (allowedSeverity == null) {
            return asBreaking(difference);
        } else if (allowedSeverity == DifferenceSeverity.BREAKING) {
            return null;
        } else {
            DifferenceSeverity diffSeverity = getMaxSeverity(difference);
            if (allowedSeverity.ordinal() - diffSeverity.ordinal() >= 0) {
                return null;
            } else {
                return asBreaking(difference);
            }
        }
    }

    private Difference asBreaking(Difference d) {
        Difference.Builder bld = Difference.copy(d)
                .addClassification(CompatibilityType.OTHER, DifferenceSeverity.BREAKING)
                .addAttachment("breaksSemanticVersioning", "true");

        if (d.description == null || !d.description.endsWith("(breaks semantic versioning)")) {
            bld.withDescription(d.description == null
                    ? "(breaks semantic versioning)"
                    : (d.description + " (breaks semantic versioning)"));
        }

        if (!d.name.startsWith("Incompatible with the current version: ")) {
            bld.withName("Incompatible with the current version: " + d.name);
        }

        return bld.build();
    }

    private DifferenceSeverity getMaxSeverity(Difference diff) {
        return diff.classification.values().stream().max((d1, d2) -> d1.ordinal() - d2.ordinal()).get();
    }

    @Override public void close() throws Exception {
    }

    @Nullable @Override public String getExtensionId() {
        return "revapi.semver.ignore";
    }

    @Nullable @Override public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/semver-ignore-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode node = analysisContext.getConfiguration();

        enabled = node.get("enabled").isDefined() && node.get("enabled").asBoolean();

        if (enabled) {
            if (hasMultipleElements(analysisContext.getOldApi().getArchives())
                    || hasMultipleElements(analysisContext.getNewApi().getArchives())) {
                throw new IllegalArgumentException(
                        "The semver extension doesn't handle changes in multiple archives at once.");
            }

            Iterator<? extends Archive> oldArchives = analysisContext.getOldApi().getArchives().iterator();
            Iterator<? extends Archive> newArchives = analysisContext.getNewApi().getArchives().iterator();

            if (!oldArchives.hasNext() || !newArchives.hasNext()) {
                enabled = false;
                return;
            }

            Archive oldArchive = oldArchives.next();
            Archive newArchive = newArchives.next();

            if (!(oldArchive instanceof Archive.Versioned)) {
                throw new IllegalArgumentException("Old archive doesn't support extracting the version.");
            }

            if (!(newArchive instanceof Archive.Versioned)) {
                throw new IllegalArgumentException("New archive doesn't support extracting the version.");
            }

            String oldVersionString = ((Archive.Versioned) oldArchive).getVersion();
            String newVersionString = ((Archive.Versioned) newArchive).getVersion();

            Version oldVersion = Version.parse(oldVersionString);
            Version newVersion = Version.parse(newVersionString);

            if (newVersion.major == 0 && oldVersion.major == 0 && !node.get("versionIncreaseAllows").isDefined()) {
                DifferenceSeverity minorChangeAllowed = asSeverity(node.get("versionIncreaseAllows", "minor"), DifferenceSeverity.BREAKING);
                DifferenceSeverity patchVersionAllowed = asSeverity(node.get("versionIncreaseAllows", "patch"), DifferenceSeverity.NON_BREAKING);

                if (newVersion.minor > oldVersion.minor) {
                    allowedSeverity = minorChangeAllowed;
                } else if (newVersion.minor == oldVersion.minor && newVersion.patch > oldVersion.patch) {
                    allowedSeverity = patchVersionAllowed;
                } else {
                    allowedSeverity = null;
                }
            } else {
                DifferenceSeverity majorChangeAllowed = asSeverity(node.get("versionIncreaseAllows", "major"), DifferenceSeverity.BREAKING);
                DifferenceSeverity minorChangeAllowed = asSeverity(node.get("versionIncreaseAllows", "minor"), DifferenceSeverity.NON_BREAKING);
                DifferenceSeverity patchVersionAllowed = asSeverity(node.get("versionIncreaseAllows", "patch"), DifferenceSeverity.EQUIVALENT);

                if (newVersion.major > oldVersion.major) {
                    allowedSeverity = majorChangeAllowed;
                } else if (newVersion.major == oldVersion.major && newVersion.minor > oldVersion.minor) {
                    allowedSeverity = minorChangeAllowed;
                } else {
                    allowedSeverity = patchVersionAllowed;
                }
            }

            passThroughDifferences = Collections.emptyList();
            if (node.get("passThroughDifferences").isDefined()) {
                passThroughDifferences =
                        node.get("passThroughDifferences").asList().stream().map(ModelNode::asString).collect(toList());
            }
        }
    }

    private boolean hasMultipleElements(Iterable<?> it) {
        Iterator<?> i = it.iterator();
        if (!i.hasNext()) {
            return false;
        }

        i.next();

        return i.hasNext();
    }

    private static final class Version {
        private static final Pattern SEMVER_PATTERN =
                Pattern.compile("(\\d+)(\\.(\\d+)(?:\\.)?(\\d*))?(\\.|-|\\+)?([0-9A-Za-z-.]*)?");

        final int major;
        final int minor;
        final int patch;
        final String sep;
        final String suffix;

        static Version parse(String version) {
            Matcher m = SEMVER_PATTERN.matcher(version);
            if (!m.matches()) {
                throw new IllegalArgumentException("Could not parse the version string '" + version
                        + "'. It does not follow the semver schema.");
            }

            int major = Integer.valueOf(m.group(1));
            String minorMatch = m.group(3);
            int minor = minorMatch == null || minorMatch.isEmpty() ? 0 : Integer.valueOf(minorMatch);
            int patch = 0;
            String patchMatch = m.group(4);
            if (patchMatch != null && !patchMatch.isEmpty()) {
                patch = Integer.valueOf(patchMatch);
            }
            String sep = m.group(5);
            String suffix = m.group(6);

            if (sep != null && sep.isEmpty()) {
                sep = null;
            }

            if (suffix != null && suffix.isEmpty()) {
                suffix = null;
            }

            return new Version(major, minor, patch, sep, suffix);
        }

        Version(int major, int minor, int patch, String sep, String suffix) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.sep = sep;
            this.suffix = suffix;
        }
    }

    private static DifferenceSeverity asSeverity(ModelNode configNode, DifferenceSeverity defaultValue) {
        if (configNode == null || !configNode.isDefined()) {
            return defaultValue;
        } else {
            switch (configNode.asString()) {
                case "none":
                    return null;
                case "equivalent":
                    return DifferenceSeverity.EQUIVALENT;
                case "nonBreaking":
                    return DifferenceSeverity.NON_BREAKING;
                case "potentiallyBreaking":
                    return DifferenceSeverity.POTENTIALLY_BREAKING;
                case "breaking":
                    return DifferenceSeverity.BREAKING;
                default:
                    return defaultValue;
            }
        }
    }
}
