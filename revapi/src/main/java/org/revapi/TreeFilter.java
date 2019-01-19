package org.revapi;

import java.util.Collections;
import java.util.Map;

import org.revapi.query.Filter;

/**
 * A tree filter is something that is called repeatedly by the caller as the caller walks a tree of elements in a depth
 * first search manner. The tree filter gives the caller filtering results and walk instructions.
 * <p>
 * As a caller of some implementation of this interface, please study the documentation of the individual methods on
 * this interface to learn at what times the methods are supposed to be called.
 */
public interface TreeFilter {

    static TreeFilter from(Filter<Element> filter) {
        return new TreeFilter() {
            @Override
            public FilterResult start(Element element) {
                return FilterResult.from(FilterMatch.fromBoolean(filter.applies(element)),
                        filter.shouldDescendInto(element));
            }

            @Override
            public FilterMatch finish(Element element) {
                return FilterMatch.fromBoolean(filter.applies(element));
            }

            @Override
            public Map<Element, FilterMatch> finish() {
                return Collections.emptyMap();
            }
        };
    }

    static TreeFilter matchAndDescend() {
        return new TreeFilter() {
            @Override
            public FilterResult start(Element element) {
                return FilterResult.matchAndDescend();
            }

            @Override
            public FilterMatch finish(Element element) {
                return FilterMatch.MATCHES;
            }

            @Override
            public Map<Element, FilterMatch> finish() {
                return Collections.emptyMap();
            }
        };
    }
    /**
     * This method is called when an element is about to be filtered. After this call all the children will be
     * processed (if the result instructs the caller to do so). Only after that, the {@link #finish(Element)} will
     * be called with the same element as this method.
     *
     * @param element the element to start filtering
     * @return a filter result informing the caller what was the result of filtering and whether to descend to children
     * or not
     */
    FilterResult start(Element element);

    /**
     * This method is called after the filtering has {@link #start(Element) started} and all children have
     * been processed by this filter.
     * <p>
     * Note that the result can still be {@link FilterMatch#UNDECIDED}. It is expected that such elements
     * will in the end be resolved with the {@link #finish()} method.
     *
     * @param element the element for which the filtering has finished
     * @return the result of filtering
     */
    FilterMatch finish(Element element);

    /**
     * Called after all elements have been processed to see if any of them have changed in their filtering
     * result (which could be the case if there are dependencies between elements other than that of parent-child).
     * <p>
     * Note that the result can remain {@link FilterMatch#UNDECIDED}. It is upon the caller to then decide what to do
     * with such elements.
     *
     * @return the final results for elements that were previously undecided if their filtering status changed
     */
    Map<Element, FilterMatch> finish();
}
