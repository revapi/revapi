package org.revapi.java.model;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MissingTypeElement implements javax.lang.model.element.TypeElement {
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

            NameImpl name1 = (NameImpl) o;

            if (!name.equals(name1.name)) {
                return false;
            }

            return true;
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

    private NoType noType = new NoType() {
        @Override
        public TypeKind getKind() {
            return TypeKind.NONE;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitNoType(this, p);
        }
    };

    private ErrorType errorType = new ErrorType() {
        @Override
        public javax.lang.model.element.Element asElement() {
            return MissingTypeElement.this;
        }

        @Override
        public TypeMirror getEnclosingType() {
            return noType;
        }

        @Override
        public List<? extends TypeMirror> getTypeArguments() {
            return Collections.emptyList();
        }

        @Override
        public TypeKind getKind() {
            return TypeKind.ERROR;
        }

        @Override
        public <R, P> R accept(TypeVisitor<R, P> v, P p) {
            return v.visitError(this, p);
        }
    };

    private final String qualifiedName;

    public MissingTypeElement(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    @Override
    public List<? extends Element> getEnclosedElements() {
        return Collections.emptyList();
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
        return Collections.emptyList();
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
        return dotIdx == -1 ? new NameImpl(qualifiedName) :
            new NameImpl(qualifiedName.substring(dotIdx + 1));
    }

    @Override
    public TypeMirror getSuperclass() {
        return noType;
    }

    @Override
    public List<? extends TypeMirror> getInterfaces() {
        return Collections.emptyList();
    }

    @Override
    public List<? extends TypeParameterElement> getTypeParameters() {
        return Collections.emptyList();
    }

    @Override
    public Element getEnclosingElement() {
        //this does not follow the spec, but let's hope that won't be a problem...
        return null;
    }

    @Override
    public String toString() {
        return qualifiedName;
    }
}
