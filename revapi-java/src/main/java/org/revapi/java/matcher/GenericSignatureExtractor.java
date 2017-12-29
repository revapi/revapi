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
import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
final class GenericSignatureExtractor implements DataExtractor<String> {
    @Override
    public String extract(JavaModelElement element) {
        return Util.toHumanReadableString(element.getDeclaringElement().asType());
    }

    @Override
    public String extract(JavaAnnotationElement element) {
        return Util.toHumanReadableString(element.getAnnotation());
    }

    @Override
    public String extract(TypeMirror type) {
        return Util.toHumanReadableString(type);
    }

    @Override
    public String extract(AnnotationAttributeElement element) {
        return extract(element.getAttributeMethod().getReturnType());
    }

    @Override
    public String extract(TypeParameterElement element) {
        return extract(element.getType());
    }

    @Override
    public String extract(AnnotationValue value) {
        return "";
    }

    @Override
    public Class<String> extractedType() {
        return String.class;
    }
}
