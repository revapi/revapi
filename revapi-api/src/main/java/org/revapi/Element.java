/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi;

import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import org.revapi.query.Filter;

/**
 * An element in a tree representation of given "language" under API inspection. In case of programming languages this
 * will usually be a (trimmed down) abstract syntax tree, in case of XSD this can be an actual DOM tree.
 * <p/>
 * An element is comparable with all other element types for given language, giving a total ordering across all and any
 * element types given language defines.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Element extends Comparable<Element> {

    Element getParent();

    void setParent(Element parent);

    SortedSet<? extends Element> getChildren();

    /**
     * This method is functionally equivalent to {@link #searchChildren(java.util.List, java.lang.Class, boolean,
     * org.revapi.query.Filter)} but returns the result in a newly allocated list instance. This is basically
     * a convenience method to enable a more succinct expressions.
     *
     * @param resultType the type of the elements to look for
     * @param recurse    false to search only in direct children of the element, true to search recursively
     * @param filter     optional filter to further trim the number of results  @return the list of child elements of
     *                   given type potentially satisfying given filter
     */
    <T extends Element> List<T> searchChildren(Class<T> resultType, boolean recurse, Filter<? super T> filter);

    /**
     * Recursively searches the children of this element for elements of given type, potentially applicable to given
     * filter.
     *
     * @param resultType the type of the elements to look for
     * @param recurse    false to search only in direct children of the element, true to search recursively
     * @param filter     optional filter to further trim the number of results
     *
     * @return the list of child elements of given type potentially satisfying given filter
     */
    <T extends Element> void searchChildren(List<T> results, Class<T> resultType, boolean recurse,
        Filter<? super T> filter);

    /**
     * Similar to search methods but avoids the traversal over the whole tree. Instead the traversal is incremental
     * and governed by the returned iterator.
     *
     * @param recurse if true, the iterator traverses the element tree using depth first search
     *
     * @return the iterator that will iterate over the results
     *
     * @see #searchChildren(Class, boolean, org.revapi.query.Filter)
     */
    <T extends Element> Iterator<T> iterateOverChildren(Class<T> resultType, boolean recurse, Filter<? super T> filter);
}
