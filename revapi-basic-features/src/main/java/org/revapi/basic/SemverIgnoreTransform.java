/*
 * Copyright $year Lukas Krejci
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
package org.revapi.basic;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
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

    @Nonnull @Override public Pattern[] getDifferenceCodePatterns() {
        return enabled ? new Pattern[]{Pattern.compile(".*")} : new Pattern[0];
    }

    @Nullable @Override public Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
                                                    @Nonnull Difference difference) {
        if (!enabled || allowedSeverity == null) {
            return difference;
        }

        if (allowedSeverity == DifferenceSeverity.BREAKING) {
            return null;
        } else {
            DifferenceSeverity diffSeverity = getMaxSeverity(difference);
            if (allowedSeverity.ordinal() - diffSeverity.ordinal() >= 0) {
                return null;
            } else {
                return difference;
            }
        }
    }

    private DifferenceSeverity getMaxSeverity(Difference diff) {
        return diff.classification.values().stream().max((d1, d2) -> d1.ordinal() - d2.ordinal()).get();
    }

    @Override public void close() throws Exception {
    }

    @Nullable @Override public String[] getConfigurationRootPaths() {
        return new String[]{"revapi.semver.ignore"};
    }

    @Nullable @Override public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        if ("revapi.semver.ignore".equals(configurationRootPath)) {
            return new InputStreamReader(getClass().getResourceAsStream("/META-INF/semver-ignore-schema.json"),
                    Charset.forName("UTF-8"));
        } else {
            return null;
        }
    }

    @Override public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode node = analysisContext.getConfiguration().get("revapi", "semver", "ignore", "enabled");
        if (hasMultipleElements(analysisContext.getOldApi().getArchives())
                || hasMultipleElements(analysisContext.getNewApi().getArchives())) {
            throw new IllegalArgumentException(
                    "The semver extension doesn't handle changes in multiple archives at once.");
        }
        enabled = node.isDefined() && node.asBoolean();

        if (enabled) {
            Archive oldArchive = analysisContext.getOldApi().getArchives().iterator().next();
            Archive newArchive = analysisContext.getNewApi().getArchives().iterator().next();

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

            if (newVersion.major == 0 && oldVersion.major == 0) {
                if (newVersion.minor > oldVersion.minor) {
                    allowedSeverity = DifferenceSeverity.BREAKING;
                } else if (newVersion.minor == oldVersion.minor && newVersion.patch > oldVersion.patch) {
                    allowedSeverity = DifferenceSeverity.POTENTIALLY_BREAKING;
                } else {
                    allowedSeverity = null;
                }
            } else {
                if (newVersion.major > oldVersion.major) {
                    allowedSeverity = DifferenceSeverity.BREAKING;
                } else if (newVersion.major == oldVersion.major && newVersion.minor > oldVersion.minor) {
                    allowedSeverity = DifferenceSeverity.POTENTIALLY_BREAKING;
                } else {
                    allowedSeverity = null;
                }
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
                Pattern.compile("(\\d+)\\.(\\d+)(?:\\.)?(\\d*)(\\.|-|\\+)?([0-9A-Za-z-.]*)?");

        final int major;
        final int minor;
        final int patch;
        final String sep;
        final String suffix;

        static Version parse(String version) {
            Matcher m = SEMVER_PATTERN.matcher(version);
            if (!m.matches()) {
                throw new IllegalArgumentException("Could not parse the version string '" + version
                        + ". It does not follow semver schema.");
            }

            int major = Integer.valueOf(m.group(1));
            int minor = Integer.valueOf(m.group(2));
            int patch = 0;
            String patchMatch = m.group(3);
            if (patchMatch != null && !patchMatch.isEmpty()) {
                patch = Integer.valueOf(patchMatch);
            }
            String sep = m.group(4);
            String suffix = m.group(5);

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
}
