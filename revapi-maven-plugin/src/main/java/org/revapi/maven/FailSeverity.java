package org.revapi.maven;

import org.revapi.DifferenceSeverity;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public enum FailSeverity {
    nonBreaking, potentiallyBreaking, breaking;

    public DifferenceSeverity toChangeSeverity() {
        switch (this) {
        case nonBreaking:
            return DifferenceSeverity.NON_BREAKING;
        case potentiallyBreaking:
            return DifferenceSeverity.POTENTIALLY_BREAKING;
        case breaking:
            return DifferenceSeverity.BREAKING;
        default:
            throw new AssertionError("FailLevel.toChangeSeverity() not exhaustive.");
        }
    }
}
