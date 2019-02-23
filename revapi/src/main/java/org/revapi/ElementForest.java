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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.query.DFSFilteringIterator;
import org.revapi.query.Filter;
import org.revapi.query.FilteringIterator;

/**
 * A representation of some "unit" understood by an API analyzer. Typically an abstract syntax tree of a language.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface ElementForest {
    static void walk(ElementForest forest, Visitor visitor) {
        for(Element r : forest.getRoots()) {
            walk(r, visitor);
        }
        visitor.finishWalk();
    }

    static void walk(Element root, Visitor visitor) {
        visitor.startWalk(root);
        for (Element c : root.getChildren()) {
            walk(c, visitor);
        }
        visitor.finishWalk(root);
    }

    /**
     * @return the API this forest represents
     */
    @Nonnull
    API getApi();

    /**
     * A sorted set of all root elements of the forest. The set uses the natural order of the element implementations.
     *
     * @return the roots elements of the forest.
     */
    @Nonnull
    SortedSet<? extends Element> getRoots();

    /**
     * Searches through the forest for elements of given type, potentially further filtering.
     * <p>
     * If the {@code searchRoot} is not null, this is technically equivalent to calling the
     * {@link Element#searchChildren(java.lang.Class, boolean, org.revapi.query.Filter)} on the
     * {@code searchRoot}.
     *
     * @param <T>        the type of the elements to look for
     * @param resultType the type of the elements to be contained in the results
     * @param recurse    false to only search direct children, true for searching recursively
     * @param filter     the optional filter
     * @param searchRoot optional element from which to conduct the search
     * @return a list of elements of given type (or any subtype) from the forest, filtered by the filter if provided
     */
    @Nonnull
    default <T extends Element> List<T> search(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter, @Nullable Element searchRoot) {

        ArrayList<T> ret = new ArrayList<>();
        Iterator<T> it = iterateOverElements(resultType, recurse, filter, searchRoot);
        while (it.hasNext()) {
            ret.add(it.next());
        }

        return ret;
    }

    @Nonnull
    default <T extends Element> Iterator<T> iterateOverElements(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Filter<? super T> filter, @Nullable Element searchRoot) {

        SortedSet<? extends Element> set = searchRoot == null ? getRoots() : searchRoot.getChildren();

        return recurse ? new DFSFilteringIterator<>(set.iterator(), resultType, filter) :
                new FilteringIterator<>(set.iterator(), resultType, filter);
    }

    @Nonnull
    default <T extends Element> Stream<T> stream(@Nonnull Class<T> resultType, boolean recurse,
            @Nullable Element searchRoot) {
        Iterator<T> it = iterateOverElements(resultType, recurse, null, searchRoot);
        Spliterator<T> sit = Spliterators.spliteratorUnknownSize(it,
                Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED);

        return StreamSupport.stream(sit, false);
    }

    /**
     * A visitor of the element forest. Passed to the {@link #walk(Element, Visitor)}  so that the callers can easily
     * walk the forest.
     */
    interface Visitor {
        /**
         * Called when the provided element is first visited.
         * @param element
         */
        void startWalk(Element element);

        /**
         * Called when all the children of the element were also visited.
         * @param element
         */
        void finishWalk(Element element);

        /**
         * Called when the whole forest has been visited.
         */
        void finishWalk();
    }
}
