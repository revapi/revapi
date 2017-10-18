package org.revapi;

import java.util.function.Supplier;

public enum FilterMatch {
    /**
     * The element matches the filter. I.e. it should proceed from the filter further.
     */
    MATCHES,

    /**
     * The element doesn't match the filter. I.e. it should NOT proceed and the filter should "stop" it.
     */
    DOESNT_MATCH,

    /**
     * The decision to let the element pass cannot be made at the moment and should be deferred until all other
     * elements are processed.
     */
    UNDECIDED;

    public static FilterMatch fromBoolean(boolean value) {
        return value ? MATCHES : DOESNT_MATCH;
    }

    public FilterMatch and(FilterMatch other) {
        switch (this) {
            case MATCHES:
                return other;
            case DOESNT_MATCH:
                return this;
            case UNDECIDED:
                return other == DOESNT_MATCH ? other : this;
        }

        throw new IllegalStateException("Unhandled FilterMatch: " + this);
    }

    public FilterMatch and(Supplier<FilterMatch> other) {
        switch (this) {
            case MATCHES:
                return other.get();
            case DOESNT_MATCH:
                return this;
            case UNDECIDED:
                FilterMatch res = other.get();
                return res == DOESNT_MATCH ? res : this;
        }

        throw new IllegalStateException("Unhandled FilterMatch: " + this);
    }

    public FilterMatch or(FilterMatch other) {
        switch (this) {
            case MATCHES:
                return this;
            case DOESNT_MATCH:
                return other;
            case UNDECIDED:
                return other == MATCHES ? other : this;
        }

        throw new IllegalStateException("Unhandled FilterMatch: " + this);
    }

    public FilterMatch or(Supplier<FilterMatch> other) {
        switch (this) {
            case MATCHES:
                return this;
            case DOESNT_MATCH:
                return other.get();
            case UNDECIDED:
                FilterMatch res = other.get();
                return res == MATCHES ? res : this;
        }

        throw new IllegalStateException("Unhandled FilterMatch: " + this);
    }

    public FilterMatch negate() {
        switch (this) {
            case MATCHES:
                return DOESNT_MATCH;
            case DOESNT_MATCH:
                return MATCHES;
            case UNDECIDED:
                return UNDECIDED;
        }

        throw new IllegalStateException("Unhandled FilterMatch: " + this);
    }
}
