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

import javax.lang.model.type.TypeMirror;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * Represents the match parsed from the input recipe.
 *
 * @see JavaElementMatcher#compile(String)
 *
 * @author Lukas Krejci
 */
interface MatchExpression {
    default FilterMatch matches(ElementGateway.AnalysisStage stage, JavaElement element) {
        if (element instanceof JavaAnnotationElement) {
            return matches(stage, (JavaAnnotationElement) element);
        } else if (element instanceof TypeParameterElement) {
            return matches(stage, (TypeParameterElement) element);
        } else if (element instanceof JavaModelElement) {
            return matches(stage, (JavaModelElement) element);
        } else if (element instanceof AnnotationAttributeElement) {
            return matches(stage, (AnnotationAttributeElement) element);
        } else {
            return FilterMatch.DOESNT_MATCH;
        }
    }

    FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element);

    FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation);

    /**
     * This method is here for special cases where we cannot match against a model element.
     * For example matching a void or primitive type.
     *
     * <p>By default this returns false and should be overridden in {@link StringExpression} and
     * {@link PatternExpression} or any other expression that cannot be further decomposed.
     *
     * @param stage
     * @param type the type mirror to match
     */
    default FilterMatch matches(ElementGateway.AnalysisStage stage, TypeMirror type) {
        return FilterMatch.DOESNT_MATCH;
    }

    /**
     * Only used by match expressions for the annotation attributes
     * @param stage
     * @param attribute the attribute to match
     */
    FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute);

    FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter);
}
