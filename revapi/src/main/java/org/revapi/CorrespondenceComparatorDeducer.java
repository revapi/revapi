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

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * A correspondence comparator deducer produces a comparator that is used to compare elements from 2 collections.
 *
 * <p>
 * This is important in situations where the choice of the API comparison "partner" element cannot be determined without
 * knowing its "neighborhood" in both element forests. A concrete example of this is comparison of overloaded methods.
 *
 * @author Lukas Krejci
 * 
 * @since 0.4.0
 */
public interface CorrespondenceComparatorDeducer<E extends Element<E>> {

    /**
     * @return a deducer that just uses the natural order of elements.
     */
    static <E extends Element<E>> CorrespondenceComparatorDeducer<E> naturalOrder() {
        return (l1, l2) -> {
            // by definition the collections are already sorted by their natural order, so we don't have to do
            // any sorting here and just return the comparator.
            return Comparator.naturalOrder();
        };
    }

    /**
     * This correspondence deducer is a good match for situations where the ordering of the children of some element is
     * not semantic but rather positional, e.g. method parameters or elements of an array. The deducer will then return
     * a comparator that will make Revapi produce the minimal set of changes necessary to transform the old into the
     * new.
     *
     * @param equality
     *            a function to determine the element equality
     *
     * @param <E>
     *            the base type of the elements
     * 
     * @return a correspondence comparator deducer that will produce a diff-like ordering of the elements
     */
    static <E extends Element<E>> CorrespondenceComparatorDeducer<E> editDistance(
            BiPredicate<? super E, ? super E> equality) {
        return (as, bs) -> {
            if (as.isEmpty() || bs.isEmpty()) {
                return Comparator.naturalOrder();
            }

            List<EditDistance.Pair<E>> pairs = EditDistance.compute(as, bs, equality);
            IdentityHashMap<E, Integer> order = new IdentityHashMap<>();

            for (int i = 0; i < pairs.size(); ++i) {
                EditDistance.Pair<E> pair = pairs.get(i);
                if (pair.left != null) {
                    order.put(pair.left, i);
                }
                if (pair.right != null) {
                    order.put(pair.right, i);
                }
            }

            return Comparator.comparingInt(order::get);
        };
    }

    /**
     * Deduces the correspondence comparator and sorts the provided lists so that the comparator, when used to compare
     * the elements for the two lists mutually is consistent.
     *
     * <p>
     * The collections will contain elements of different types (which is consistent with how {@link ElementForest}
     * stores the children) and it is assumed that the sorter is able to pick and choose which types of elements it is
     * able to sort. The collections will be sorted according the natural order of the elements when entering this
     * method.
     *
     * @param first
     *            the first collection of elements
     * @param second
     *            the second collection of elements
     */
    Comparator<? super E> sortAndGetCorrespondenceComparator(List<E> first, List<E> second);
}
