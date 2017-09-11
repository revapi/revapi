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

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.ElementKindVisitor7;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.ElementMatcher;
import org.revapi.ElementMatcher.Result;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 */
final class ThrowsExpression implements MatchExpression {
    private final MatchExpression throwsMatch;

    ThrowsExpression(MatchExpression throwsMatch) {
        this.throwsMatch = throwsMatch;
    }

    @Override
    public Result matches(JavaModelElement element) {
        List<? extends TypeMirror> thrownTypes = ((JavaMethodElement) element).getModelRepresentation()
                .getThrownTypes();

        if (thrownTypes.isEmpty()) {
            return Result.fromBoolean(throwsMatch == null);
        } else if (throwsMatch == null) {
            return Result.DOESNT_MATCH;
        }

        TypeVisitor<Result, Void> decision = new SimpleTypeVisitor8<Result, Void>(Result.DOESNT_MATCH) {
            @Override
            public Result visitDeclared(DeclaredType t, Void ignored) {
                return t.asElement().accept(new ElementKindVisitor7<Result, Void>(Result.DOESNT_MATCH) {
                    @Override
                    public Result visitType(TypeElement e, Void ignored) {
                        JavaTypeElement modelType = element.getTypeEnvironment().getModelElement(e);
                        if (modelType == null) {
                            return Result.DOESNT_MATCH;
                        } else {
                            return throwsMatch.matches(modelType);
                        }
                    }
                }, null);

            }
        };

        Result ret = Result.DOESNT_MATCH;
        for (TypeMirror t : thrownTypes) {
            Result result = t.accept(decision, null);
            if (result == Result.MATCH) {
                return Result.MATCH;
            } else {
                ret = ret.or(result);
            }
        }

        return ret;
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
        return Result.DOESNT_MATCH;
    }
}
