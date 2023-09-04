/*
 * Copyright 2014-2023 Lukas Krejci
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

import org.revapi.configuration.Configurable;

/**
 * An API analyzer is the main interface one has to implement to support checking some kind of API.
 *
 * <p>
 * The API analyzer is a kind of "hub" that, once configured, produces archive analyzers to crack open the API archives
 * and generate an element tree from them. Later on during the analysis the API analyzer is asked to create a difference
 * analyzer that will be responsible to check pairs of comparable elements, each coming from the different version of
 * the API.
 *
 * <p>
 * The {@link #close()} is not called if there is no prior call to {@link #initialize(AnalysisContext)}. Do all your
 * resource acquisition in initialize, not during the construction of the object.
 *
 * @param <E>
 *            the base type of elements analyzed by this analyzer
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public interface ApiAnalyzer<E extends Element<E>> extends AutoCloseable, Configurable {

    /**
     * This method is called exactly twice during the API difference analysis. The first time it is called to obtain an
     * archive analyzer for the old version of the archives and the second time for the new version of the archives.
     *
     * @param api
     *            the api to analyze
     *
     * @return the analyzer for the supplied archives
     */
    ArchiveAnalyzer<E> getArchiveAnalyzer(API api);

    /**
     * This method is called exactly once during the API difference analysis and produces an element analyzer which will
     * be used to compare the corresponding elements in the old and new archives.
     *
     * @param oldArchive
     *            the analyzer used for the old archives
     * @param newArchive
     *            the analyzer used for the new archives
     *
     * @return an element analyzer
     */
    DifferenceAnalyzer<E> getDifferenceAnalyzer(ArchiveAnalyzer<E> oldArchive, ArchiveAnalyzer<E> newArchive);

    /**
     * The correspondence sorter to use when finalizing the comparison order of the elements in the element forest.
     *
     * @return the correspondence sorter, never null
     */
    CorrespondenceComparatorDeducer<E> getCorrespondenceDeducer();
}
