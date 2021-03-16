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

import static java.util.Collections.emptySortedSet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.query.DFSFilteringIterator;
import org.revapi.query.Filter;
import org.revapi.query.FilteringIterator;

/**
 * A representation of some "unit" understood by an API analyzer. Typically an abstract syntax tree of a language.
 *
 * @param <E>
 *            the common super type of all elements in the forest
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public interface ElementForest<E extends Element<E>> {
    static <E extends Element<E>> void walk(ElementForest<E> forest, Visitor<E> visitor) {
        for (E r : forest.getRoots()) {
            walk(r, visitor);
        }
        visitor.finishWalk();
    }

    static <E extends Element<E>> void walk(E root, Visitor<E> visitor) {
        visitor.startWalk(root);
        for (E c : root.getChildren()) {
            walk(c, visitor);
        }
        visitor.finishWalk(root);
    }

    static <E extends Element<E>> ElementForest<E> empty(API api) {
        return new ElementForest<E>() {
            @Nonnull
            @Override
            public API getApi() {
                return api;
            }

            @Override
            public SortedSet<E> getRoots() {
                return emptySortedSet();
            }

            @Override
            public <T extends Element<E>> Stream<T> stream(Class<T> resultType, boolean recurse, TreeFilter<E> filter,
                    Element<E> root) {
                return Stream.empty();
            }
        };
    }

    /**
     * @return the API this forest represents
     */
    API getApi();

    /**
     * A sorted set of all root elements of the forest. The set uses the natural order of the element implementations.
     *
     * @return the roots elements of the forest.
     */
    SortedSet<E> getRoots();

    /**
     * Searches through the forest for elements of given type, potentially further filtering.
     * <p>
     * If the {@code searchRoot} is not null, this is technically equivalent to calling the
     * {@link Element#searchChildren(java.lang.Class, boolean, org.revapi.query.Filter)} on the {@code searchRoot}.
     *
     * @param <T>
     *            the type of the elements to look for
     * @param resultType
     *            the type of the elements to be contained in the results
     * @param recurse
     *            false to only search direct children, true for searching recursively
     * @param filter
     *            the optional filter
     * @param searchRoot
     *            optional element from which to conduct the search
     * 
     * @return a list of elements of given type (or any subtype) from the forest, filtered by the filter if provided
     * 
     * @deprecated in favor of more versatile {@link #stream(Class, boolean, Element)}
     */
    @Deprecated
    @Nonnull
    default <T extends Element<T>> List<T> search(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter, @Nullable Element<T> searchRoot) {

        ArrayList<T> ret = new ArrayList<>();
        Iterator<T> it = iterateOverElements(resultType, recurse, filter, searchRoot);
        while (it.hasNext()) {
            ret.add(it.next());
        }

        return ret;
    }

    /**
     * @deprecated use the more versatile {@link #stream(Class, boolean, Element)}
     */
    @Deprecated
    @Nonnull
    default <T extends Element<T>> Iterator<T> iterateOverElements(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter, @Nullable Element<T> searchRoot) {

        SortedSet<? extends Element> set = searchRoot == null ? getRoots() : searchRoot.getChildren();

        return recurse ? new DFSFilteringIterator<>(set.iterator(), resultType, filter)
                : new FilteringIterator<>(set.iterator(), resultType, filter);
    }

    @Nonnull
    default <T extends Element<E>> Stream<T> stream(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Element<E> searchRoot) {
        return stream(resultType, recurse, TreeFilter.matchAndDescend(), searchRoot);
        // SortedSet<? extends Element<E>> start = searchRoot == null ? getRoots() : searchRoot.getChildren();
        //
        // Stream<T> stream = start.stream()
        // .filter(Objects::nonNull)
        // .filter(e -> resultType.isAssignableFrom(e.getClass()))
        // .map(resultType::cast);
        //
        // if (recurse) {
        // stream = stream.flatMap(e -> Stream.concat(Stream.of(e), e.stream(resultType, true)));
        // }
        //
        // return stream;
    }

    /**
     * Walks through the forest and returns a stream of elements that match the provided filter.
     *
     * @param resultType
     *            the expected type of results
     * @param recurse
     *            whether to recursively descend into children. If false, only the direct children of the
     *            {@literal root} are searched.
     * @param filter
     *            the filter to use when looking for matching children
     * @param root
     *            the search root. If null, the whole element forest is searched
     * @param <T>
     *            the expected type of results
     * 
     * @return the stream of the matching elements
     */
    <T extends Element<E>> Stream<T> stream(Class<T> resultType, boolean recurse, TreeFilter<E> filter,
            @Nullable Element<E> root);

    /**
     * A visitor of the element forest. Passed to the {@link #walk(Element, Visitor)} so that the callers can easily
     * walk the forest.
     */
    interface Visitor<E extends Element<E>> {
        /**
         * Called when the provided element is first visited.
         * 
         * @param element
         */
        void startWalk(E element);

        /**
         * Called when all the children of the element were also visited.
         * 
         * @param element
         */
        void finishWalk(E element);

        /**
         * Called when the whole forest has been visited.
         */
        void finishWalk();
    }
}
