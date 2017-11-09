/*
 * Copyright 2014-2017 Lukas Krejci
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
