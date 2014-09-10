package org.revapi.java.compilation;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class MissingTypeAwareDelegatingTypes implements Types {

    private static final Logger LOG = LoggerFactory.getLogger(MissingTypeAwareDelegatingTypes.class);

    private final Types types;

    private final IgnoreCompletionFailures.Fn1<Element, TypeMirror> asElement = new IgnoreCompletionFailures.Fn1<Element, TypeMirror>() {
        @Override
        public Element call(TypeMirror t) throws Exception {
            return types.asElement(t);
        }
    };

    private final IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror> isSameType = new IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror>() {
        @Override
        public Boolean call(TypeMirror t1, TypeMirror t2) throws Exception {
            return types.isSameType(t1, t2);
        }
    };

    private final IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror> isSubtype = new IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror>() {
        @Override
        public Boolean call(TypeMirror t1, TypeMirror t2) throws Exception {
            return types.isSubtype(t1, t2);
        }
    };

    private final IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror> isAssignable = new IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror>() {
        @Override
        public Boolean call(TypeMirror t1, TypeMirror t2) throws Exception {
            return types.isAssignable(t1, t2);
        }
    };

    private final IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror> contains = new IgnoreCompletionFailures.Fn2<Boolean, TypeMirror, TypeMirror>() {
        @Override
        public Boolean call(TypeMirror t1, TypeMirror t2) throws Exception {
            return types.contains(t1, t2);
        }
    };

    private final IgnoreCompletionFailures.Fn2<Boolean, ExecutableType, ExecutableType> isSubsignature = new IgnoreCompletionFailures.Fn2<Boolean, ExecutableType, ExecutableType>() {
        @Override
        public Boolean call(ExecutableType t1, ExecutableType t2) throws Exception {
            return types.isSubsignature(t1, t2);
        }
    };

    private final IgnoreCompletionFailures.Fn1<List<? extends TypeMirror>, TypeMirror> directSuperTypes = new IgnoreCompletionFailures.Fn1<List<? extends TypeMirror>, TypeMirror>() {
        @Override
        public List<? extends TypeMirror> call(TypeMirror typeMirror) throws Exception {
            return types.directSupertypes(typeMirror);
        }
    };

    private final IgnoreCompletionFailures.Fn1<TypeMirror, TypeMirror> erasure = new IgnoreCompletionFailures.Fn1<TypeMirror, TypeMirror>() {
        @Override
        public TypeMirror call(TypeMirror typeMirror) throws Exception {
            return types.erasure(typeMirror);
        }
    };

    private final IgnoreCompletionFailures.Fn1<TypeElement, PrimitiveType> boxedClass = new IgnoreCompletionFailures.Fn1<TypeElement, PrimitiveType>() {
        @Override
        public TypeElement call(PrimitiveType primitiveType) throws Exception {
            return types.boxedClass(primitiveType);
        }
    };

    private final IgnoreCompletionFailures.Fn1<PrimitiveType, TypeMirror> unboxedType = new IgnoreCompletionFailures.Fn1<PrimitiveType, TypeMirror>() {
        @Override
        public PrimitiveType call(TypeMirror typeMirror) throws Exception {
            return types.unboxedType(typeMirror);
        }
    };

    private final IgnoreCompletionFailures.Fn1<TypeMirror, TypeMirror> capture = new IgnoreCompletionFailures.Fn1<TypeMirror, TypeMirror>() {
        @Override
        public TypeMirror call(TypeMirror typeMirror) throws Exception {
            return types.capture(typeMirror);
        }
    };

    private final IgnoreCompletionFailures.Fn1<ArrayType, TypeMirror> getArrayType = new IgnoreCompletionFailures.Fn1<ArrayType, TypeMirror>() {
        @Override
        public ArrayType call(TypeMirror typeMirror) throws Exception {
            return types.getArrayType(typeMirror);
        }
    };

    private final IgnoreCompletionFailures.Fn2<WildcardType, TypeMirror, TypeMirror> getWildcardType = new IgnoreCompletionFailures.Fn2<WildcardType, TypeMirror, TypeMirror>() {
        @Override
        public WildcardType call(TypeMirror typeMirror, TypeMirror typeMirror2) throws Exception {
            return types.getWildcardType(typeMirror, typeMirror2);
        }
    };

    private final IgnoreCompletionFailures.Fn2<DeclaredType, TypeElement, TypeMirror[]> getDeclaredType = new IgnoreCompletionFailures.Fn2<DeclaredType, TypeElement, TypeMirror[]>() {
        @Override
        public DeclaredType call(TypeElement typeElement, TypeMirror[] typeMirrors) throws Exception {
            return types.getDeclaredType(typeElement, typeMirrors);
        }
    };

    private final IgnoreCompletionFailures.Fn3<DeclaredType, DeclaredType, TypeElement, TypeMirror[]> getDeclaredType2 = new IgnoreCompletionFailures.Fn3<DeclaredType, DeclaredType, TypeElement, TypeMirror[]>() {
        @Override
        public DeclaredType call(DeclaredType declaredType, TypeElement typeElement, TypeMirror[] typeMirrors)
            throws Exception {

            return types.getDeclaredType(declaredType, typeElement, typeMirrors);
        }
    };

    private final IgnoreCompletionFailures.Fn2<TypeMirror, DeclaredType, Element> asMemberOf = new IgnoreCompletionFailures.Fn2<TypeMirror, DeclaredType, Element>() {
        @Override
        public TypeMirror call(DeclaredType declaredType, Element element) throws Exception {
            return types.asMemberOf(declaredType, element);
        }
    };

    public MissingTypeAwareDelegatingTypes(Types types) {
        this.types = types;
    }

    @Override
    public Element asElement(final TypeMirror t) {
        return IgnoreCompletionFailures.call(asElement, t);
    }

    @Override
    public boolean isSameType(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.call(isSameType, t1, t2);
    }

    @Override
    public boolean isSubtype(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.call(isSubtype, t1, t2);
    }

    @Override
    public boolean isAssignable(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.call(isAssignable, t1, t2);
    }

    @Override
    public boolean contains(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.call(contains, t1, t2);
    }

    @Override
    public boolean isSubsignature(final ExecutableType m1, final ExecutableType m2) {
        return IgnoreCompletionFailures.call(isSubsignature, m1, m2);
    }

    @Override
    public List<? extends TypeMirror> directSupertypes(
        TypeMirror t) {

        return IgnoreCompletionFailures.call(directSuperTypes, t);
    }

    @Override
    public TypeMirror erasure(TypeMirror t) {
        return IgnoreCompletionFailures.call(erasure, t);
    }

    @Override
    public TypeElement boxedClass(PrimitiveType p) {
        return IgnoreCompletionFailures.call(boxedClass, p);
    }

    @Override
    public PrimitiveType unboxedType(TypeMirror t) {
        return IgnoreCompletionFailures.call(unboxedType, t);
    }

    @Override
    public TypeMirror capture(TypeMirror t) {
        return IgnoreCompletionFailures.call(capture, t);
    }

    @Override
    public PrimitiveType getPrimitiveType(TypeKind kind) {
        return types.getPrimitiveType(kind);
    }

    @Override
    public NullType getNullType() {
        return types.getNullType();
    }

    @Override
    public NoType getNoType(TypeKind kind) {
        return types.getNoType(kind);
    }

    @Override
    public ArrayType getArrayType(TypeMirror componentType) {
        return IgnoreCompletionFailures.call(getArrayType, componentType);
    }

    @Override
    public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {

        return IgnoreCompletionFailures.call(getWildcardType, extendsBound, superBound);
    }

    @Override
    public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
        return IgnoreCompletionFailures.call(getDeclaredType, typeElem, typeArgs);
    }

    @Override
    public DeclaredType getDeclaredType(DeclaredType containing, TypeElement typeElem, TypeMirror... typeArgs) {
        return IgnoreCompletionFailures
            .call(getDeclaredType2, containing, typeElem, typeArgs);
    }

    @Override
    public TypeMirror asMemberOf(DeclaredType containing, Element element) {
        return IgnoreCompletionFailures.call(asMemberOf, containing, element);
    }

}
