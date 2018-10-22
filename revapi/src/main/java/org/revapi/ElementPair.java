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
