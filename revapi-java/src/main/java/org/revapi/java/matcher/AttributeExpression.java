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
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.ElementMatcher.Result;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class AttributeExpression implements MatchExpression {
    private final Function<AnnotationAttributeElement, Result> filter;
    private final boolean onlyExplicitValues;

    public AttributeExpression(@Nullable MatchExpression attributeNameMatch, @Nullable MatchExpression valueMatch,
                               boolean onlyExplicitValues) {
        this.onlyExplicitValues = onlyExplicitValues;

        Function<AnnotationAttributeElement, Result> nameFilter = attributeNameMatch == null
                ? __ -> Result.MATCH
                : attributeNameMatch::matches;

        Function<AnnotationAttributeElement, Supplier<Result>> valueFilter = valueMatch == null
                ? __ -> () -> Result.MATCH
                : e -> () -> valueMatch.matches(e);

        filter = e -> nameFilter.apply(e).and(valueFilter.apply(e));
    }

    @Override
    public Result matches(JavaModelElement element) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(JavaAnnotationElement annotation) {
        AnnotationMirror am = annotation.getAnnotation();
        Map<? extends ExecutableElement, ? extends AnnotationValue> attrs;

        if (onlyExplicitValues) {
            attrs = am.getElementValues();
        } else {
            attrs = annotation.getTypeEnvironment().getElementUtils()
                    .getElementValuesWithDefaults(am);
        }

        Result res = Result.DOESNT_MATCH;
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : attrs.entrySet()) {
            AnnotationAttributeElement el = new AnnotationAttributeElement(annotation, e.getKey(), e.getValue());

            res = res.or(filter.apply(el));

            if (res == Result.MATCH) {
                return res;
            }
        }

        return res;
    }

    @Override
    public Result matches(AnnotationAttributeElement attribute) {
        return filter.apply(attribute);
    }

    @Override
    public Result matches(TypeParameterElement typeParameter) {
        return Result.DOESNT_MATCH;
    }

    @Override
    public Result matches(TypeMirror type) {
        return Result.DOESNT_MATCH;
    }
}
