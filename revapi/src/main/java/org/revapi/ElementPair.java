/*
 * Copyright 2014-2019 Lukas Krejci
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

import javax.annotation.Nullable;

/**
 * A simple pair of elements. At least one of them is always non-null.
 */
public final class ElementPair {
    @Nullable
    private final Element oldElement;

    @Nullable
    private final Element newElement;

    public ElementPair(@Nullable Element oldElement, @Nullable Element newElement) {
        if (oldElement == null && newElement == null) {
            throw new IllegalArgumentException("At least one of the elements must not be null.");
        }
        this.oldElement = oldElement;
        this.newElement = newElement;
    }

    @Nullable
    public Element getOldElement() {
        return oldElement;
    }

    @Nullable
    public Element getNewElement() {
        return newElement;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof ElementPair)) {
            return false;
        }

        ElementPair t = (ElementPair) o;

        return Objects.equals(oldElement, t.oldElement) && Objects.equals(newElement, t.newElement);
    }

    @Override
    public int hashCode() {
        return Objects.hash(oldElement, newElement);
    }
}
