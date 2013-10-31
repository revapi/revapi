/*
 * Copyright 2013 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java.elements;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public enum JavaElementType {
    CLASS(ClassElement.class),
    METHOD(MethodElement.class),
    FIELD(FieldElement.class),
    METHOD_PARAMETER(MethodParameterElement.class),
    ANNOTATION(AnnotationElement.class),
    ANNOTATION_ATTRIBUTE_ELEMENT(AnnotationAttributeElement.class),
    PRIMITIVE_ANNOTATION_VALUE_ELEMENT(PrimitiveAnnotationValueElement.class),
    ARRAY_ANNOTATION_VALUE_ELEMENT(ArrayAnnotationValueElement.class),
    CLASS_ANNOTATION_VALUE_ELEMENT(ClassAnnotationValueElement.class),
    ENUM_ANNOTATION_VALUE_ELEMENT(EnumAnnotationValueElement.class),
    TYPE_PARAMETER(TypeParameterElement.class);

    private final Class<? extends AbstractJavaElement<?>> myElementType;

    private JavaElementType(Class<? extends AbstractJavaElement<?>> myElementType) {
        this.myElementType = myElementType;
    }

    public Class<? extends AbstractJavaElement<?>> getElementType() {
        return myElementType;
    }

    public static final JavaElementType of(AbstractJavaElement<?> element) {
        Class<?> cls = element.getClass();

        for (JavaElementType t : values()) {
            if (cls.equals(t.myElementType)) {
                return t;
            }
        }

        return null;
    }
}
