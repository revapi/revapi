/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Heavily inspired by the equivalently named class in Clirr 0.6.
 *
 * <p>This is an iterator that walks a pair of collections, returning
 * matching pairs from the set.
 *
 * <p>When an element is present in the left set but there is no equal object
 * in the right set, the pair (leftobj, null) is returned.
 *
 * <p>When an element is present in the right set but there is no equal object
 * in the left set, the pair (null, rightobj) is returned.
 *
 * <p>When an element in one set has an equal element in the other set, the
 * pair (leftobj, rightobj) is returned.
 *
 * <p>Note that the phrase "pair is returned" above actually means that the
 * getLeft and getRight methods on the iterator return those objects; the
 * pair is "conceptual" rather than a physical Pair instance. This avoids
 * instantiating an object to represent the pair for each step of the
 * iterator which would not be efficient.
 *
 * <p>Note also that elements from the sets are always returned in the
 * iteration order.
 *
 * @author Simon Kitching
 * @author Lukas Krejci
 * @since 0.1
 */
public final class CoIterator<E> {

    private final Iterator<? extends E> left;
    private final Iterator<? extends E> right;
    private final Comparator<? super E> comparator;

    private E currentLeft;
    private E currentRight;
    private E reportedLeft;
    private E reportedRight;

    /**
     * The iterators must iterate over sorted collections otherwise this instance might not
     * produce the intended results.
     *
     * <p>Also, the iterators must not ever return null - i.e. the collections must not contain null
     * values otherwise the behavior of the iteration is undefined.
     *
     * @param left  the iterator over "left" collection
     * @param right the iterator over "right" collection
     * @param comparator the comparator used to sort the collections (this must have been done prior to calling this
     *                   constructor)
     */
    public CoIterator(Iterator<? extends E> left, Iterator<? extends E> right, Comparator<? super E> comparator) {
        this.left = left;
        this.right = right;
        this.comparator = comparator;

        if (left.hasNext()) {
            currentLeft = left.next();
        }
        if (right.hasNext()) {
            currentRight = right.next();
        }
    }

    /**
     * Assumes the iterators iterate over comparable elements and uses their natural ordering instead of an explicit
     * comparator.
     * <p>If <code>E</code> is not at the same time comparable, calling {@link #next()} will fail with a class cast
     * exception at the first mutual comparison of elements from the two collections.
     *
     * @param left the iterator over the "left" collection
     * @param right the iterator over the "right" collection
     *
     * @see #CoIterator(java.util.Iterator, java.util.Iterator, java.util.Comparator)
     */
    public CoIterator(Iterator<? extends E> left, Iterator<? extends E> right) {
        this(left, right, new NaturalOrderComparator());
    }

    public boolean hasNext() {
        return currentLeft != null || currentRight != null;
    }

    /**
     * Use {@link #getLeft()} and {@link #getRight()} to get the next elements from the two iterated collections.
     */
    public void next() {
        boolean hasLeft = currentLeft != null;
        boolean hasRight = currentRight != null;

        if (!hasLeft && !hasRight) {
            throw new NoSuchElementException();
        }

        int order;
        if (hasLeft && !hasRight) {
            order = -1;
        } else if (!hasLeft) {
            order = 1;
        } else {
            order = comparator.compare(currentLeft, currentRight);
        }

        if (order < 0) {
            reportedLeft = currentLeft;
            currentLeft = nextOrNull(left);
            reportedRight = null;
        } else if (order > 0) {
            reportedLeft = null;
            reportedRight = currentRight;
            currentRight = nextOrNull(right);
        } else {
            reportedLeft = currentLeft;
            reportedRight = currentRight;
            currentLeft = nextOrNull(left);
            currentRight = nextOrNull(right);
        }
    }

    /**
     * After calling {@link #next()}, this will contain the next element from the "left" collection.
     * @return the next element from the left collection
     */
    public E getLeft() {
        return reportedLeft;
    }

    /**
     * After calling {@link #next()}, this will contain the next element from the "right" collection.
     * @return the next element from the right collection
     */
    public E getRight() {
        return reportedRight;
    }

    private E nextOrNull(Iterator<? extends E> it) {
        return it.hasNext() ? it.next() : null;
    }
}
