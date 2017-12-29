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

import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class NegatingExpression implements MatchExpression {
    private final MatchExpression expr;

    NegatingExpression(MatchExpression expr) {
        this.expr = expr;
    }

    @Override
    public FilterMatch matches(JavaModelElement element) {
        return expr.matches(element).negate();
    }

    @Override
    public FilterMatch matches(TypeParameterElement typeParameter) {
        return expr.matches(typeParameter).negate();
    }

    @Override
    public FilterMatch matches(JavaAnnotationElement annotation) {
        return expr.matches(annotation).negate();
    }

    @Override
    public FilterMatch matches(TypeMirror type) {
        return expr.matches(type).negate();
    }

    @Override
    public FilterMatch matches(AnnotationAttributeElement attribute) {
        return expr.matches(attribute).negate();
    }
}
