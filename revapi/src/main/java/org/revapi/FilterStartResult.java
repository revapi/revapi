/*
 * Copyright 2014-2019 Lukas Krejci
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

/**
 * The result of the element filtering in {@link TreeFilter}. The result tells the analysis whether to let
 * the element pass or not to the next stage but also whether to descend to its children or not.
 */
public final class FilterStartResult {
    private static final FilterStartResult MATCH_DESCEND_NOT_INHERITED = new FilterStartResult(FilterMatch.MATCHES, true, false);
    private static final FilterStartResult MATCH_NOT_DESCEND_NOT_INHERITED = new FilterStartResult(FilterMatch.MATCHES, false, false);
    private static final FilterStartResult DOESNT_MATCH_DESCEND_NOT_INHERITED = new FilterStartResult(FilterMatch.DOESNT_MATCH, true, false);
    private static final FilterStartResult DOESNT_MATCH_NOT_DESCEND_NOT_INHERITED = new FilterStartResult(FilterMatch.DOESNT_MATCH, false, false);
    private static final FilterStartResult UNDECIDED_DESCEND_NOT_INHERITED = new FilterStartResult(FilterMatch.UNDECIDED, true, false);
    private static final FilterStartResult UNDECIDED_NOT_DESCEND_NOT_INHERITED = new FilterStartResult(FilterMatch.UNDECIDED, false, false);
    private static final FilterStartResult MATCH_DESCEND_INHERITED = new FilterStartResult(FilterMatch.MATCHES, true, true);
    private static final FilterStartResult MATCH_NOT_DESCEND_INHERITED = new FilterStartResult(FilterMatch.MATCHES, false, true);
    private static final FilterStartResult DOESNT_MATCH_DESCEND_INHERITED = new FilterStartResult(FilterMatch.DOESNT_MATCH, true, true);
    private static final FilterStartResult DOESNT_MATCH_NOT_DESCEND_INHERITED = new FilterStartResult(FilterMatch.DOESNT_MATCH, false, true);
    private static final FilterStartResult UNDECIDED_DESCEND_INHERITED = new FilterStartResult(FilterMatch.UNDECIDED, true, true);
    private static final FilterStartResult UNDECIDED_NOT_DESCEND_INHERITED = new FilterStartResult(FilterMatch.UNDECIDED, false, true);

    private final FilterMatch match;
    private final boolean descend;
    private final boolean inherited;

    public static FilterStartResult matchAndDescend() {
        return from(FilterMatch.MATCHES, true, false);
    }

    public static FilterStartResult matchAndDescendInherited() {
        return from(FilterMatch.MATCHES, true, true);
    }

    public static FilterStartResult undecidedAndDescend() {
        return from(FilterMatch.UNDECIDED, true, false);
    }

    public static FilterStartResult doesntMatch() {
        return from(FilterMatch.DOESNT_MATCH, false, false);
    }

    public static FilterStartResult doesntMatchAndDescend() {
        return from(FilterMatch.DOESNT_MATCH, true, false);
    }

    public static FilterStartResult direct(FilterMatch match, boolean descend) {
        return from(match, descend, false);
    }

    public static FilterStartResult inherit(FilterStartResult parent) {
        return from(parent.match, parent.descend, true);
    }

    public static FilterStartResult from(FilterMatch match, boolean descend, boolean inherited) {
        switch (match) {
        case DOESNT_MATCH:
            return descend
                    ? (inherited ? DOESNT_MATCH_DESCEND_INHERITED : DOESNT_MATCH_DESCEND_NOT_INHERITED)
                    : (inherited ? DOESNT_MATCH_NOT_DESCEND_INHERITED : DOESNT_MATCH_NOT_DESCEND_NOT_INHERITED);
        case MATCHES:
            return descend
                    ? (inherited ? MATCH_DESCEND_INHERITED : MATCH_DESCEND_NOT_INHERITED)
                    : (inherited ? MATCH_NOT_DESCEND_INHERITED : MATCH_NOT_DESCEND_NOT_INHERITED);
        case UNDECIDED:
            return descend
                    ? (inherited ? UNDECIDED_DESCEND_INHERITED : UNDECIDED_DESCEND_NOT_INHERITED)
                    : (inherited ? UNDECIDED_NOT_DESCEND_INHERITED : UNDECIDED_NOT_DESCEND_NOT_INHERITED);
        default:
            throw new IllegalArgumentException("Unhandled filter match value: " + match);
        }
    }

