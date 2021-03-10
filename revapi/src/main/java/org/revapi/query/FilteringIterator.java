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
package org.revapi.query;

import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A decorator over the {@link java.util.Iterator} impls that can leave out the elements of incompatible types that
 * optionally don't conform to provided filter.
 *
 * <p>
 * This implementation does NOT support null elements returned by the decorated iterator.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 * 
 * @deprecated Filtering turned out to be more complex than this. {@link Filter} has been superseded by
 *             {@link org.revapi.TreeFilter}.
 */
@Deprecated
public class FilteringIterator<E> implements Iterator<E> {
    private final Class<E> resultType;
    private final Iterator<?> wrapped;
    private final Filter<? super E> filter;
    private E current;

    public FilteringIterator(@Nonnull Iterator<?> iterator, @Nonnull Class<E> resultType,
            @Nullable Filter<? super E> filter) {
        this.wrapped = iterator;
        this.filter = filter;
        this.resultType = resultType;
    }

    @Override
    public boolean hasNext() {
        if (current != null) {
            return wrapped.hasNext();
        } else {
            while (wrapped.hasNext()) {
                Object next = wrapped.next();
                if (next == null || !resultType.isAssignableFrom(next.getClass())) {
                    continue;
                }

                E cur = resultType.cast(next);
                if (filter == null || filter.applies(cur)) {
                    current = cur;
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public @Nonnull E next() {
        if (current == null && !hasNext()) {
            throw new NoSuchElementException();
        }

        E ret = current;
        current = null;

        return ret;
    }

    @Override
    public void remove() {
        wrapped.remove();
    }
}
