package org.revapi.maven;

import org.revapi.ChangeSeverity;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public enum FailSeverity {
    nonBreaking, potentiallyBreaking, breaking;

    public ChangeSeverity toChangeSeverity() {
        switch (this) {
        case nonBreaking:
            return ChangeSeverity.NON_BREAKING;
        case potentiallyBreaking:
            return ChangeSeverity.POTENTIALLY_BREAKING;
        case breaking:
            return ChangeSeverity.BREAKING;
        default:
            throw new AssertionError("FailLevel.toChangeSeverity() not exhaustive.");
        }
    }
}
