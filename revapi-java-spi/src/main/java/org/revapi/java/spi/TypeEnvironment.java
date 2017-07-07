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
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
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
     * Returns full Revapi representation of the provided java type. This sort of a reverse to
     * {@link JavaModelElement#getDeclaringElement()} on the type level.
     *
     * <p>For elements that were not fully analyzed (for example the types included in the Java runtime)
     * a stub implementation is returned that reflects the findings of the analysis but may not reflect
     * the full state of the type (for example use sites not encountered during the analysis will be missing, etc.)
     *
     * @param javaType the java type
     * @return revapi model element or null if it cannot be const
     */
    JavaTypeElement getModelElement(TypeElement javaType);

    /**
     * A variant of {@link #getModelElement(TypeElement)} that accepts a type mirror.
     *
     * Returns null if the provided type mirror doesn't represent a declared type.
     *
     * @param type the type mirror to try and find the model representation of
     *
     * @return the model representation of the type or null if it cannot be found or the type is not a declared type
     *
     * @see #getModelElement(TypeElement)
     */
    default JavaTypeElement getModelElement(TypeMirror type) {
        return type.accept(new SimpleTypeVisitor8<JavaTypeElement, Void>() {
            @Override
            public JavaTypeElement visitDeclared(DeclaredType t, Void o) {
                Element el = t.asElement();
                if (el instanceof TypeElement) {
                    return getModelElement((TypeElement) el);
                } else {
                    return null;
                }
            }
        }, null);
    }

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
     * the element or not (see for example {@link CheckBase#isAccessible(JavaModelElement)}).
     *
     * @param element the element to check (might be type, method, whatever)
     * @return true if the the user explicitly excluded this element from the API checks, false otherwise.
     */
    boolean isExplicitlyExcluded(Element element);
}
