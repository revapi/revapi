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

import java.util.Collections;
import java.util.Map;

import org.revapi.Element;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;

/**
 * A convenience base class for tree filters. This base class doesn't match any elements ever.
 *
 * @param <E>
 *            the parent type of all elements produced by an API analyzer
 */
public class BaseTreeFilter<E extends Element<E>> implements TreeFilter<E> {
    @Override
    public FilterStartResult start(E element) {
        return FilterStartResult.doesntMatch();
    }

    @Override
    public FilterFinishResult finish(E element) {
        return FilterFinishResult.doesntMatch();
    }

    @Override
    public Map<E, FilterFinishResult> finish() {
        return Collections.emptyMap();
    }
}
