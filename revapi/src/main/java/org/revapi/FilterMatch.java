/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi;

import java.util.function.Supplier;

/**
 * A result of a filter.
 */
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

    public boolean toBoolean(boolean undecidedValue) {
        switch (this) {
            case MATCHES:
                return true;
            case DOESNT_MATCH:
                return false;
            case UNDECIDED:
                return undecidedValue;
            default:
                throw new IllegalStateException("Unhandled FilterMatch value: " + this);
        }
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
