/*
 * Copyright 2014-2021 Lukas Krejci
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
 * The result of the element filtering in {@link TreeFilter}. The result tells the analysis whether to let the element
 * pass or not to the next stage but also whether to descend to its children or not.
 */
public final class FilterStartResult {
    private static final FilterStartResult FALSE_FALSE_FALSE = new FilterStartResult(FALSE, FALSE, false);
    private static final FilterStartResult FALSE_FALSE_TRUE = new FilterStartResult(FALSE, FALSE, true);
    private static final FilterStartResult FALSE_TRUE_FALSE = new FilterStartResult(FALSE, TRUE, false);
    private static final FilterStartResult FALSE_TRUE_TRUE = new FilterStartResult(FALSE, TRUE, true);
    private static final FilterStartResult FALSE_UNDECIDED_FALSE = new FilterStartResult(FALSE, UNDECIDED, false);
    private static final FilterStartResult FALSE_UNDECIDED_TRUE = new FilterStartResult(FALSE, UNDECIDED, true);
    private static final FilterStartResult TRUE_FALSE_FALSE = new FilterStartResult(TRUE, FALSE, false);
    private static final FilterStartResult TRUE_FALSE_TRUE = new FilterStartResult(TRUE, FALSE, true);
    private static final FilterStartResult TRUE_TRUE_FALSE = new FilterStartResult(TRUE, TRUE, false);
    private static final FilterStartResult TRUE_TRUE_TRUE = new FilterStartResult(TRUE, TRUE, true);
    private static final FilterStartResult TRUE_UNDECIDED_FALSE = new FilterStartResult(TRUE, UNDECIDED, false);
    private static final FilterStartResult TRUE_UNDECIDED_TRUE = new FilterStartResult(TRUE, UNDECIDED, true);
    private static final FilterStartResult UNDECIDED_FALSE_FALSE = new FilterStartResult(UNDECIDED, FALSE, false);
    private static final FilterStartResult UNDECIDED_FALSE_TRUE = new FilterStartResult(UNDECIDED, FALSE, true);
    private static final FilterStartResult UNDECIDED_TRUE_FALSE = new FilterStartResult(UNDECIDED, TRUE, false);
    private static final FilterStartResult UNDECIDED_TRUE_TRUE = new FilterStartResult(UNDECIDED, TRUE, true);
    private static final FilterStartResult UNDECIDED_UNDECIDED_FALSE = new FilterStartResult(UNDECIDED, UNDECIDED,
            false);
    private static final FilterStartResult UNDECIDED_UNDECIDED_TRUE = new FilterStartResult(UNDECIDED, UNDECIDED, true);

    private final Ternary match;
    private final Ternary descend;
    private final boolean inherited;

    /**
     * This result is undecided about its match and descend and is marked as inherited. This means that it doesn't
     * influence the "decision" of other filters but if it is the only one present, it gives the consumers of the result
     * the chance to make a decision whether to make the elements pass or not.
     *
     * <p>
     * This should be used by the tree filters in cases where they don't have anything to "say" about filtering (due to
     * lack of configuration or something similar).
     */
    public static FilterStartResult defaultResult() {
        return from(UNDECIDED, UNDECIDED, true);
    }

    /**
     * The result will match any element and will always descend to any element's children. This SHOULD NOT be used as a
     * default value of the filter result. Instead, it is meant for situations where one wants to retrieve all elements
     * in the tree using {@link ElementForest#stream(Class, boolean, TreeFilter, Element)} or similar methods.
     */
    public static FilterStartResult matchAndDescend() {
        return TRUE_TRUE_FALSE;
    }

    public static FilterStartResult doesntMatch() {
        return FALSE_FALSE_FALSE;
    }

    public static FilterStartResult direct(Ternary match, Ternary descend) {
        return from(match, descend, false);
    }

    public static FilterStartResult inherit(FilterStartResult parent) {
        return from(parent.match, parent.descend, true);
    }

