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

import javax.lang.model.element.AnnotationValue;

import org.revapi.Archive;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 */
final class NegatingAttributeValueExpression extends AbstractAttributeValueExpression {
    private final AbstractAttributeValueExpression match;

    NegatingAttributeValueExpression(AbstractAttributeValueExpression match) {
        this.match = match;
    }

    @Override
    public FilterMatch matches(int index, AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return match.matches(index, value, archive, env).negate();
    }

    @Override
    public FilterMatch matches(AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return match.matches(value, archive, env).negate();
    }
}
