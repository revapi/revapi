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

/**
 * The instances of implementations of this interface are produced by the {@link org.revapi.ApiAnalyzer}s to
 * analyze the API archives and create an element tree that is then used for API comparison.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface ArchiveAnalyzer {

    /**
     * Analyzes the API archives and filters the forest using the provided filter.
     * <p>
     * This produces a preliminary forest which can be too "wide" because of {@link FilterMatch#UNDECIDED} elements.
     * Once the preliminary forest is obtained and filtered down, it can then be {@link #prune(ElementForest) pruned}
     * by this analyzer to account for "non-local" effects removal of elements can have on it (like for example removal
     * of elements that are no longer used by any other element in the forest, if the analyzer deems it necessary).
     *
     * @param filter the filter to use to "prune" the forest
     * @return the element forest ready for analysis
     */
    ElementForest analyze(TreeFilter filter);

    /**
     * Once all the filtering on the element forest is done, the analyzer is allowed one final "pass" through the forest
     * to remove any elements that should not be there any longer.
     *
     * @param forest the forest to prune
     */
    void prune(ElementForest forest);
}
