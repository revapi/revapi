/*
 * Copyright 2014-2018 Lukas Krejci
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

import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;

/**
 * An element matcher is a helper extension to other extensions that need to figure out if a certain
 * element meets certain criteria.
 *
 * @author Lukas Krejci
 */
public interface ElementMatcher extends Configurable, AutoCloseable {

    /**
     * Tries to compile the provided recipe into a form that can test individual elements.
     *
     * @param recipe the recipe to compile
     *
     * @return a compiled recipe or empty optional if the string cannot be compiled by this matcher
     */
    Optional<CompiledRecipe> compile(String recipe);

    /**
     * A "compiled" representation of a textual recipe. It is assumed that the element matchers will want to create
     * some intermediate representation of the textual recipe that is faster to transform into a tree filter.
     */
    interface CompiledRecipe {
        /**
         * The recipe needs to be transformed into a {@link TreeFilter} to be used for filtering of the element forest.
         * It is assumed that the element matcher may want cooperate with the archive analyzer that produced the element
         * forest to correctly set up the filter.
         *
         * @param archiveAnalyzer the archive analyzer that produced the element forest that will be filtered by the
         *                        return tree filter
         * @return a tree filter to use for filtering the forest or null if the recipe is not applicable to elements of
         * the provided archive analyzer
         */
        @Nullable
        TreeFilter filterFor(ArchiveAnalyzer archiveAnalyzer);
    }
}
