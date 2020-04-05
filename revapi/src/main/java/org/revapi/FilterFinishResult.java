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
 * A result of a finished filtering (see {@link TreeFilter#finish(Element)}.
 */
public class FilterFinishResult {
    private static final FilterFinishResult MATCH_NOT_INHERITED = new FilterFinishResult(FilterMatch.MATCHES, false);
    private static final FilterFinishResult DOESNT_MATCH_NOT_INHERITED = new FilterFinishResult(FilterMatch.DOESNT_MATCH, false);
    private static final FilterFinishResult UNDECIDED_NOT_INHERITED = new FilterFinishResult(FilterMatch.UNDECIDED, false);
    private static final FilterFinishResult MATCH_INHERITED = new FilterFinishResult(FilterMatch.MATCHES, true);
    private static final FilterFinishResult DOESNT_MATCH_INHERITED = new FilterFinishResult(FilterMatch.DOESNT_MATCH, true);
    private static final FilterFinishResult UNDECIDED_INHERITED = new FilterFinishResult(FilterMatch.UNDECIDED, true);

    private final FilterMatch match;
    private final boolean inherited;

    /**
     * @return produces a result signifying that the filter didn't match.
     */
    public static FilterFinishResult doesntMatch() {
        return from(FilterMatch.DOESNT_MATCH, false);
    }

    /**
     * @return produces a result signifying that the filter matched.
     */
    public static FilterFinishResult matches() {
        return from(FilterMatch.MATCHES, false);
    }

    /**
     * The return filter finish result will have the result of the match and will not be inherited, meaning that it
     * was intended directly for the elements the filter was processed upon.
     * @param match the result of the filter
     * @return the filter finish result
     */
    public static FilterFinishResult direct(FilterMatch match) {
        return from(match, false);
    }

    /**
     * Produces a filter finish result that indicates it was inherited from the provided result.
     *
     * @param parent the result to inherit from
     * @return an inherited filter finish result
     */
    public static FilterFinishResult inherit(FilterFinishResult parent) {
        return from(parent.match, true);
    }

    /**
     * A factory method for filter finish results.
     * @param match the result of the filtering
     * @param inherited whether the finish result is inherited or explicit
     * @return the filter finish result
     */
    public static FilterFinishResult from(FilterMatch match, boolean inherited) {
        switch (match) {
        case DOESNT_MATCH:
            return inherited ? DOESNT_MATCH_INHERITED : DOESNT_MATCH_NOT_INHERITED;
        case MATCHES:
            return inherited ? MATCH_INHERITED : MATCH_NOT_INHERITED;
        case UNDECIDED:
            return inherited ? UNDECIDED_INHERITED : UNDECIDED_NOT_INHERITED;
        default:
            throw new IllegalArgumentException("Unhandled filter match value: " + match);
        }
    }

    /**
     * Converts the provided start result into a finish result.
     * @param startResult the start result to convert
     * @return the converted finish result
     */
    public static FilterFinishResult from(FilterStartResult startResult) {
        return from(startResult.getMatch(), startResult.isInherited());
    }

    private FilterFinishResult(FilterMatch match, boolean inherited) {
        this.match = match;
        this.inherited = inherited;
    }

    /**
     * The result of the test
     */
    public FilterMatch getMatch() {
        return match;
    }

    /**
     * Tells whether the result is implicitly inherited from some parent element or if it was explicitly evaluated
     * on some element.
     */
    public boolean isInherited() {
        return inherited;
    }

    public FilterFinishResult and(FilterFinishResult other) {
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
        return from(getMatch().and(other.getMatch()), newInherited);
    }

    public FilterFinishResult and(Iterable<FilterFinishResult> others) {
        FilterFinishResult res = this;
        for (FilterFinishResult other : others) {
            res = res.and(other);
        }
        return res;
    }

    public FilterFinishResult or(FilterFinishResult other) {
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

        return from(getMatch().or(other.getMatch()), newInherited);
    }

    public FilterFinishResult or(Iterable<FilterFinishResult> others) {
        FilterFinishResult res = this;
        for (FilterFinishResult other : others) {
            res = res.or(other);
        }
        return res;
    }

    public FilterFinishResult negateMatch() {
        return from(getMatch().negate(), isInherited());
    }

    public FilterFinishResult withMatch(FilterMatch match) {
        return from(match, isInherited());
    }

    public FilterFinishResult withInherited(boolean inherited) {
        return from(getMatch(), inherited);
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
        result = 31 * result + (isInherited() ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterEndResult{" +
                "match=" + getMatch() +
                ", inherited=" + isInherited() +
                '}';
    }
}
