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

import java.util.Objects;

import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class StringExpression implements MatchExpression {
    private final DataExtractor<String> extractor;
    private final String value;

    StringExpression(DataExtractor<String> extractor, String value) {
        this.extractor = extractor;
        this.value = value;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        return Objects.equals(value, extractor.extract(element));
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        return Objects.equals(value, extractor.extract(annotation));
    }

    @Override
    public boolean matches(TypeMirror type) {
        return Objects.equals(value, extractor.extract(type));
    }

    @Override
    public boolean matches(AnnotationAttributeElement attribute) {
        return Objects.equals(value, extractor.extract(attribute));
    }

    @Override
    public boolean matches(TypeParameterElement typeParameter) {
        return Objects.equals(value, extractor.extract(typeParameter));
    }
}
