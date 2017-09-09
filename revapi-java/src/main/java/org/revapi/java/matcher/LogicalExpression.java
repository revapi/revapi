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
    public boolean matches(JavaModelElement element) {
        return applyOperator(left.matches(element), right.matches(element));
    }

    @Override
    public boolean matches(AnnotationAttributeElement attribute) {
        return applyOperator(left.matches(attribute), right.matches(attribute));
    }

    @Override
    public boolean matches(TypeParameterElement typeParameter) {
        return applyOperator(left.matches(typeParameter), right.matches(typeParameter));
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        return applyOperator(left.matches(annotation), right.matches(annotation));
    }

    private boolean applyOperator(boolean left, boolean right) {
        switch (operator) {
            case AND:
                return left && right;
            case OR:
                return left || right;
            default:
                return false;
        }
    }
}
