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

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;

/**
 * @author Lukas Krejci
 */
final class HasOuterClassExpression implements MatchExpression {
    private final boolean direct;
    private final MatchExpression outerClassMatch;

    HasOuterClassExpression(boolean direct, MatchExpression outerClassMatch) {
        this.direct = direct;
        this.outerClassMatch = outerClassMatch;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        if (!(element instanceof JavaTypeElement)) {
            return false;
        }

        TypeEnvironment env = element.getTypeEnvironment();

        Element enclosingElement = element.getDeclaringElement().getEnclosingElement();
        if (!(enclosingElement instanceof TypeElement)) {
            return false;
        }

        JavaTypeElement enclosingType = env.getModelElement((TypeElement) enclosingElement);
        if (enclosingType == null) {
            return false;
        }

        if (direct) {
            return outerClassMatch.matches(enclosingType);
        }

        while (enclosingType != null) {
            if (outerClassMatch.matches(enclosingType)) {
                return true;
            }

            enclosingElement = enclosingType.getDeclaringElement().getEnclosingElement();
            if (!(enclosingElement instanceof TypeElement)) {
                enclosingType = null;
            } else {
                enclosingType = env.getModelElement((TypeElement) enclosingElement);
            }
        }

        return false;
    }

    @Override
    public boolean matches(AnnotationAttributeElement attribute) {
        return false;
    }

    @Override
    public boolean matches(TypeParameterElement typeParameter) {
        return false;
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        return false;
    }
}
