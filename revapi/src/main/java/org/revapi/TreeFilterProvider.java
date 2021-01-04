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

import java.util.Optional;

import org.revapi.configuration.Configurable;

/**
 * Forest filter helps the {@link ArchiveAnalyzer} filter the resulting element forest while it is being
 * created.
 * <p>
 * It is guaranteed that the elements will be called in an hierarchical order, e.g. parents will be filtered
 * before their children.
 */
public interface TreeFilterProvider extends Configurable, AutoCloseable {
    /**
     * Creates a new filter specifically for use with the provided analyzer. Can return null if this forest filter
     * cannot understand elements provided by the analyzer.
     *
     * @param archiveAnalyzer the archive analyzer to produce a new filter for
     * @return a new filter for given analyzer or empty if this forest filter is not compatible with the analyzer
     */
    <E extends Element<E>> Optional<TreeFilter<E>> filterFor(ArchiveAnalyzer<E> archiveAnalyzer);
}
