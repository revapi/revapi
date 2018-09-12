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
package org.revapi.java.spi;

import javax.annotation.Nonnull;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
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

            @Override
            public JavaTypeElement visitIntersection(IntersectionType t, Void aVoid) {
                return t.getBounds().get(0).accept(this, null);
            }

            @Override
            public JavaTypeElement visitTypeVariable(TypeVariable t, Void aVoid) {
                return t.getUpperBound().accept(this, null);
            }

            @Override
            public JavaTypeElement visitWildcard(WildcardType t, Void aVoid) {
                if (t.getSuperBound() != null) {
                    return t.getSuperBound().accept(this, null);
                } else if (t.getExtendsBound() != null) {
                    return t.getExtendsBound().accept(this, null);
                } else {
                    return getModelElement(getElementUtils().getTypeElement("java.lang.Object"));
                }
            }
        }, null);
    }
}
