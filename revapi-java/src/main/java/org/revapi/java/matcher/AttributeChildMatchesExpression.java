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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import org.revapi.Archive;
import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.model.AnnotationElement;

/**
 * @author Lukas Krejci
 */
final class AttributeChildMatchesExpression extends AbstractAttributeValueExpression {
    private final AttributeExpression childAttributeMatch;

    public AttributeChildMatchesExpression(AttributeExpression childAttributeMatch) {
        this.childAttributeMatch = childAttributeMatch;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationValue value, Archive archive,
            ProbingEnvironment env) {
        return value.accept(new SimpleAnnotationValueVisitor8<FilterMatch, Void>() {
            @Override
            protected FilterMatch defaultAction(Object o, Void aVoid) {
                return FilterMatch.DOESNT_MATCH;
            }

            @Override
            public FilterMatch visitAnnotation(AnnotationMirror a, Void aVoid) {
                return childAttributeMatch.matches(stage, new AnnotationElement(env, archive, a));
            }
        }, null);
    }
}
