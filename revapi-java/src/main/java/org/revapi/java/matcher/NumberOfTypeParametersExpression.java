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

import javax.lang.model.element.Parameterizable;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class NumberOfTypeParametersExpression implements MatchExpression {
    private final ComparisonOperator operator;
    private final int expected;

    NumberOfTypeParametersExpression(ComparisonOperator operator, int expected) {
        this.operator = operator;
        this.expected = expected;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        if (!(element.getDeclaringElement() instanceof Parameterizable)) {
            return FilterMatch.DOESNT_MATCH;
        }

        Parameterizable el = (Parameterizable) element.getDeclaringElement();

        int nofArguments = el.getTypeParameters().size();

        return FilterMatch.fromBoolean(operator.evaluate(nofArguments, expected));
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
