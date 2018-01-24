/*
 * Copyright 2014-2017 Lukas Krejci
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

import javax.lang.model.type.TypeMirror;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
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
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        if (!(element instanceof JavaTypeElement)) {
            return FilterMatch.DOESNT_MATCH;
        }

        TypeEnvironment env = element.getTypeEnvironment();

        TypeMirror superType = ((JavaTypeElement) element).getDeclaringElement().getSuperclass();

        return superTypeMatches(stage, env, superType);
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

    private FilterMatch superTypeMatches(ElementGateway.AnalysisStage stage, TypeEnvironment env, TypeMirror superType) {
        JavaTypeElement superTypeElement = superType == null ? null : env.getModelElement(superType);

        if (superTypeElement == null) {
            return FilterMatch.DOESNT_MATCH;
        }

        if (direct) {
            return superTypeExpression.matches(stage, superTypeElement);
        }

        FilterMatch ret = FilterMatch.DOESNT_MATCH;

        while (superTypeElement != null) {
            ret = ret.or(superTypeExpression.matches(stage, superTypeElement));

            if (ret != FilterMatch.DOESNT_MATCH) {
                return ret;
            }

            superType = superTypeElement.getDeclaringElement().getSuperclass();

            superTypeElement = superType == null ? null : env.getModelElement(superType);
        }

        return ret;
    }
}
