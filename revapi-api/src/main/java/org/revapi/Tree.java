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

import java.util.List;
import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.query.Filter;

/**
 * A representation of some "unit" understood by an API analyzer. Typically an abstract syntax tree of a language.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface Tree {

    /**
     * @return the API this tree represents
     */
    @Nonnull
    API getApi();

    /**
     * A sorted set of all root elements of the tree. The set uses the natural order of the element implementations.
     */
    @Nonnull
    SortedSet<? extends Element> getRoots();

    /**
     * Searches through the tree for elements of given type, potentially further filtering.
     * <p/>
     * If the {@code searchRoot} is not null, this is technically equivalent to calling the
     * {@link Element#searchChildren(java.lang.Class, boolean, org.revapi.query.Filter)} on the
     * {@code searchRoot}.
     *
     * @param resultType the type of the elements to be contained in the results
     * @param recurse    false to only search direct children, true for searching recursively
     * @param filter     the optional filter
     * @param searchRoot optional element from which to conduct the search
     *
     * @return a list of elements of given type (or any subtype) from the tree, filtered by the filter if provided
     */
    @Nonnull
    <T extends Element> List<T> search(@Nonnull Class<T> resultType, boolean recurse,
        @Nullable Filter<? super T> filter,
        @Nullable Element searchRoot);
}
