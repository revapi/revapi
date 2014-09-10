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

package org.revapi.java.spi;

import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * This interface mimics (to an extent) the {@link javax.annotation.processing.ProcessingEnvironment} and
 * serves the same purpose. To give a context to the API checking classes.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface TypeEnvironment {

    /**
     * The instance of the utility class to examine the elements of the API (types, methods, etc.)
     *
     * @see javax.lang.model.util.Elements
     */
    @Nonnull
    Elements getElementUtils();

    /**
     * The instance of the utility class to examine the types in the API.
     *
     * @see javax.lang.model.util.Types
     */
    @Nonnull
    Types getTypeUtils();

    /**
     * Determines whether given class is explicitly part of the API being checked.
     * This might return false for inner classes or (package) private classes.
     * <p/>
     * The model still contains the classes that are not explicitly part of the API because there still might be checks
     * on them that might be relevant, like annotation or visibility checks.
     *
     * @param type the type to check
     *
     * @return true if the type is explicitly part of the API, false if it is not
     */
    boolean isExplicitPartOfAPI(@Nonnull TypeElement type);

    /**
     * Returns the known use sites of the given type.
     *
     * @param type the type
     *
     * @return the use sites found during the analysis of archives
     */
    @Nonnull
    Set<UseSite> getUseSites(@Nonnull TypeElement type);

    /**
     * Visits all uses of the provided type and recursively all uses of the types of the use sites.
     * <p/>
     * I.e. when the provided type is used as a return type of a method, the visitor will also visit the type that
     * defines the method and all its uses, recursively.
     *
     * @param type      the type to visit uses of
     * @param visitor   the visitor
     * @param parameter the parameter to supply to the visitor
     * @param <R>       the return type (use {@link java.lang.Void} for no return type)
     * @param <P>       the type of the parameter (use {@link java.lang.Void} for no particular type)
     *
     * @return the value returned by the visitor
     */
    @Nullable
    <R, P> R visitUseSites(@Nonnull TypeElement type, @Nonnull UseSite.Visitor<R, P> visitor, @Nullable P parameter);
}
