/*
 * Copyright 2015-2017 Lukas Krejci
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
 *
 */

package org.revapi.java.matcher;

import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;

/**
 * @author Lukas Krejci
 */
final class HasSuperTypeExpression implements MatchExpression {
    private final MatchExpression superTypeExpression;
    private final boolean direct;

    HasSuperTypeExpression(boolean direct, MatchExpression superTypeExpression) {
        this.superTypeExpression = superTypeExpression;
        this.direct = direct;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        if (!(element instanceof JavaTypeElement)) {
            return false;
        }

        TypeEnvironment env = element.getTypeEnvironment();

        TypeMirror superType = ((JavaTypeElement) element).getDeclaringElement().getSuperclass();

        return superTypeMatches(env, superType);
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        return false;
    }

    @Override
    public boolean matches(AnnotationAttributeElement attribute) {
        return false;
    }

    @Override
    public boolean matches(TypeParameterElement typeParameter) {
        TypeEnvironment env = typeParameter.getTypeEnvironment();

        TypeMirror tp = typeParameter.getType();

        return false;
    }

    private boolean superTypeMatches(TypeEnvironment env, TypeMirror superType) {
        JavaTypeElement superTypeElement = superType == null ? null : env.getModelElement(superType);

        if (superTypeElement == null) {
            return false;
        }

        if (direct) {
            return superTypeExpression.matches(superTypeElement);
        }

        while (superTypeElement != null) {
            if (superTypeExpression.matches(superTypeElement)) {
                return true;
            }

            superType = superTypeElement.getDeclaringElement().getSuperclass();

            superTypeElement = superType == null ? null : env.getModelElement(superType);
        }

        return false;
    }
}
