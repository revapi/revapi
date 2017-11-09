/*
 * Copyright 2014-2017 Lukas Krejci
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A correspondence comparator deducer produces a comparator that is used to compare elements from 2 collections.
 *
 * <p>This is important in situations where the choice of the API comparison "partner" element cannot be determined
 * without knowing its "neighborhood" in both element forests. A concrete example of this is comparison of overloaded
 * methods.
 *
 * @author Lukas Krejci
 * @since 0.4.0
 */
public interface CorrespondenceComparatorDeducer {

    /**
     * @return a deducer that just uses the natural order of elements.
     */
    static CorrespondenceComparatorDeducer naturalOrder() {
        return (c1, c2) -> {
            Comparator<? super Element> ret = Comparator.naturalOrder();

            Collections.sort(c1, ret);
            Collections.sort(c2, ret);

            return ret;
        };
    }

    /**
     * Deduces the correspondence comparator and sorts the provided lists so that the comparator, when used to compare
     * the elements for the two lists mutually is consistent.
     *
     * <p> The collections will contain elements of different types (which is consistent with how {@link ElementForest}
     * stores the children) and it is assumed that the sorter is able to pick and choose with types of elements it is
     * able to sort. The collections will be sorted according the natural order of the elements when entering this
     * method.
     *
     * @param first the first collection of elements
     * @param second the second collection of elements
     */
    Comparator<? super Element> sortAndGetCorrespondenceComparator(List<Element> first, List<Element> second);
}
