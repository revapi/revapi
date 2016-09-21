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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
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
     * This returns true for elements that are included by the means of configuration. I.e. even though they could
     * otherwise be excluded from the API, the user chose to include them.
     * <p>
     * Note that this is not a mere opposite of {@link #isExplicitlyExcluded(Element)}. Both methods can return false
     * for a single element, which means that the inclusion state of that element is implicit. Usually this means that
     * the inclusion is dependent on the parent element.
     *
     * @param element the element to check
     * @return true if this element is explicitly included by configuration, false otherwise
     */
    boolean isExplicitlyIncluded(Element element);

    /**
     * This returns true for elements that are excluded by the means of configuration. I.e. even though they would
     * otherwise be included in the API, they are excluded by the user.
     * <p>
     * It does not mean that the element is to be included in the API checks if this method returns false. That merely
     * means that the user didn't explicitly exclude it and further checks need to be made to establish whether to check
     * the element or not (see for example {@link CheckBase#isAccessible(Element, TypeEnvironment)}).
     *
     * @param element the element to check (might be type, method, whatever)
     * @return true if the the user explicitly excluded this element from the API checks, false otherwise.
     */
    boolean isExplicitlyExcluded(Element element);

    /**
     * Converts between the javax.lang.model and revapi representation of the type. The revapi representation contains
     * some useful methods with "results" computed during the analysis.
     *
     * <p>Note that this will return {@code null} for types that are not part of the revapi representation - namely
     * the types on the bootstrap classpath.
     *
     * @param type the javax.lang.model representation of a type
     * @return the revapi representation type
     */
    @Nullable JavaTypeElement getModelElement(TypeElement type);
}
