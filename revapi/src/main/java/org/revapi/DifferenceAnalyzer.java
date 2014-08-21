/*
 * Copyright 2014 Lukas Krejci
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * An element analyzer is basically a visitor over matching elements of some language between
 * the two API versions being compared.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface DifferenceAnalyzer extends AutoCloseable {

    /**
     * Returns a comparator that can be used to determine correspondence of two elements from the 2 versions of the API
     * being analyzed.
     * <p/>
     * This comparator may "lessen" the difference between the two elements (i.e. the {@link org.revapi.Element}s
     * are themselves comparable and the returned comparator may pronounce some elements equal even if their natural
     * order would be different) but it may NEVER change the order of the elements (i.e. if 2 elements are equal
     * according to their natural order, they must remain equal and if a > b according to the natural order, "a" must
     * never be less than "b" using the correspondence comparator).
     * <p/>
     * In another words, it is required that the natural order of the elements is an refinement of the order produced by
     * the correspondence comparator returned from this method.
     * <p/>
     * Further, this comparator must be able to consistently compare any 2 types of elements that can be produced by
     * the API analyzer.
     *
     * @return the comparator used to compare 2 corresponding elements from the 2 versions of the API
     */
    @Nonnull
    Comparator<? super Element> getCorrespondenceComparator();

    /**
     * Called right before the analysis starts. Can be used to "warm up" the analyzer. The corresponding
     * {@link #close()} method is provided through the {@link java.lang.AutoCloseable} super interface.
     */
    void open();

    /**
     * Called when the analysis of the two corresponding elements begins. If those elements contain children, all the
     * children will be analyzed before the {@link #endAnalysis(Element, Element)} method will be called for these
     * two elements.
     *
     * @param oldElement the element from the old archives
     * @param newElement the element from the new archives
     */
    void beginAnalysis(@Nullable Element oldElement, @Nullable Element newElement);

    /**
     * Called when the analysis of the two elements ends (i.e. all the children have been visited).
     *
     * @param oldElement the element from the old archives
     * @param newElement the element from the new archives
     *
     * @return a report detailing the difference found between these two elements
     */
    Report endAnalysis(@Nullable Element oldElement, @Nullable Element newElement);
}
