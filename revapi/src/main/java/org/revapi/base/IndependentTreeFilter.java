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

import java.util.IdentityHashMap;
import java.util.Objects;

import org.revapi.Element;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;

/**
 * A simple implementation of the {@link TreeFilter} interface that simply repeats the result provided from
 * the {@link #start(Element)} method in its {@link #finish(Element)} method.
 * <p>
 * This is what filters that do not depend on any other element usually need.
 */
public abstract class IndependentTreeFilter<E extends Element<E>> extends BaseTreeFilter<E> {
    private final IdentityHashMap<E, FilterFinishResult> cache = new IdentityHashMap<>();
    @Override
    public final FilterStartResult start(E element) {
        FilterStartResult ret = doStart(element);
        cache.put(element, FilterFinishResult.from(ret));
        return ret;
    }

    protected abstract FilterStartResult doStart(E element);

    @Override
    public FilterFinishResult finish(E element) {
        return Objects.requireNonNull(cache.remove(element));
    }
}
