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

import java.util.Objects;

/**
 * Represents a reference between two elements determined in some way by the API analyzer. The type of the relationship
 * is specified by the {@link Type} interface which is basically opaque outside of the API analyzer.
 * <p>
 * Note that, generally speaking, the references should not be used in {@link TreeFilter} implementations because
 * the filters are used during the construction of the {@link ElementForest} meaning that the references are not yet
 * finalized at that point in time.
 *
 * @param <E>
 *            the base type of elements
 */
public class Reference<E extends Element<E>> {
    private final E element;
    private final Type<E> type;

    public Reference(E element, Type<E> type) {
        this.element = element;
        this.type = type;
    }

    public E getElement() {
        return element;
    }

    public Type<E> getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Reference<?> relatedElement = (Reference<?>) o;
        return element.equals(relatedElement.element) && type.equals(relatedElement.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(element, type);
    }

    /**
     * Represents the type of the relationship between two elements.
     * 
     * @param <E>
     */
    public interface Type<E extends Element<E>> {
        String getName();
    }
}
