package org.revapi.java;

import java.util.function.Function;

import javax.annotation.Nullable;

import org.revapi.query.Filter;

/**
 * @author Lukas Krejci
 * @since 0.11.0
 */
public final class FlatFilter {
    /**
     * A simple filter that scans only the direct children of some element.
     *
     * @param filter the function to determine if the filter {@link Filter#applies(Object)}
     * @param <T> the type of the element to accept
     * @return a filter implementation that does not descend into any element
     */
    public static <T> Filter<T> by(Function<T, Boolean> filter) {
        return new Filter<T>() {
            @Override public boolean applies(@Nullable T element) {
                return filter.apply(element);
            }

            @Override public boolean shouldDescendInto(@Nullable Object element) {
                return false;
            }
        };
    }
}
