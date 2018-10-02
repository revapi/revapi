package org.revapi.simple;

import java.util.IdentityHashMap;
import java.util.Objects;

import org.revapi.Element;
import org.revapi.FilterMatch;
import org.revapi.FilterResult;
import org.revapi.TreeFilter;

/**
 * A simple implementation of the {@link TreeFilter} interface that simply repeats the result provided from
 * the {@link #start(Element)} method in its {@link #finish(Element)} method.
 */
public abstract class RepeatingTreeFilter implements TreeFilter {
    private final IdentityHashMap<Element, FilterMatch> cache = new IdentityHashMap<>();
    @Override
    public final FilterResult start(Element element) {
        FilterResult ret = doStart(element);
        cache.put(element, ret.getMatch());
        return ret;
    }

    protected abstract FilterResult doStart(Element element);

    @Override
    public FilterMatch finish(Element element) {
        FilterMatch ret = Objects.requireNonNull(cache.remove(element));
        return ret;
    }
}
