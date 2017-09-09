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

import java.util.Map;
import java.util.function.Predicate;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class AttributeExpression implements MatchExpression {
    private final @Nullable MatchExpression valueMatch;
    private final @Nullable MatchExpression attributeNameMatch;
    private final boolean onlyExplicitValues;

    public AttributeExpression(@Nullable MatchExpression attributeNameMatch, @Nullable MatchExpression valueMatch,
                               boolean onlyExplicitValues) {
        this.attributeNameMatch = attributeNameMatch;
        this.valueMatch = valueMatch;
        this.onlyExplicitValues = onlyExplicitValues;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        return false;
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        AnnotationMirror am = annotation.getAnnotation();
        Map<? extends ExecutableElement, ? extends AnnotationValue> attrs;

        if (onlyExplicitValues) {
            attrs = am.getElementValues();
        } else {
            attrs = annotation.getTypeEnvironment().getElementUtils()
                    .getElementValuesWithDefaults(am);
        }

        Predicate<AnnotationAttributeElement> nameFilter = attributeNameMatch == null
                ? __ -> true
                : attributeNameMatch::matches;

        Predicate<AnnotationAttributeElement> valueFilter = valueMatch == null
                ? __ -> true
                : valueMatch::matches;

        return attrs.entrySet().stream()
                .map(e -> new AnnotationAttributeElement(annotation, e.getKey(), e.getValue()))
                .anyMatch(nameFilter.and(valueFilter));
    }

    @Override
    public boolean matches(AnnotationAttributeElement attribute) {
        return (attributeNameMatch == null || attributeNameMatch.matches(attribute))
                && (valueMatch == null || valueMatch.matches(attribute));

    }

    @Override
    public boolean matches(TypeParameterElement typeParameter) {
        return false;
    }

    @Override
    public boolean matches(TypeMirror type) {
        return false;
    }
}
