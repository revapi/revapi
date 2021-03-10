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
package org.revapi.java.spi;

import java.util.Map;
import java.util.Set;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

/**
 * Elements in the element forest that represent Java types, will implement this interface.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public interface JavaTypeElement extends JavaModelElement {

    @Override
    DeclaredType getModelRepresentation();

    @Override
    TypeElement getDeclaringElement();

    /**
     * @return the set of "places" where this type element is used
     */
    Set<UseSite> getUseSites();

    /**
     * This provides the types used by this type. The keys are the types of use, values are maps from the used type to
     * the set of concrete users of the type (the users represent some child of this element).
     *
     * @return the types used by this type
     */
    Map<UseSite.Type, Map<JavaTypeElement, Set<JavaModelElement>>> getUsedTypes();

    /**
     * Visits the uses of the provided type. The visit will stop as soon as a non-null value is returned from the
     * visitor, even if some use sites are left unvisited.
     *
     * @param <R>
     *            the return type (use {@link java.lang.Void} for no return type)
     * @param <P>
     *            the type of the parameter (use {@link java.lang.Void} for no particular type)
     * @param visitor
     *            the visitor
     * @param parameter
     *            the parameter to supply to the visitor
     *
     * @return the value returned by the visitor
     */
    default <R, P> R visitUseSites(UseSite.Visitor<R, P> visitor, P parameter) {
        DeclaredType type = getModelRepresentation();
        for (UseSite u : getUseSites()) {
            R ret = visitor.visit(type, u, parameter);
            if (ret != null) {
                return ret;
            }
        }

        return visitor.end(type, parameter);
    }

    /**
     * @return true if this type was found to be a part of the API, false otherwise
     */
    boolean isInAPI();

    /**
     * @return true, if the class is not accessible in and of itself but is dragged into the API by a significant use.
     */
    boolean isInApiThroughUse();
}
