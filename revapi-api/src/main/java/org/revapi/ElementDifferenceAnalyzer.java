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

import javax.annotation.Nullable;

/**
 * An element analyzer is basically a visitor over matching elements of some language between
 * the two API versions being compared.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface ElementDifferenceAnalyzer {

    /**
     * Called right before the analysis starts. Can be used to "warm up" the analyzer.
     */
    void setup();

    /**
     * Called right after the analysis ended. Can be used to tear down the analyzer and release
     * any resources it may hold.
     */
    void tearDown();

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
    MatchReport endAnalysis(@Nullable Element oldElement, @Nullable Element newElement);
}
