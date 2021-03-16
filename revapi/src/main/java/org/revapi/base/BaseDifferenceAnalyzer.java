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

import org.revapi.DifferenceAnalyzer;
import org.revapi.Element;

/**
 * A convenience base class for difference analyzers analyzers.
 *
 * @param <E>
 *            the parent type of all elements produced by this API analyzer
 */
public abstract class BaseDifferenceAnalyzer<E extends Element<E>> implements DifferenceAnalyzer<E> {
    @Override
    public void open() {
    }

    /**
     * This default implementation returns {@code true} only if both elements are non-null.
     *
     * @param oldElement
     *            the element from the old archives
     * @param newElement
     *            the element from the new archives
     * 
     * @return true if both elements are not null, false otherwise
     */
    @Override
    public boolean isDescendRequired(@Nullable E oldElement, @Nullable E newElement) {
        return oldElement != null && newElement != null;
    }

    @Override
    public void close() throws Exception {

    }
}
