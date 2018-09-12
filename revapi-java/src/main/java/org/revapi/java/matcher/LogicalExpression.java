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

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class LogicalExpression implements MatchExpression {
    private final MatchExpression left;
    private final MatchExpression right;
    private final LogicalOperator operator;

    LogicalExpression(MatchExpression left, LogicalOperator operator, MatchExpression right) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        return applyOperator(left.matches(stage, element), right.matches(stage, element));
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
        return applyOperator(left.matches(stage, attribute), right.matches(stage, attribute));
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return applyOperator(left.matches(stage, typeParameter), right.matches(stage, typeParameter));
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return applyOperator(left.matches(stage, annotation), right.matches(stage, annotation));
    }

    private FilterMatch applyOperator(FilterMatch left, FilterMatch right) {
        switch (operator) {
            case AND:
                return left.and(right);
            case OR:
                return left.or(right);
            default:
                return FilterMatch.DOESNT_MATCH;
        }
    }
}
