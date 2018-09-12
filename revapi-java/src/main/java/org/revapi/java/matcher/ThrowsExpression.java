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
package org.revapi.java.matcher;

import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.ElementKindVisitor7;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
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
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        List<? extends TypeMirror> thrownTypes = ((JavaMethodElement) element).getModelRepresentation()
                .getThrownTypes();

        if (thrownTypes.isEmpty()) {
            return FilterMatch.fromBoolean(throwsMatch == null);
        } else if (throwsMatch == null) {
            return FilterMatch.DOESNT_MATCH;
        }

        TypeVisitor<FilterMatch, Void> decision = new SimpleTypeVisitor8<FilterMatch, Void>(FilterMatch.DOESNT_MATCH) {
            @Override
            public FilterMatch visitDeclared(DeclaredType t, Void ignored) {
                return t.asElement().accept(new ElementKindVisitor7<FilterMatch, Void>(FilterMatch.DOESNT_MATCH) {
                    @Override
                    public FilterMatch visitType(TypeElement e, Void ignored) {
                        JavaTypeElement modelType = element.getTypeEnvironment().getModelElement(e);
                        if (modelType == null) {
                            return FilterMatch.DOESNT_MATCH;
                        } else {
                            return throwsMatch.matches(stage, modelType);
                        }
                    }
                }, null);

            }
        };

        FilterMatch ret = FilterMatch.DOESNT_MATCH;
        for (TypeMirror t : thrownTypes) {
            FilterMatch result = t.accept(decision, null);
            if (result == FilterMatch.MATCHES) {
                return FilterMatch.MATCHES;
            } else {
                ret = ret.or(result);
            }
        }

        return ret;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return FilterMatch.DOESNT_MATCH;
    }
}
