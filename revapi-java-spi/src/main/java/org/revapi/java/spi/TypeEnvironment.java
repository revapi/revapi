/*
 * Copyright 2015 Lukas Krejci
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
     * @return The instance of the utility class to examine the elements of the API (types, methods, etc.)
     *
     * @see javax.lang.model.util.Elements
     */
    @Nonnull
    Elements getElementUtils();

    /**
     * @return The instance of the utility class to examine the types in the API.
     *
     * @see javax.lang.model.util.Types
     */
    @Nonnull
    Types getTypeUtils();

    /**
     * Visits the uses of the provided type. The visit will stop as soon as a non-null value is returned
     * from the visitor, even if some use sites are left unvisited.
     *
     * @param <R>       the return type (use {@link java.lang.Void} for no return type)
     * @param <P>       the type of the parameter (use {@link java.lang.Void} for no particular type)
     * @param type      the type to visit uses of
     * @param visitor   the visitor
     * @param parameter the parameter to supply to the visitor
     *
     * @return the value returned by the visitor
     */
    @Nullable
    <R, P> R visitUseSites(@Nonnull TypeElement type, @Nonnull UseSite.Visitor<R, P> visitor, @Nullable P parameter);

    /**
     * Returns the set of subclasses of given type that are declared public.
     * @param type the class
     * @return the set of subclasses or empty set if no (accessible) subclasses are known
     */
    @Nonnull
    Set<TypeElement> getAccessibleSubclasses(@Nonnull TypeElement type);
}
