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
import org.revapi.ElementMatcher.Result;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
abstract class AbstractAttributeValueExpression implements MatchExpression {
    @Override
    public final Result matches(JavaModelElement element) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public final Result matches(JavaAnnotationElement annotation) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public final Result matches(AnnotationAttributeElement attribute) {
        return matches(attribute.getAnnotationValue(), attribute.getArchive(), (ProbingEnvironment) attribute.getTypeEnvironment());
    }

    @Override
    public Result matches(TypeParameterElement typeParameter) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public final Result matches(TypeMirror type) {
        return Result.DOESNT_MATCH;
    }

    public Result matches(int index, AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return matches(value, archive, env);
    }

    public abstract Result matches(AnnotationValue value, Archive archive, ProbingEnvironment env);
}
