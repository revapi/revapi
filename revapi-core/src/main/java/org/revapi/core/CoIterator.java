/*
 * Copyright 2013 Lukas Krejci
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

package org.revapi.core;

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
 * Note also that elements from the sets are always returned in the order
 * defined by the provided comparator.
 *
 * @author Simon Kitching
 * @author Lukas Krejci
 * @since 1.0
 */
final class CoIterator<E extends Comparable<? super E>> {

    private final Iterator<? extends E> left;
    private final Iterator<? extends E> right;

    private E currentLeft;
    private E currentRight;
    private E reportedLeft;
    private E reportedRight;
    private boolean moveLeft;
    private boolean moveRight;

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
        this.moveLeft = true;
        this.moveRight = true;
    }

    public boolean hasNext() {
        return left.hasNext() || right.hasNext();
    }

    public void next() {
        if (!(left.hasNext() && left.hasNext())) {
            throw new NoSuchElementException();
        }

        if (moveLeft) {
            currentLeft = left.next();
        }

        if (moveRight) {
            currentRight = right.next();
        }

        int order = currentLeft.compareTo(currentRight);

        if (order < 0) {
            reportedLeft = currentLeft;
            reportedRight = null;
            moveLeft = true;
            moveRight = false;
        } else if (order > 0) {
            reportedLeft = null;
            reportedRight = currentRight;
            moveLeft = false;
            moveRight = true;
        } else {
            moveLeft = moveRight = true;
            reportedLeft = currentLeft;
            reportedRight = currentRight;
        }
    }

    public E getLeft() {
        return reportedLeft;
    }

    public E getRight() {
        return reportedRight;
    }
}
