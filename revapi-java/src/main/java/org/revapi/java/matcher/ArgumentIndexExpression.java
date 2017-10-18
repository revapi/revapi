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

import org.revapi.FilterMatch;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class ArgumentIndexExpression implements MatchExpression {
    private final ComparisonOperator operator;
    private final int expectedIndex;

    ArgumentIndexExpression(ComparisonOperator operator, int expectedIndex) {
        this.operator = operator;
        this.expectedIndex = expectedIndex;
    }

    @Override
    public FilterMatch matches(JavaModelElement element) {
        if (!(element instanceof MethodParameterElement)) {
            return FilterMatch.DOESNT_MATCH;
        }

        int index = ((MethodParameterElement) element).getIndex();

        return FilterMatch.fromBoolean(operator.evaluate(index, expectedIndex));
    }

    @Override
    public FilterMatch matches(JavaAnnotationElement annotation) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public FilterMatch matches(AnnotationAttributeElement attribute) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public FilterMatch matches(TypeParameterElement typeParameter) {
        return FilterMatch.DOESNT_MATCH;
    }
}
