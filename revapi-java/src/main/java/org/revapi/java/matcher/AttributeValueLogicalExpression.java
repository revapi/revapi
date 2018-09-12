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

import javax.lang.model.element.AnnotationValue;

import org.revapi.Archive;
import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 */
final class AttributeValueLogicalExpression extends AbstractAttributeValueExpression {
    private final AbstractAttributeValueExpression left;
    private final LogicalOperator operator;
    private final AbstractAttributeValueExpression right;

    AttributeValueLogicalExpression(AbstractAttributeValueExpression left, LogicalOperator operator, AbstractAttributeValueExpression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationValue value, Archive archive,
            ProbingEnvironment env) {
        switch (operator) {
            case AND:
                return left.matches(stage, value, archive, env).and(() -> right.matches(stage, value, archive, env));
            case OR:
                return left.matches(stage, value, archive, env).or(() -> right.matches(stage, value, archive, env));
            default:
                return FilterMatch.DOESNT_MATCH;
        }

    }
}
