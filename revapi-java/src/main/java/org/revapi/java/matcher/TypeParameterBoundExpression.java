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

import org.revapi.ElementMatcher;
import org.revapi.ElementMatcher.Result;
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
    public Result matches(JavaModelElement element) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(JavaAnnotationElement annotation) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(AnnotationAttributeElement attribute) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(TypeParameterElement typeParameter) {

        return typeParameter.getType().accept(new SimpleTypeVisitor8<Result, Void>() {
            @Override
            public Result visitTypeVariable(TypeVariable t, Void aVoid) {
                return match(lowerBound ? t.getLowerBound() : t.getUpperBound());
            }

            @Override
            public Result visitWildcard(WildcardType t, Void aVoid) {
                return match(lowerBound ? t.getSuperBound() : t.getExtendsBound());
            }

            @Override
            public Result visitDeclared(DeclaredType t, Void aVoid) {
                return Result.DOESNT_MATCH;
            }

            private Result match(TypeMirror boundType) {
                if (boundType == null || boundType.getKind() == TypeKind.NULL) {
                    return Result.DOESNT_MATCH;
                }
                return boundType.accept(new SimpleTypeVisitor8<Result, Void>() {
                    @Override
                    protected Result defaultAction(TypeMirror e, Void aVoid) {
                        return Result.DOESNT_MATCH;
                    }

                    @Override
                    public Result visitDeclared(DeclaredType t, Void aVoid) {
                        TypeElement type = new TypeElement(typeParameter.getTypeEnvironment(), typeParameter.getArchive(),
                                (javax.lang.model.element.TypeElement) t.asElement(), t);
                        type.setParent(typeParameter);

                        return boundMatch.matches(type);
                    }

                    @Override
                    public Result visitTypeVariable(TypeVariable t, Void aVoid) {
                        TypeParameterElement tp = new TypeParameterElement(typeParameter.getTypeEnvironment(),
                                typeParameter.getApi(), typeParameter.getArchive(), t);
                        tp.setParent(typeParameter);

                        return boundMatch.matches(tp);
                    }

                    @Override
                    public Result visitIntersection(IntersectionType t, Void aVoid) {
                        return t.getBounds().stream().reduce(
                                Result.DOESNT_MATCH,
                                (res, type) -> res.or(this.visit(type)),
                                Result::or);
                    }
                }, null);
            }
        }, null);
    }
}
