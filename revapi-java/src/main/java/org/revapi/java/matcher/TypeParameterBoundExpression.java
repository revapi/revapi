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

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class TypeParameterBoundExpression implements MatchExpression {
    private final MatchExpression boundMatch;
    private final boolean lowerBound;

    public TypeParameterBoundExpression(MatchExpression boundMatch, boolean lowerBound) {
        this.boundMatch = boundMatch;
        this.lowerBound = lowerBound;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        return false;
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

        return typeParameter.getType().accept(new SimpleTypeVisitor8<Boolean, Void>() {
            @Override
            public Boolean visitTypeVariable(TypeVariable t, Void aVoid) {
                return match(lowerBound ? t.getLowerBound() : t.getUpperBound());
            }

            @Override
            public Boolean visitWildcard(WildcardType t, Void aVoid) {
                return match(lowerBound ? t.getSuperBound() : t.getExtendsBound());
            }

            @Override
            public Boolean visitDeclared(DeclaredType t, Void aVoid) {
                return false;
            }

            private boolean match(TypeMirror boundType) {
                if (boundType == null || boundType.getKind() == TypeKind.NULL) {
                    return false;
                }
                return boundType.accept(new SimpleTypeVisitor8<Boolean, Void>() {
                    @Override
                    protected Boolean defaultAction(TypeMirror e, Void aVoid) {
                        return false;
                    }

                    @Override
                    public Boolean visitDeclared(DeclaredType t, Void aVoid) {
                        TypeElement type = new TypeElement(typeParameter.getTypeEnvironment(), typeParameter.getArchive(),
                                (javax.lang.model.element.TypeElement) t.asElement(), t);
                        type.setParent(typeParameter);

                        return boundMatch.matches(type);
                    }

                    @Override
                    public Boolean visitTypeVariable(TypeVariable t, Void aVoid) {
                        TypeParameterElement tp = new TypeParameterElement(typeParameter.getTypeEnvironment(),
                                typeParameter.getApi(), typeParameter.getArchive(), t);
                        tp.setParent(typeParameter);

                        return boundMatch.matches(tp);
                    }

                    @Override
                    public Boolean visitIntersection(IntersectionType t, Void aVoid) {
                        return t.getBounds().stream().reduce(
                                false,
                                (res, type) -> res || this.visit(type),
                                (v1, v2) -> v1 || v2);
                    }
                }, null);
            }
        }, null);
    }
}
