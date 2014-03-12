/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.NullType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.SimpleTypeVisitor7;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class TypeMirrorPairVisitor<R> extends SimpleTypeVisitor7<R, TypeMirror> {

    @SuppressWarnings("UnusedParameters")
    protected R unmatchedAction(@Nonnull TypeMirror type, @Nullable TypeMirror otherType) {
        return null;
    }

    protected R defaultMatchAction(@Nonnull TypeMirror type, @Nullable TypeMirror otherType) {
        return unmatchedAction(type, otherType);
    }

    @Override
    public final R visitPrimitive(PrimitiveType type, TypeMirror otherType) {
        return otherType instanceof PrimitiveType ? visitPrimitive(type, (PrimitiveType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitPrimitive(PrimitiveType type, PrimitiveType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitNull(NullType type, TypeMirror otherType) {
        return otherType instanceof NullType ? visitNull(type, (NullType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitNull(NullType type, NullType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitArray(ArrayType type, TypeMirror otherType) {
        return otherType instanceof ArrayType ? visitArray(type, (ArrayType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitArray(ArrayType type, ArrayType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitDeclared(DeclaredType type, TypeMirror otherType) {
        return otherType instanceof DeclaredType ? visitDeclared(type, (DeclaredType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitDeclared(DeclaredType type, DeclaredType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitError(ErrorType type, TypeMirror otherType) {
        return otherType instanceof ErrorType ? visitError(type, (ErrorType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitError(ErrorType type, ErrorType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitTypeVariable(TypeVariable type, TypeMirror otherType) {
        return otherType instanceof TypeVariable ? visitTypeVariable(type, (TypeVariable) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitTypeVariable(TypeVariable type, TypeVariable otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitWildcard(WildcardType type, TypeMirror otherType) {
        return otherType instanceof WildcardType ? visitWildcard(type, (WildcardType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitWildcard(WildcardType type, WildcardType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitExecutable(ExecutableType type, TypeMirror otherType) {
        return otherType instanceof ExecutableType ? visitExecutable(type, (ExecutableType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitExecutable(ExecutableType type, ExecutableType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public final R visitNoType(NoType type, TypeMirror otherType) {
        return otherType instanceof NoType ? visitNoType(type, (NoType) otherType) :
            unmatchedAction(type, otherType);
    }

    protected R visitNoType(NoType type, NoType otherType) {
        return defaultMatchAction(type, otherType);
    }

    @Override
    public R visitUnknown(TypeMirror type, TypeMirror otherType) {
        return unmatchedAction(type, otherType);
    }
}
