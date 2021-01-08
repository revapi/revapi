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

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.revapi.query.Filter;

/**
 * An element in a forest representation of given "language" under API inspection. In case of programming languages this
 * will usually be a (trimmed down) abstract syntax tree, in case of XSD this can be an actual DOM tree.
 *
 * <p>An element is comparable with all other element types for given language, giving a total ordering across all and
 * any element types given language defines.
 *
 * <p>The {@link #compareTo(Object)} method is used by Revapi to match up the pairs of elements from the old and new
 * API. Revapi sorts the elements from old and new API (using their natural order, i.e. using the {@code compareTo}
 * method) and then iterates through both sets at the same time, comparing the elements.
 *
 * <table>
 *     <caption>API traversal outline</caption>
 *     <tr><th>Comparison</th><th>Meaning</th><th>Result</th><th>Iteration Progress</th></tr>
 *     <tr>
 *         <td>old &lt; new</td>
 *         <td>New is considered removed from the API</td>
 *         <td>(old, null)</td>
 *         <td>Old moves forward, new stays</td>
 *     </tr>
 *     <tr>
 *         <td>old &gt; new</td>
 *         <td>Old is considered removed from the API</td>
 *         <td>(null, new)</td>
 *         <td>New moves forward, old stays</td>
 *     </tr>
 *     <tr>
 *         <td>old == new</td>
 *         <td>New is considered equal to old</td>
 *         <td>(old, new)</td>
 *         <td>Old moves forward, new moves forward</td>
 *     </tr>
 * </table>
 *
 * The result from the above comparison is handed over to the difference analyzer which is assumed to do a more detailed
 * analysis on the elements and return a (possibly empty) set of differences.
 *
 * @param <E> the common base class of all elements of some api analyzer
 *
 * @author Lukas Krejci
 * @since 0.1
 * @see CoIterator
 */
public interface Element<E extends Element<E>> extends Comparable<E> {

    /**
     * Casts this element to the provided type.
     * @param type the type to cast this instance to
     * @return this cast as the provided type
     * @throws ClassCastException if this instance cannot be cast to the provided type
     */
    default <T extends Element<?>> T as(Class<T> type) {
        return type.cast(this);
    }

    /**
     * @return the API version this element comes from
     */
    API getApi();

    /**
     * @return the archive the element comes from or null if that cannot be determined
     */
    @Nullable
    Archive getArchive();

    /**
     * @return the parent element or null if this is a root element
     */
    @Nullable
    E getParent();

    /**
     * Sets a new parent.
     * @param parent the new parent of this element
     */
    void setParent(@Nullable E parent);

    SortedSet<E> getChildren();

    /**
     * Provides the full "path" to the element in the forest in a human readable way.
     * This method is meant to be used by the reporters to identify the element in the reports.
     *
     * @return human readable representation of the element
     */
    String getFullHumanReadableString();

    /**
     * This method is functionally equivalent to {@link #searchChildren(java.util.List, java.lang.Class, boolean,
     * org.revapi.query.Filter)} but returns the result in a newly allocated list instance. This is basically
     * a convenience method to enable a more succinct expressions.
     *
     * @param <T>        the type of the elements to look for
     * @param resultType the type of the elements to look for
     * @param recurse    false to search only in direct children of the element, true to search recursively
     * @param filter     optional filter to further trim the number of results  @return the list of child elements of
     *                   given type potentially satisfying given filter
     *
     * @return the list of found elements
     * @deprecated in favor of {@link #stream(Class, boolean)}
     */
    @Deprecated
    <T extends Element<E>> List<T> searchChildren(Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter);

    /**
     * Recursively searches the children of this element for elements of given type, potentially applicable to given
     * filter.
     *
     * <p>This is identical to {@link #searchChildren(Class, boolean, org.revapi.query.Filter)} in behavior but avoids
     * the instantiation of a new list.
     *
     * @param <T>        the type of the elements to look for
     * @param results    the list of the results to fill
     * @param resultType the type of the elements to look for
     * @param recurse    false to search only in direct children of the element, true to search recursively
     * @param filter     optional filter to further trim the number of results
     * @deprecated in favor of {@link #stream(Class, boolean)}
     */
    @Deprecated
    <T extends Element<E>> void searchChildren(List<T> results, Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter);

    /**
     * Similar to search methods but avoids the traversal over the whole forest. Instead the traversal is incremental
     * and governed by the returned iterator.
     *
     * @param <T>        the type of the elements to look for
     * @param resultType the type of elements to look for
     * @param recurse if true, the iterator traverses the element forest using depth first search
     * @param filter optional filter to further trim the number of results
     *
     * @return the iterator that will iterate over the results
     *
     * @see #searchChildren(Class, boolean, org.revapi.query.Filter)
     * @deprecated use the more standard {@link #stream(Class, boolean)}
     */
    @Deprecated
    <T extends Element<E>> Iterator<T> iterateOverChildren(Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter);

    /**
     * A stream equivalent of {@link #iterateOverChildren(Class, boolean, Filter)}. The resulting stream contains
     * distinct non-null elements.
     *
     * @param <T>         the type of the elements to look for
     * @param elementType the type of elements to look for
     * @param recurse     if true, the iterator traverses the element forest using depth first search
     * @return the stream of elements complying to the filter
     */
    default <T extends Element<E>> Stream<T> stream(Class<T> elementType, boolean recurse) {
        Stream<T> stream = getChildren().stream()
                .filter(Objects::nonNull)
                .filter(e -> elementType.isAssignableFrom(e.getClass()))
                .map(elementType::cast);

        if (recurse) {
            stream = stream.flatMap(e -> Stream.concat(Stream.of(e), e.stream(elementType, true)));
        }

        return stream;
    }
}