    public static FilterStartResult from(Ternary match, Ternary descend, boolean inherited) {
        switch (match) {
        case TRUE:
            switch (descend) {
            case FALSE:
                return inherited ? TRUE_FALSE_TRUE : TRUE_FALSE_FALSE;
            case TRUE:
                return inherited ? TRUE_TRUE_TRUE : TRUE_TRUE_FALSE;
            case UNDECIDED:
                return inherited ? TRUE_UNDECIDED_TRUE : TRUE_UNDECIDED_FALSE;
            }
            break;
        case FALSE:
            switch (descend) {
            case FALSE:
                return inherited ? FALSE_FALSE_TRUE : FALSE_FALSE_FALSE;
            case TRUE:
                return inherited ? FALSE_TRUE_TRUE : FALSE_TRUE_FALSE;
            case UNDECIDED:
                return inherited ? FALSE_UNDECIDED_TRUE : FALSE_UNDECIDED_FALSE;
            }
            break;
        case UNDECIDED:
            switch (descend) {
            case FALSE:
                return inherited ? UNDECIDED_FALSE_TRUE : UNDECIDED_FALSE_FALSE;
            case TRUE:
                return inherited ? UNDECIDED_TRUE_TRUE : UNDECIDED_TRUE_FALSE;
            case UNDECIDED:
                return inherited ? UNDECIDED_UNDECIDED_TRUE : UNDECIDED_UNDECIDED_FALSE;
            }
            break;
        }
        throw new IllegalStateException("Unhandled filter match or descend value.");
    }

    public static FilterStartResult from(FilterFinishResult result, Ternary descend) {
        return from(result.getMatch(), descend, result.isInherited());
    }

    private FilterStartResult(Ternary match, Ternary descend, boolean inherited) {
        this.match = match;

        this.descend = descend;
        this.inherited = inherited;
    }

    /**
     * The result of the test
     */
    public Ternary getMatch() {
        return match;
    }

    public Ternary getDescend() {
        return descend;
    }

    /**
     * Tells whether the result is implicitly inherited from some parent element or if it was explicitly evaluated on
     * some element.
     */
    public boolean isInherited() {
        return inherited;
    }

    public FilterStartResult and(FilterStartResult other) {
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
                throw new IllegalArgumentException("Unhandled match type: " + match);
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
        return from(match.and(other.match), combineDescend(other), newInherited);
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
                throw new IllegalArgumentException("Unhandled match type: " + match);
            }
            break;
        case FALSE:
            newInherited = other.inherited;
            break;
        case UNDECIDED:
            newInherited = other.match == FALSE ? other.inherited : inherited;
            break;
        default:
            throw new IllegalArgumentException("Unhandled match type: " + match);
        }

        return from(match.or(other.match), combineDescend(other), newInherited);
    }

    public FilterStartResult or(Iterable<FilterStartResult> others) {
        FilterStartResult res = this;
        for (FilterStartResult other : others) {
            res = res.or(other);
        }
        return res;
    }

    public FilterStartResult negateMatch() {
        return from(match.negate(), descend, inherited);
    }

    public FilterStartResult withMatch(Ternary match) {
        return from(match, descend, inherited);
    }

    public FilterStartResult withDescend(Ternary descend) {
        return from(match, descend, inherited);
    }

    public FilterStartResult withInherited(boolean inherited) {
        return from(match, descend, inherited);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FilterStartResult other = (FilterStartResult) o;

        return descend == other.descend && inherited == other.inherited && match == other.match;
    }

    @Override
    public int hashCode() {
        int result = match.hashCode();
        result = 31 * result + descend.hashCode();
        result = 31 * result + (inherited ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterStartResult{" + "match=" + match + ", descend=" + descend + ", inherited=" + inherited + '}';
    }

    private Ternary combineDescend(FilterStartResult other) {
        // we favor descend over non-descend.. E.g. if one filter wants to descend, it overrides the decision of
        // other filters to not descend...
        // we also favor a decision over a non-decision. E.g. if we're undecided about the descend, we let the other
        // result decide.

        switch (descend) {
        case TRUE:
            return descend;
        case FALSE:
            return other.descend == TRUE ? TRUE : FALSE;
        case UNDECIDED:
            return other.descend;
        default:
            throw new IllegalStateException("Unhandled descend type");
        }
    }
}
