/*
 * Copyright 2015 Lukas Krejci
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
 */

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

import org.revapi.java.spi.IgnoreCompletionFailures;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class MissingTypeAwareDelegatingTypes implements Types {

    private final Types types;

    public MissingTypeAwareDelegatingTypes(Types types) {
        this.types = types;
    }

    @Override
    public Element asElement(final TypeMirror t) {
        return IgnoreCompletionFailures.in(types::asElement, t);
    }

    @Override
    public boolean isSameType(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(types::isSameType, t1, t2);
    }

    @Override
    public boolean isSubtype(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(types::isSubtype, t1, t2);
    }

    @Override
    public boolean isAssignable(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(types::isAssignable, t1, t2);
    }

    @Override
    public boolean contains(final TypeMirror t1, final TypeMirror t2) {
        return IgnoreCompletionFailures.in(types::contains, t1, t2);
    }

    @Override
    public boolean isSubsignature(final ExecutableType m1, final ExecutableType m2) {
        return IgnoreCompletionFailures.in(types::isSubsignature, m1, m2);
    }

    @Override
    public List<? extends TypeMirror> directSupertypes(TypeMirror t) {

        return IgnoreCompletionFailures.in(types::directSupertypes, t);
    }

    @Override
    public TypeMirror erasure(TypeMirror t) {
        return IgnoreCompletionFailures.in(types::erasure, t);
    }

    @Override
    public TypeElement boxedClass(PrimitiveType p) {
        return IgnoreCompletionFailures.in(types::boxedClass, p);
    }

    @Override
    public PrimitiveType unboxedType(TypeMirror t) {
        return IgnoreCompletionFailures.in(types::unboxedType, t);
    }

    @Override
    public TypeMirror capture(TypeMirror t) {
        return IgnoreCompletionFailures.in(types::capture, t);
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
        return IgnoreCompletionFailures.in(types::getArrayType, componentType);
    }

    @Override
    public WildcardType getWildcardType(TypeMirror extendsBound, TypeMirror superBound) {

        return IgnoreCompletionFailures.in(types::getWildcardType, extendsBound, superBound);
    }

    @Override
    public DeclaredType getDeclaredType(TypeElement typeElem, TypeMirror... typeArgs) {
        return IgnoreCompletionFailures.in(types::getDeclaredType, typeElem, typeArgs);
    }

    @Override
    public DeclaredType getDeclaredType(DeclaredType containing, TypeElement typeElem, TypeMirror... typeArgs) {
        return IgnoreCompletionFailures.in(types::getDeclaredType, containing, typeElem, typeArgs);
    }

    @Override
    public TypeMirror asMemberOf(DeclaredType containing, Element element) {
        return IgnoreCompletionFailures.in(types::asMemberOf, containing, element);
    }

}
