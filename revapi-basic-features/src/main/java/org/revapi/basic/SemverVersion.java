package org.revapi.basic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A helper class to parse the version strings according to the Semver spec.
 */
final class SemverVersion {
    private static final Pattern SEMVER_PATTERN = Pattern.compile("(\\d+)(\\.(\\d+)(?:\\.)?(\\d*))?([.\\-+])?(.*)?");
    private static final Pattern STRICT_SEMVER_PATTERN = Pattern
            .compile("(\\d+)(\\.(\\d+)(?:\\.)?(\\d*))?(\\.|-|\\+)?([0-9A-Za-z-.]*)?");

    final int major;
    final int minor;
    final int patch;
    final String suffixSeparator;
    final String suffix;

    static SemverVersion parse(String version, boolean strict) {
        Matcher m = (strict ? STRICT_SEMVER_PATTERN : SEMVER_PATTERN).matcher(version);
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "Could not parse the version string '" + version + "'. It does not follow the semver schema.");
        }

        int major = Integer.parseInt(m.group(1));
        String minorMatch = m.group(3);
        int minor = minorMatch == null || minorMatch.isEmpty() ? 0 : Integer.parseInt(minorMatch);
        String patchMatch = m.group(4);
        int patch = patchMatch == null || patchMatch.isEmpty() ? 0 : Integer.parseInt(patchMatch);

        String sep = m.group(5);
        String suffix = m.group(6);

        if (sep != null && sep.isEmpty()) {
            sep = null;
        }

        if (suffix != null && suffix.isEmpty()) {
            suffix = null;
        }

        return new SemverVersion(major, minor, patch, sep, suffix);
    }

    SemverVersion(int major, int minor, int patch, String sep, String suffix) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.suffixSeparator = sep;
        this.suffix = suffix;
    }
}
