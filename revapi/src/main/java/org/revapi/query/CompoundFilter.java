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

package org.revapi.query;

import java.util.Arrays;
import java.util.Iterator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class CompoundFilter<T> implements Filter<T> {

    private final Iterable<? extends Filter<? super T>> filters;

    public CompoundFilter(@Nonnull Iterable<? extends Filter<? super T>> filters) {
        this.filters = filters;
    }

    @SafeVarargs
    public CompoundFilter(Filter<? super T>... filters) {
        this(Arrays.asList(filters));
    }

    public Iterable<? extends Filter<? super T>> getWrappedFilters() {
        return filters;
    }

    /**
     * Return true if all the member filters apply.
     */
    @Override
    public boolean applies(@Nullable T element) {
        for (Filter<? super T> f : filters) {
            if (!f.applies(element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Return true if at least one of the member filters applies (or if there are no member filters at all).
     */
    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        Iterator<? extends Filter<? super T>> it = filters.iterator();
        boolean hasNoFilters = !it.hasNext();

        while (it.hasNext()) {
            if (it.next().shouldDescendInto(element)) {
                return true;
            }
        }

        return hasNoFilters;
    }
}
