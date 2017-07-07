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

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
final class SubTypeExpression implements MatchExpression {
    private final MatchExpression superTypeMatch;
    private final boolean onlyDirectSuperTypes;
    private final boolean searchInterfaces;

    public SubTypeExpression(MatchExpression superTypeMatch, boolean onlyDirectSuperTypes, boolean searchInterfaces) {
        this.superTypeMatch = superTypeMatch;
        this.onlyDirectSuperTypes = onlyDirectSuperTypes;
        this.searchInterfaces = searchInterfaces;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        if (!(element instanceof JavaTypeElement)) {
            return false;
        }

        TypeMirror elementType = ((JavaTypeElement) element).getModelRepresentation();
        Types types = element.getTypeEnvironment().getTypeUtils();

        Collection<? extends TypeMirror> candidates;

        if (onlyDirectSuperTypes) {
            List<? extends TypeMirror> superTypes = types.directSupertypes(elementType);

            if (superTypes.isEmpty()) {
                return false;
            }

            if (searchInterfaces) {
                candidates = superTypes.subList(1, superTypes.size());
            } else {
                candidates = Collections.singleton(superTypes.get(0));
            }
        } else {
            if (searchInterfaces) {
                candidates = Util.getAllSuperInterfaces(types, elementType);
            } else {
                candidates = Util.getAllSuperClasses(types, elementType);
            }
        }

        TypeEnvironment typeEnvironment = element.getTypeEnvironment();

        for (TypeMirror t : candidates) {
            JavaTypeElement type = typeEnvironment.getModelElement(t);
            if (type != null && superTypeMatch.matches(type)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        return false;
    }
}
