package org.revapi;

/**
 * The result of the element filtering in {@link ElementGateway}. The result tells the analysis whether to let
 * the element pass or not to the next stage but also whether to descend to its children or not.
 */
public final class FilterResult {
    private final FilterMatch match;
    private final boolean descend;

    public static FilterResult passAndDescend() {
        return new FilterResult(FilterMatch.MATCHES, true);
    }

    public static FilterResult undecidedAndDescend() {
        return new FilterResult(FilterMatch.UNDECIDED, true);
    }

    public FilterResult(FilterMatch match, boolean descend) {
        this.match = match;
        this.descend = descend;
    }

    public FilterMatch getMatch() {
        return match;
    }

    public boolean isDescend() {
        return descend;
    }

    public FilterResult and(FilterResult other) {
        return new FilterResult(getMatch().and(other.getMatch()), isDescend() && other.isDescend());
    }

    public FilterResult and(Iterable<FilterResult> others) {
        FilterMatch resultingState = getMatch();
        boolean resultingDescend = isDescend();

        for (FilterResult r : others) {
            resultingState = resultingState.and(r.getMatch());
            resultingDescend = resultingDescend && r.isDescend();
        }

        return new FilterResult(resultingState, resultingDescend);
    }

    public FilterResult or(FilterResult other) {
        return new FilterResult(getMatch().or(other.getMatch()), isDescend() || other.isDescend());
    }

    public FilterResult or(Iterable<FilterResult> others) {
        FilterMatch resultingMatch = getMatch();
        boolean resultingDescend = isDescend();

        for (FilterResult r : others) {
            resultingMatch = resultingMatch.or(r.getMatch());
            resultingDescend = resultingDescend || r.isDescend();
        }

        return new FilterResult(resultingMatch, resultingDescend);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FilterResult filterResult = (FilterResult) o;

        if (descend != filterResult.descend) return false;
        if (match != filterResult.match) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = match.hashCode();
        result = 31 * result + (descend ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FilterResult{" +
                "state=" + match +
                ", descend=" + descend +
                '}';
    }

}
