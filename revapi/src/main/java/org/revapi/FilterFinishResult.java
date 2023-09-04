/*
 * Copyright 2014-2023 Lukas Krejci
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

import static org.revapi.Ternary.FALSE;
import static org.revapi.Ternary.TRUE;
import static org.revapi.Ternary.UNDECIDED;

/**
 * A result of a finished filtering (see {@link TreeFilter#finish(Element)}.
 */
public final class FilterFinishResult {
    private static final FilterFinishResult TRUE_FALSE = new FilterFinishResult(TRUE, false);
    private static final FilterFinishResult FALSE_FALSE = new FilterFinishResult(FALSE, false);
    private static final FilterFinishResult UNDECIDED_FALSE = new FilterFinishResult(UNDECIDED, false);
    private static final FilterFinishResult TRUE_TRUE = new FilterFinishResult(TRUE, true);
    private static final FilterFinishResult FALSE_TRUE = new FilterFinishResult(FALSE, true);
    private static final FilterFinishResult UNDECIDED_TRUE = new FilterFinishResult(UNDECIDED, true);

    private final Ternary match;
    private final boolean inherited;

    /**
     * Similar to {@link FilterStartResult#defaultResult()}, this returns a finish result that is undecided about the
     * match and is marked as inherited. This means that if there are any other results that need to be combined with
     * this one, those results will take precedence.
     */
    public static FilterFinishResult defaultResult() {
        return from(UNDECIDED, true);
    }

    /**
     * @return produces a result signifying that the filter didn't match.
     */
    public static FilterFinishResult doesntMatch() {
        return from(FALSE, false);
    }

    /**
     * @return produces a result signifying that the filter matched.
     */
    public static FilterFinishResult matches() {
        return from(TRUE, false);
    }

    /**
     * The return filter finish result will have the result of the match and will not be inherited, meaning that it was
     * intended directly for the elements the filter was processed upon.
     *
     * @param match
     *            the result of the filter
     *
     * @return the filter finish result
     */
    public static FilterFinishResult direct(Ternary match) {
        return from(match, false);
    }

    /**
     * Produces a filter finish result that indicates it was inherited from the provided result.
     *
     * @param parent
     *            the result to inherit from
     *
     * @return an inherited filter finish result
     */
    public static FilterFinishResult inherit(FilterFinishResult parent) {
        return from(parent.match, true);
    }

    /**
     * A factory method for filter finish results.
     *
     * @param match
     *            the result of the filtering
     * @param inherited
     *            whether the finish result is inherited or explicit
     *
     * @return the filter finish result
     */
    public static FilterFinishResult from(Ternary match, boolean inherited) {
        switch (match) {
        case FALSE:
            return inherited ? FALSE_TRUE : FALSE_FALSE;
        case TRUE:
            return inherited ? TRUE_TRUE : TRUE_FALSE;
        case UNDECIDED:
            return inherited ? UNDECIDED_TRUE : UNDECIDED_FALSE;
        default:
            throw new IllegalArgumentException("Unhandled filter match value: " + match);
        }
    }

    /**
     * Converts the provided start result into a finish result.
     *
     * @param startResult
     *            the start result to convert
     *
     * @return the converted finish result
     */
    public static FilterFinishResult from(FilterStartResult startResult) {
        return from(startResult.getMatch(), startResult.isInherited());
    }

    private FilterFinishResult(Ternary match, boolean inherited) {
        this.match = match;
        this.inherited = inherited;
    }

    /**
     * The result of the test
     */
    public Ternary getMatch() {
        return match;
    }

    /**
     * Tells whether the result is implicitly inherited from some parent element or if it was explicitly evaluated on
     * some element.
     */
    public boolean isInherited() {
        return inherited;
    }

    public FilterFinishResult and(FilterFinishResult other) {
        boolean newInherited;
        switch (match) {
        case TRUE:
            switch (other.match) {
            case TRUE:
                newInherited = inherited || other.inherited;
                break;
            case FALSE:
            case UNDECIDED:
                newInherited = other.inherited;
                break;
            default:
                throw new IllegalArgumentException("Unhandled match type: " + other.match);
            }
            break;
        case FALSE:
            newInherited = inherited;
            break;
        case UNDECIDED:
            newInherited = other.match == FALSE ? inherited : other.inherited;
            break;
        default:
            throw new IllegalArgumentException("Unhandled match type: " + match);
        }
        return from(match.and(other.match), newInherited);
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
        switch (match) {
        case TRUE:
            switch (other.match) {
            case TRUE:
                newInherited = inherited || other.inherited;
                break;
            case FALSE:
            case UNDECIDED:
                newInherited = inherited;
                break;
            default:
                throw new IllegalArgumentException("Unhandled match type: " + other.match);
            }
            break;
        case FALSE:
            newInherited = other.inherited;
            break;
        case UNDECIDED:
            newInherited = other.match == TRUE ? other.inherited : inherited;
            break;
        default:
            throw new IllegalArgumentException("Unhandled match type: " + match);
        }

        return from(match.or(other.match), newInherited);
    }

    public FilterFinishResult or(Iterable<FilterFinishResult> others) {
        FilterFinishResult res = this;
        for (FilterFinishResult other : others) {
            res = res.or(other);
        }
        return res;
    }

    public FilterFinishResult negateMatch() {
        return from(match.negate(), inherited);
    }

    public FilterFinishResult withMatch(Ternary match) {
        return from(match, inherited);
    }

    public FilterFinishResult withInherited(boolean inherited) {
        return from(match, inherited);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FilterFinishResult other = (FilterFinishResult) o;

        return inherited == other.inherited && match == other.match;
    }

    @Override
    public int hashCode() {
        int result = match.hashCode();
        result = 31 * result + (inherited ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterEndResult{" + "match=" + match + ", inherited=" + inherited + '}';
    }
}
