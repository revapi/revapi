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
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class ElementKindExtractor implements DataExtractor<String> {
    @Override
    public String extract(JavaModelElement element) {
        ElementKind kind = element.getDeclaringElement().getKind();
        switch (kind) {
            //not supported ATM
            //case PACKAGE:
            //    return "package";
            case ENUM:
                return "enum";
            case CLASS:
                return "class";
            case ANNOTATION_TYPE:
                return "annotationType";
            case INTERFACE:
                return "interface";
            case ENUM_CONSTANT:
                return "enumConstant";
            case FIELD:
                return "field";
            case PARAMETER:
                return "parameter";
            case METHOD:
                return "method";
            case CONSTRUCTOR:
                return "constructor";
            case TYPE_PARAMETER:
                return "typeParameter";
            default:
                throw new IllegalArgumentException("Unsupported element kind: '" + kind + "'.");
        }
    }

    @Override
    public String extract(JavaAnnotationElement element) {
        return "annotation";
    }

    @Override
    public String extract(TypeMirror type) {
        switch (type.getKind()) {
            case INT:
                return "int";
            case BOOLEAN:
                return "boolean";
            case BYTE:
                return "byte";
            case CHAR:
                return "char";
            case LONG:
                return "long";
            case DOUBLE:
                return "double";
            case FLOAT:
                return "float";
            case VOID:
                return "void";
            case SHORT:
                return "short";
            default:
                throw new IllegalStateException("Only void and primitive types supported but type mirror with kind "
                        + type.getKind() + " encountered");
        }

    }

    @Override
    public String extract(AnnotationAttributeElement element) {
        return "attribute";
    }

    @Override
    public String extract(TypeParameterElement element) {
        return "typeParameter";
    }

    @Override
    public String extract(AnnotationValue value) {
        return "attributeValue";
    }

    @Override
    public Class<String> extractedType() {
        return String.class;
    }
}
