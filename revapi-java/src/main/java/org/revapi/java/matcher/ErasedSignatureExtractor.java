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
import javax.lang.model.util.Types;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
final class ErasedSignatureExtractor implements DataExtractor<String> {
    @Override
    public String extract(JavaModelElement element) {
        return extract(element.getModelRepresentation(), element.getTypeEnvironment().getTypeUtils());
    }

    @Override
    public String extract(JavaAnnotationElement element) {
        return Util.toHumanReadableString(element.getAnnotation());
    }

    @Override
    public String extract(TypeMirror type) {
        return extract(type, null);
    }

    @Override
    public String extract(AnnotationAttributeElement element) {
        return extract(element.getAttributeMethod().getReturnType(), element.getTypeEnvironment().getTypeUtils());
    }

    @Override
    public String extract(TypeParameterElement element) {
        return extract(element.getType(), element.getTypeEnvironment().getTypeUtils());
    }

    @Override
    public String extract(AnnotationValue value) {
        return "";
    }

    @Override
    public Class<String> extractedType() {
        return String.class;
    }

    private String extract(TypeMirror type, Types types) {
        type = types == null ? type : types.erasure(type);
        return Util.toHumanReadableString(type);
    }
}
