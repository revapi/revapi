/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.java.model;

import static java.util.Collections.emptyList;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class MissingTypeElement implements javax.lang.model.element.TypeElement {
    public static final NoType NO_TYPE = new NoType() {
        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return null;
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return (A[]) Array.newInstance(annotationType, 0);
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.NONE;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }
    };

    private javax.lang.model.type.ErrorType errorType = new ErrorType();

    private final String qualifiedName;
    private final PackageElement pkg;

    public static boolean isMissing(Element e) {
        return e instanceof MissingTypeElement;
    }

    public static boolean isMissing(TypeMirror type) {
        return type == NO_TYPE || type instanceof ErrorType;
    }

    @Override
    public List<? extends Element> getEnclosedElements() {
        return emptyList();
    }

    public MissingTypeElement(PackageElement containingPackage, String qualifiedName) {
        this.qualifiedName = qualifiedName;
        this.pkg = containingPackage;
    }

    @Override
    public <R, P> R accept(ElementVisitor<R, P> v, P p) {
        return v.visitType(this, p);
    }

    @Override
    public NestingKind getNestingKind() {
        return NestingKind.TOP_LEVEL;
    }

    @Override
    public Name getQualifiedName() {
        return new NameImpl(qualifiedName);
    }

    @Override
    public TypeMirror asType() {
        return errorType;
    }

    @Override
    public ElementKind getKind() {
        return ElementKind.CLASS;
    }

    @Override
    public List<? extends AnnotationMirror> getAnnotationMirrors() {
        return emptyList();
    }

    @Override
    public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
        return null;
    }

    @Override
    public Set<Modifier> getModifiers() {
        return Collections.emptySet();
    }

    @Override
    public Name getSimpleName() {
        int dotIdx = qualifiedName.lastIndexOf('.');
        return dotIdx == -1 ? new NameImpl(qualifiedName) : new NameImpl(qualifiedName.substring(dotIdx + 1));
    }

    @Override
    public TypeMirror getSuperclass() {
        return NO_TYPE;
    }

    @Override
    public List<? extends TypeMirror> getInterfaces() {
        return emptyList();
    }

    @Override
    public List<? extends TypeParameterElement> getTypeParameters() {
        return emptyList();
    }

    @Override
    public Element getEnclosingElement() {
        return pkg;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
        return (A[]) Array.newInstance(annotationType, 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MissingTypeElement that = (MissingTypeElement) o;
        return qualifiedName.equals(that.qualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualifiedName);
    }

    @Override
    public String toString() {
        return qualifiedName;
    }

    private static class NameImpl implements Name {
        private final String name;

        private NameImpl(String name) {
            this.name = name;
        }

        @Override
        public boolean contentEquals(CharSequence cs) {
            if (cs.length() != name.length()) {
                return false;
            }

            for (int i = 0; i < cs.length(); ++i) {
                if (cs.charAt(i) != name.charAt(i)) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public int length() {
            return name.length();
        }

        @Override
        public char charAt(int index) {
            return name.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return name.subSequence(start, end);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            NameImpl other = (NameImpl) o;

            return name.equals(other.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public final class ErrorType implements javax.lang.model.type.ErrorType {
        @Override
        public Element asElement() {
            return MissingTypeElement.this;
        }

        @Override
        public TypeMirror getEnclosingType() {
            return NO_TYPE;
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return emptyList();
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.ERROR;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitError(this, p);
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }

        @Override
        public List<? extends AnnotationMirror> getAnnotationMirrors() {
            return emptyList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationType) {
            return (A[]) Array.newInstance(annotationType, 0);
        }
    }
}