    public static FilterStartResult from(FilterFinishResult result, boolean descend) {
        return from(result.getMatch(), descend, result.isInherited());
    }

    private FilterStartResult(FilterMatch match, boolean descend, boolean inherited) {
        this.match = match;

        this.descend = descend;
        this.inherited = inherited;
    }

    /**
     * The result of the test
     */
    public FilterMatch getMatch() {
        return match;
    }

    /**
     * Whether the caller should continue with the depth-first search descend down the element tree or not.
     */
    public boolean isDescend() {
        return descend;
    }

    /**
     * Tells whether the result is implicitly inherited from some parent element or if it was explicitly evaluated
     * on some element.
     */
    public boolean isInherited() {
        return inherited;
    }

    public FilterStartResult and(FilterStartResult other) {
        boolean newInherited;
        switch (getMatch()) {
        case MATCHES:
            switch (other.getMatch()) {
            case MATCHES:
                newInherited = isInherited() || other.isInherited();
                break;
            case DOESNT_MATCH:
                newInherited = other.isInherited();
                break;
            case UNDECIDED:
                newInherited = other.isInherited();
                break;
            default:
                throw new IllegalArgumentException("Unhandled match type: " + getMatch());
            }
            break;
        case DOESNT_MATCH:
            newInherited = isInherited();
            break;
        case UNDECIDED:
            newInherited = other.getMatch() == FilterMatch.DOESNT_MATCH ? isInherited() : other.isInherited();
            break;
        default:
            throw new IllegalArgumentException("Unhandled match type: " + getMatch());
        }
        return from(getMatch().and(other.getMatch()), isDescend() && other.isDescend(), newInherited);
    }

    public FilterStartResult and(Iterable<FilterStartResult> others) {
        FilterStartResult res = this;
        for (FilterStartResult other : others) {
            res = res.and(other);
        }
        return res;
    }

    public FilterStartResult or(FilterStartResult other) {
        boolean newInherited;
        switch (getMatch()) {
        case MATCHES:
            switch (other.getMatch()) {
            case MATCHES:
                newInherited = isInherited() || other.isInherited();
                break;
            case DOESNT_MATCH:
                newInherited = isInherited();
                break;
            case UNDECIDED:
                newInherited = isInherited();
                break;
            default:
                throw new IllegalArgumentException("Unhandled match type: " + getMatch());
            }
            break;
        case DOESNT_MATCH:
            newInherited = other.isInherited();
            break;
        case UNDECIDED:
            newInherited = other.getMatch() == FilterMatch.MATCHES ? other.isInherited() : isInherited();
            break;
        default:
            throw new IllegalArgumentException("Unhandled match type: " + getMatch());
        }

        return from(getMatch().or(other.getMatch()), isDescend() || other.isDescend(), newInherited);
    }

    public FilterStartResult or(Iterable<FilterStartResult> others) {
        FilterStartResult res = this;
        for (FilterStartResult other : others) {
            res = res.or(other);
        }
        return res;
    }

    public FilterStartResult negateMatch() {
        return from(getMatch().negate(), isDescend(), isInherited());
    }

    public FilterStartResult withMatch(FilterMatch match) {
        return from(match, isDescend(), isInherited());
    }

    public FilterStartResult withDescend(boolean descend) {
        return from(getMatch(), descend, isInherited());
    }

    public FilterStartResult withInherited(boolean inherited) {
        return from(getMatch(), isDescend(), inherited);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FilterStartResult filterResult = (FilterStartResult) o;

        if (isDescend() != filterResult.isDescend()) {
            return false;
        }
        if (isInherited() != filterResult.isInherited()) {
            return false;
        }
        if (getMatch() != filterResult.getMatch()) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = getMatch().hashCode();
        result = 31 * result + (isDescend() ? 1 : 0);
        result = 31 * result + (isInherited() ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterStartResult{" +
                "state=" + getMatch() +
                ", descend=" + isDescend() +
                ", inherited=" + isInherited() +
                '}';
    }
}
