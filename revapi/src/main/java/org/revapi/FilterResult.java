/*
 * Copyright 2014-2018 Lukas Krejci
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
 * The result of the element filtering in {@link ElementGateway}. The result tells the analysis whether to let
 * the element pass or not to the next stage but also whether to descend to its children or not.
 */
public abstract class FilterResult {
    private static final FilterResult MATCH_AND_DESCEND = new MatchAndDescend();
    private static final FilterResult MATCH_AND_NOT_DESCEND = new MatchAndNotDescend();
    private static final FilterResult DOESNT_MATCH_AND_DESCEND = new DoesntMatchAndDescend();
    private static final FilterResult DOESNT_MATCH_AND_NOT_DESCEND = new DoesntMatchAndNotDescend();
    private static final FilterResult UNDECIDED_AND_DESCEND = new UndecidedAndDescend();
    private static final FilterResult UNDECIDED_AND_NOT_DESCEND = new UndecidedAndNotDescend();

    public static FilterResult matchAndDescend() {
        return from(FilterMatch.MATCHES, true);
    }

    public static FilterResult undecidedAndDescend() {
        return from(FilterMatch.UNDECIDED, true);
    }

    public static FilterResult doesntMatch() {
        return from(FilterMatch.DOESNT_MATCH, false);
    }

    public static FilterResult doesntMatchAndDescend() {
        return from(FilterMatch.DOESNT_MATCH, true);
    }

    public static FilterResult from(FilterMatch match, boolean descend) {
        switch (match) {
            case DOESNT_MATCH:
                return descend ? DOESNT_MATCH_AND_DESCEND : DOESNT_MATCH_AND_NOT_DESCEND;
            case MATCHES:
                return descend ? MATCH_AND_DESCEND : MATCH_AND_NOT_DESCEND;
            case UNDECIDED:
                return descend ? UNDECIDED_AND_DESCEND : UNDECIDED_AND_NOT_DESCEND;
            default:
                throw new IllegalArgumentException("Unhandled filter match value: " + match);
        }
    }

    private FilterResult() {

    }

    public abstract FilterMatch getMatch();

    public abstract boolean isDescend();

    public FilterResult and(FilterResult other) {
        return from(getMatch().and(other.getMatch()), isDescend() && other.isDescend());
    }

    public FilterResult and(Iterable<FilterResult> others) {
        FilterMatch resultingState = getMatch();
        boolean resultingDescend = isDescend();

        for (FilterResult r : others) {
            resultingState = resultingState.and(r.getMatch());
            resultingDescend = resultingDescend && r.isDescend();
        }

        return from(resultingState, resultingDescend);
    }

    public FilterResult or(FilterResult other) {
        return from(getMatch().or(other.getMatch()), isDescend() || other.isDescend());
    }

    public FilterResult or(Iterable<FilterResult> others) {
        FilterMatch resultingMatch = getMatch();
        boolean resultingDescend = isDescend();

        for (FilterResult r : others) {
            resultingMatch = resultingMatch.or(r.getMatch());
            resultingDescend = resultingDescend || r.isDescend();
        }

        return from(resultingMatch, resultingDescend);
    }

    public FilterResult negate() {
        FilterMatch negatedMatch = getMatch().negate();

        //noinspection SimplifiableConditionalExpression
        return from(negatedMatch, negatedMatch == FilterMatch.UNDECIDED ? isDescend() : !isDescend());
    }

    public FilterResult negateMatch() {
        return from(getMatch().negate(), isDescend());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FilterResult filterResult = (FilterResult) o;

        if (isDescend() != filterResult.isDescend()) {
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
        return result;
    }

    @Override
    public String toString() {
        return "FilterResult{" +
                "state=" + getMatch() +
                ", descend=" + isDescend() +
                '}';
    }

    private static final class MatchAndDescend extends FilterResult {

        @Override
        public FilterMatch getMatch() {
            return FilterMatch.MATCHES;
        }

        @Override
        public boolean isDescend() {
            return true;
        }
    }

    private static final class MatchAndNotDescend extends FilterResult {

        @Override
        public FilterMatch getMatch() {
            return FilterMatch.MATCHES;
        }

        @Override
        public boolean isDescend() {
            return false;
        }
    }

    private static final class DoesntMatchAndDescend extends FilterResult {

        @Override
        public FilterMatch getMatch() {
            return FilterMatch.DOESNT_MATCH;
        }

        @Override
        public boolean isDescend() {
            return true;
        }
    }

    private static final class DoesntMatchAndNotDescend extends FilterResult {

        @Override
        public FilterMatch getMatch() {
            return FilterMatch.DOESNT_MATCH;
        }

        @Override
        public boolean isDescend() {
            return false;
        }
    }

    private static final class UndecidedAndDescend extends FilterResult {

        @Override
        public FilterMatch getMatch() {
            return FilterMatch.UNDECIDED;
        }

        @Override
        public boolean isDescend() {
            return true;
        }
    }

    private static final class UndecidedAndNotDescend extends FilterResult {

        @Override
        public FilterMatch getMatch() {
            return FilterMatch.UNDECIDED;
        }

        @Override
        public boolean isDescend() {
            return false;
        }
    }
}
