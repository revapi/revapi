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

import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */ //    private interface MatcherInstance<T> {
//        boolean matches(T value);
//
//        default boolean supports(Class<?> type) {
//            ParameterizedType ptype = (ParameterizedType) this.getClass().getGenericInterfaces()[0];
//            return type.getName().equals(ptype.getActualTypeArguments()[0].getTypeName());
//        }
//
//        @SuppressWarnings("unchecked")
//        static <X> MatcherInstance<X> ifCompatible(MatcherInstance<?> inst, Class<X> type) {
//            if (inst.supports(type)) {
//                return (MatcherInstance<X>) inst;
//            } else {
//                return null;
//            }
//        }
//    }
//
//        private static class StringMatcher implements MatcherInstance<String> {
//        private final String string;
//
//        private StringMatcher(String string) {
//            this.string = string;
//        }
//
//        @Override
//        public boolean matches(String value) {
//            switch (operator) {
//                case EQUALS:
//                    return string.equals(value);
//                case NOT_EQUALS:
//                    return !string.equals(value);
//                default:
//                    throw new IllegalArgumentException("String comparison only supports '=' and '!=' operators.");
//            }
//        }
//    }
//
//    private static class PatternMatcher implements MatcherInstance<String> {
//        private final Pattern pattern;
//
//        private PatternMatcher(Pattern pattern) {
//            this.pattern = pattern;
//        }
//
//        @Override
//        public boolean matches(String value) {
//            switch (operator) {
//                case EQUALS:
//                    return pattern.matcher(value).matches();
//                case NOT_EQUALS:
//                    return !pattern.matcher(value).matches();
//                default:
//                    throw new IllegalArgumentException("Regex comparison only supports '=' and '!=' operators.");
//            }
//        }
//    }
//
//    private static final class IndexMatcher implements MatcherInstance<Integer> {
//        private final int index;
//
//        private IndexMatcher(int index) {
//            this.index = index;
//        }
//
//        @Override
//        public boolean matches(Integer value) {
//            switch (operator) {
//                case EQUALS:
//                    return index == value;
//                case NOT_EQUALS:
//                    return index != value;
//                case GE:
//                    return index >= value;
//                case GT:
//                    return index > value;
//                case LE:
//                    return index <= value;
//                case LT:
//                    return index < value;
//                default:
//                    throw new IllegalArgumentException("Unsupported operator: " + operator);
//            }
//        }
//    }
//
//    private static final class ElementKindExtractor implements DataExtractor<String> {
//        @Override
//        public String extract(JavaElement element) {
//            if (element instanceof JavaModelElement) {
//                ElementKind kind = ((JavaModelElement) element).getDeclaringElement().getKind();
//                switch (kind) {
//                    //not supported ATM
//                    //case PACKAGE:
//                    //    return "package";
//                    case ENUM:
//                        return "enum";
//                    case CLASS:
//                        return "class";
//                    case ANNOTATION_TYPE:
//                        return "annotationType";
//                    case INTERFACE:
//                        return "interface";
//                    case ENUM_CONSTANT:
//                        return "enumConstant";
//                    case FIELD:
//                        return "field";
//                    case PARAMETER:
//                        return "parameter";
//                    case METHOD:
//                        return "method";
//                    case CONSTRUCTOR:
//                        return "constructor";
//                    //not supported ATM
//                    //case TYPE_PARAMETER:
//                    //    return "typeParameter";
//                    default:
//                        throw new IllegalArgumentException("Unsupported element kind: '" + kind + "'.");
//                }
//            } else if (element instanceof JavaAnnotationElement) {
//                return "annotation";
//            } else {
//                throw new IllegalArgumentException("Cannot find an element kind of element of type '" + element.getClass() + "'.");
//            }
//        }
//
//        @Override
//        public Class<String> extractedType() {
//            return String.class;
//        }
//    }
//
//    private static final class ElementPackageExtractor implements DataExtractor<String> {
//        @Override
//        public String extract(JavaElement element) {
//            JavaTypeElement type = element instanceof JavaTypeElement
//                    ? (JavaTypeElement) element
//                    : findParentWithType(element, JavaTypeElement.class);
//
//            while (type != null && type.getParent() != null) {
//                type = findParentWithType(type, JavaTypeElement.class);
//            }
//
//            if (type == null) {
//                return null;
//            } else {
//                PackageElement pkg = (PackageElement) type.getDeclaringElement().getEnclosingElement();
//                return pkg == null ? null : pkg.getQualifiedName().toString();
//            }
//        }
//
//        @Override
//        public Class<String> extractedType() {
//            return String.class;
//        }
//    }
//
//    private static final class ElementClassExtractor implements DataExtractor<String> {
//
//        @Override
//        public String extract(JavaElement element) {
//            JavaTypeElement type = element instanceof JavaTypeElement
//                    ? (JavaTypeElement) element
//                    : findParentWithType(element, JavaTypeElement.class);
//
//            return type == null ? null : Util.toHumanReadableString(type.getModelRepresentation());
//        }
//
//        @Override
//        public Class<String> extractedType() {
//            return String.class;
//        }
//    }
//
//    private static final class SimpleNameExtractor implements DataExtractor<String> {
//
//        @Override
//        public String extract(JavaElement element) {
//            if (element instanceof JavaModelElement) {
//                return ((JavaModelElement) element).getDeclaringElement().getSimpleName().toString();
//            } else if (element instanceof JavaAnnotationElement) {
//                return ((JavaAnnotationElement) element).getAnnotation().getAnnotationType().asElement().getSimpleName().toString();
//            } else {
//                return null;
//            }
//        }
//
//        @Override
//        public Class<String> extractedType() {
//            return String.class;
//        }
//    }
//
final class SignatureExtractor implements DataExtractor<String> {

    @Override
    public String extract(JavaModelElement element) {
        return Util.toHumanReadableString(element.getModelRepresentation());
    }

    @Override
    public String extract(JavaAnnotationElement element) {
        return element.getFullHumanReadableString();
    }

    @Override
    public String extract(TypeMirror type) {
        return Util.toHumanReadableString(type);
    }

    @Override
    public Class<String> extractedType() {
        return String.class;
    }
}
