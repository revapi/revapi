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

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * Interface for extracting some kind of data from a java element. E.g. signature, simple name, parameter index, etc.
 *
 * @author Lukas Krejci
 */
interface DataExtractor<T> {
    T extract(JavaModelElement element);

    T extract(JavaAnnotationElement element);

    /**
     * This is only used for types that are not representable as a model element, ie. null and primitive types.
     *
     * @param type the type
     * @return a representation of the type
     */
    T extract(TypeMirror type);

    T extract(AnnotationAttributeElement element);

    T extract(AnnotationValue value);

    Class<T> extractedType();
}
