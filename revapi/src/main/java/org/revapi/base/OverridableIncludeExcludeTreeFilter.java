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
package org.revapi.base;

import javax.annotation.Nullable;

import org.revapi.Element;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;

/**
 * This is similar to the {@link IncludeExcludeTreeFilter} but adds support for the "include inside exclude", e.g.
 * to match the some children of an element that is itself excluded. This comes at a cost of always needing to descend
 * into the children even if the exclude filter says it is not necessary.
 *
 * @param <E>
 */
public class OverridableIncludeExcludeTreeFilter<E extends Element<E>> extends IncludeExcludeTreeFilter<E> {
    public OverridableIncludeExcludeTreeFilter(@Nullable TreeFilter<E> include, @Nullable TreeFilter<E> exclude) {
        super(include, exclude);
    }

    protected @Nullable
    FilterStartResult processIncludeStart(@Nullable FilterStartResult result) {
        if (result != null && result.getDescend() == Ternary.FALSE) {
            result = result.withDescend(Ternary.UNDECIDED);
        }

        return result;
    }

    protected @Nullable FilterStartResult processExcludeStart(@Nullable FilterStartResult result) {
        return processIncludeStart(result);
    }
}
