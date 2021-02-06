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

/**
 * The instances of implementations of this interface are produced by the {@link org.revapi.ApiAnalyzer}s to
 * analyze the API archives and create an element tree that is then used for API comparison.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface ArchiveAnalyzer<E extends Element<E>> {

    /**
     * @return the {@link ApiAnalyzer} that created this instance
     */
    ApiAnalyzer<E> getApiAnalyzer();

    /**
     * @return the API that this analyzer analyzes
     */
    API getApi();

    /**
     * Analyzes the API archives and filters the forest using the provided filter.
     * <p>
     * This produces a preliminary forest which can be too "wide" because of {@link Ternary#UNDECIDED} elements or
     * non-local relationships between elements. Once this method returns the preliminary forest, the callers should
     * also call the {@link #prune(ElementForest)} method to obtain a forest that is truly minimal.
     *
     * @param filter the filter to use to filter out unwanted elements from the forest
     * @return the preliminary element forest that should be {@link #prune(ElementForest) pruned} before analysis
     */
    ElementForest<E> analyze(TreeFilter<E> filter);

    /**
     * Once all the filtering on the element forest is done, the analyzer is allowed one final "pass" through the forest
     * to remove any elements that should not be there any longer.
     *
     * @param forest the forest to prune
     */
    void prune(ElementForest<E> forest);
}
