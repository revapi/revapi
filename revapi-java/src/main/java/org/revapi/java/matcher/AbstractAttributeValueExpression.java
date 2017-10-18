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

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.type.TypeMirror;

import org.revapi.Archive;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
abstract class AbstractAttributeValueExpression implements MatchExpression {
    @Override
    public final FilterMatch matches(JavaModelElement element) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public final FilterMatch matches(JavaAnnotationElement annotation) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public final FilterMatch matches(AnnotationAttributeElement attribute) {
        return matches(attribute.getAnnotationValue(), attribute.getArchive(), (ProbingEnvironment) attribute.getTypeEnvironment());
    }

    @Override
    public FilterMatch matches(TypeParameterElement typeParameter) {
        return FilterMatch.DOESNT_MATCH;
    }

    @Override
    public final FilterMatch matches(TypeMirror type) {
        return FilterMatch.DOESNT_MATCH;
    }

    public FilterMatch matches(int index, AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return matches(value, archive, env);
    }

    public abstract FilterMatch matches(AnnotationValue value, Archive archive, ProbingEnvironment env);
}
