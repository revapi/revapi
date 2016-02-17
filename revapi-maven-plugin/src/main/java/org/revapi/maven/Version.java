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
package org.revapi.maven;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
final class Version implements Cloneable {
    private static final Pattern SEMVER_PATTERN =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.)?(\\d*)(\\.|-|\\+)?([0-9A-Za-z-.]*)?");

    private int major;
    private int minor;
    private int patch;
    private String suffixSeparator;
    private String suffix;

    static Version parse(String version) {
        Matcher m = SEMVER_PATTERN.matcher(version);
        if (!m.matches()) {
            throw new IllegalArgumentException("Could not update the version string '" + version
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

    public Version() {

    }

    public Version(int major, int minor, int patch, String suffix, String suffixSeparator) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.suffix = suffix;
        this.suffixSeparator = suffixSeparator;
        if (suffix != null && (suffixSeparator == null || suffixSeparator.isEmpty())) {
            this.suffixSeparator = "-";
        }
    }

    public int getMajor() {
        return major;
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public int getMinor() {
        return minor;
    }

    public void setMinor(int minor) {
        this.minor = minor;
    }

    public int getPatch() {
        return patch;
    }

    public void setPatch(int patch) {
        this.patch = patch;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        if (suffix != null && suffixSeparator == null) {
            suffixSeparator = "-";
        }
        this.suffix = suffix;
    }

    public String getSuffixSeparator() {
        return suffixSeparator;
    }

    public void setSuffixSeparator(String suffixSeparator) {
        this.suffixSeparator = suffixSeparator;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (suffix == null ? "" : suffixSeparator)
                + (suffix == null ? "" : suffix);
    }

    @Override public Version clone() {
        try {
            return (Version) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError("Clone not supported on a cloneable class. WFT?", e);
        }
    }
}
