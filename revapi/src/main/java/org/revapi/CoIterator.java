/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Heavily inspired by the equivalently named class in Clirr 0.6.
 * <p/>
 * This is an iterator that walks a pair of collections, returning
 * matching pairs from the set.
 * <p/>
 * When an element is present in the left set but there is no equal object
 * in the right set, the pair (leftobj, null) is returned.
 * <p/>
 * When an element is present in the right set but there is no equal object
 * in the left set, the pair (null, rightobj) is returned.
 * <p/>
 * When an element in one set has an equal element in the other set, the
 * pair (leftobj, rightobj) is returned.
 * <p/>
 * Note that the phrase "pair is returned" above actually means that the
 * getLeft and getRight methods on the iterator return those objects; the
 * pair is "conceptual" rather than a physical Pair instance. This avoids
 * instantiating an object to represent the pair for each step of the
 * iterator which would not be efficient.
 * <p/>
 * Note also that elements from the sets are always returned in the
 * iteration order.
 *
 * @author Simon Kitching
 * @author Lukas Krejci
 * @since 0.1
 */
final class CoIterator<E extends Comparable<? super E>> {

    private final Iterator<? extends E> left;
    private final Iterator<? extends E> right;

    private E currentLeft;
    private E currentRight;
    private E reportedLeft;
    private E reportedRight;

    /**
     * The iterators must iterate over sorted collections otherwise this instance might not
     * produce the intended results.
     * <p/>
     * Also, the iterators must not ever return null - i.e. the collections must not contain null
     * values.
     *
     * @param left  the iterator over "left" collection
     * @param right the iterator over "right" collection
     */
    public CoIterator(Iterator<? extends E> left, Iterator<? extends E> right) {
        this.left = left;
        this.right = right;
        if (left.hasNext()) {
            currentLeft = left.next();
        }
        if (right.hasNext()) {
            currentRight = right.next();
        }
    }

    public boolean hasNext() {
        return currentLeft != null || currentRight != null;
    }

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
            order = currentLeft.compareTo(currentRight);
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

    public E getLeft() {
        return reportedLeft;
    }

    public E getRight() {
        return reportedRight;
    }

    private E nextOrNull(Iterator<? extends E> it) {
        return it.hasNext() ? it.next() : null;
    }
}
