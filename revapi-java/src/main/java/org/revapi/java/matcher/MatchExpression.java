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
    default FilterMatch matches(JavaElement element) {
        if (element instanceof JavaAnnotationElement) {
            return matches((JavaAnnotationElement) element);
        } else if (element instanceof TypeParameterElement) {
            return matches((TypeParameterElement) element);
        } else if (element instanceof JavaModelElement) {
            return matches((JavaModelElement) element);
        } else if (element instanceof AnnotationAttributeElement) {
            return matches((AnnotationAttributeElement) element);
        } else {
            return FilterMatch.DOESNT_MATCH;
        }
    }

    FilterMatch matches(JavaModelElement element);

    FilterMatch matches(JavaAnnotationElement annotation);

    /**
     * This method is here for special cases where we cannot match against a model element.
     * For example matching a void or primitive type.
     *
     * <p>By default this returns false and should be overridden in {@link StringExpression} and
     * {@link PatternExpression} or any other expression that cannot be further decomposed.
     *
     * @param type the type mirror to match
     */
    default FilterMatch matches(TypeMirror type) {
        return FilterMatch.DOESNT_MATCH;
    }

    /**
     * Only used by match expressions for the annotation attributes
     * @param attribute the attribute to match
     */
    FilterMatch matches(AnnotationAttributeElement attribute);

    FilterMatch matches(TypeParameterElement typeParameter);
}
