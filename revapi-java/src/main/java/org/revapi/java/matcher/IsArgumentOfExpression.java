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

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.lang.model.type.TypeMirror;

import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

public class IsArgumentOfExpression extends AbstractFullScanRequiringExpression {
    private final Integer order;

    IsArgumentOfExpression(MatchExpression scan, Integer order) {
        super(scan);
        this.order = order;
    }

    @Override
    protected FilterMatch matchesAfterScan(JavaModelElement element) {
        return matches(typeOf(element));
    }

    @Override
    protected FilterMatch matchesAfterScan(JavaAnnotationElement element) {
        return matches(typeOf(element));
    }

    @Override
    protected FilterMatch matchesAfterScan(AnnotationAttributeElement element) {
        return matches(typeOf(element));
    }

    @Override
    protected FilterMatch matchesAfterScan(TypeParameterElement element) {
        return matches(typeOf(element));
    }

    private FilterMatch matches(JavaTypeElement typeElement) {
        if (typeElement == null) {
            return FilterMatch.DOESNT_MATCH;
        }

        String type = Util.toUniqueString(typeElement.getModelRepresentation());

        Stream<JavaMethodElement> subMatches = getMatchedInScan().stream()
                .filter(e -> e instanceof JavaMethodElement)
                .map(e -> (JavaMethodElement) e);

        Predicate<TypeMirror> test = t -> Util.toUniqueString(t).equals(type);

        Stream<TypeMirror> params;

        if (order == null) {
            params = subMatches.flatMap(m -> m.getModelRepresentation().getParameterTypes().stream());
        } else {
            params = subMatches.flatMap(m -> {
                List<? extends TypeMirror> paramTypes = m.getModelRepresentation().getParameterTypes();

                if (paramTypes.size() < order) {
                    return Stream.empty();
                } else {
                    return Stream.of(paramTypes.get(order));
                }
            });
        }

        return FilterMatch.fromBoolean(params.anyMatch(test));
    }
}
