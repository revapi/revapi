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

import javax.lang.model.type.TypeMirror;

import org.revapi.ElementMatcher;
import org.revapi.ElementMatcher.Result;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * Represents the match parsed from the input recipe.
 *
 * @see JavaElementMatcher#matches(String, org.revapi.Element)
 *
 * @author Lukas Krejci
 */
interface MatchExpression {
    default Result matches(JavaElement element) {
        if (element instanceof JavaAnnotationElement) {
            return matches((JavaAnnotationElement) element);
        } else if (element instanceof TypeParameterElement) {
            return matches((TypeParameterElement) element);
        } else if (element instanceof JavaModelElement) {
            return matches((JavaModelElement) element);
        } else if (element instanceof AnnotationAttributeElement) {
            return matches((AnnotationAttributeElement) element);
        } else {
            return Result.DOESNT_MATCH;
        }
    }

    Result matches(JavaModelElement element);

    Result matches(JavaAnnotationElement annotation);

    /**
     * This method is here for special cases where we cannot match against a model element.
     * For example matching a void or primitive type.
     *
     * <p>By default this returns false and should be overridden in {@link StringExpression} and
     * {@link PatternExpression} or any other expression that cannot be further decomposed.
     *
     * @param type the type mirror to match
     */
    default Result matches(TypeMirror type) {
        return Result.DOESNT_MATCH;
    }

    /**
     * Only used by match expressions for the annotation attributes
     * @param attribute the attribute to match
     */
    Result matches(AnnotationAttributeElement attribute);

    Result matches(TypeParameterElement typeParameter);
}