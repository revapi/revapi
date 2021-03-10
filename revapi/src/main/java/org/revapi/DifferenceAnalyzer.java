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

import javax.annotation.Nullable;

/**
 * An element analyzer is basically a visitor over matching elements of some language between the two API versions being
 * compared.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public interface DifferenceAnalyzer<E extends Element<E>> extends AutoCloseable {

    /**
     * Called right before the analysis starts. Can be used to "warm up" the analyzer. The corresponding
     * {@link #close()} method is provided through the {@link java.lang.AutoCloseable} super interface.
     */
    void open();

    /**
     * Called when the analysis of the two corresponding elements begins. If those elements contain children, all the
     * children will be analyzed before the {@link #endAnalysis(Element, Element)} method will be called for these two
     * elements.
     *
     * @param oldElement
     *            the element from the old archives
     * @param newElement
     *            the element from the new archives
     */
    void beginAnalysis(@Nullable E oldElement, @Nullable E newElement);

    /**
     * Tells whether the analyzer needs to descend "into" the two provided elements. This is called after
     * {@link #beginAnalysis(Element, Element)} and before {@link #endAnalysis(Element, Element)} with the same pair of
     * elements.
     * <p>
     * Note that this decision is made on the "filtered" trees. I.e. if the filters filter out children of an element,
     * the analyzer cannot override that decision and try to include such elements in the analysis again.
     *
     * @param oldElement
     *            the element from the old archives
     * @param newElement
     *            the element from the new archives
     * 
     * @return true when the analysis should also include the children of the provided elements, false otherwise.
     */
    boolean isDescendRequired(@Nullable E oldElement, @Nullable E newElement);

    /**
     * Called when the analysis of the two elements ends (i.e. all the children have been visited).
     *
     * @param oldElement
     *            the element from the old archives
     * @param newElement
     *            the element from the new archives
     *
     * @return a report detailing the difference found between these two elements
     */
    Report endAnalysis(@Nullable E oldElement, @Nullable E newElement);
}
