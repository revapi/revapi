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
package org.revapi.java.checks.methods;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public final class ReturnTypeChanged extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        if (!isBothAccessible(oldMethod, newMethod)) {
            return;
        }

        assert oldMethod != null;
        assert newMethod != null;

        TypeMirror oldReturnType = oldMethod.getModelRepresentation().getReturnType();
        TypeMirror newReturnType = newMethod.getModelRepresentation().getReturnType();

        String oldRet = Util.toUniqueString(oldReturnType);
        String newRet = Util.toUniqueString(newReturnType);

        TypeMirror erasedOldType = getOldTypeEnvironment().getTypeUtils()
                .erasure(oldMethod.getDeclaringElement().getReturnType());
        TypeMirror erasedNewType = getNewTypeEnvironment().getTypeUtils()
                .erasure(newMethod.getDeclaringElement().getReturnType());

        String oldErasedRet = Util.toUniqueString(erasedOldType);
        String newErasedRet = Util.toUniqueString(erasedNewType);

        if (!oldRet.equals(newRet) || !oldErasedRet.equals(newErasedRet)) {
            pushActive(oldMethod, newMethod, oldReturnType, oldRet, newReturnType, newRet, erasedOldType, oldErasedRet,
                    erasedNewType, newErasedRet);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaMethodElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        TypeMirror oldReturnType = (TypeMirror) methods.context[0];
        String oldR = (String) methods.context[1];

        TypeMirror newReturnType = (TypeMirror) methods.context[2];
        String newR = (String) methods.context[3];

        TypeMirror erasedOldType = (TypeMirror) methods.context[4];
        String oldER = (String) methods.context[5];

        TypeMirror erasedNewType = (TypeMirror) methods.context[6];
        String newER = (String) methods.context[7];

        Code code = null;

        String oldHR = Util.toHumanReadableString(oldReturnType);
        String newHR = Util.toHumanReadableString(newReturnType);

        if (!oldER.equals(newER)) {
            // we need to check if the returned type changed covariantly or not.
            if (isPrimitiveOrVoid(erasedOldType) || isPrimitiveOrVoid(erasedNewType)) {
                code = Code.METHOD_RETURN_TYPE_CHANGED;
            } else if (isCovariant(erasedOldType, erasedNewType)) {
                code = Code.METHOD_RETURN_TYPE_CHANGED_COVARIANTLY;
            } else if (oldR.equals(newR)) {
                oldHR = Util.toHumanReadableString(erasedOldType);
                newHR = Util.toHumanReadableString(erasedNewType);
                code = Code.METHOD_RETURN_TYPE_ERASURE_CHANGED;
            } else {
                code = Code.METHOD_RETURN_TYPE_CHANGED;
            }
        } else {
            if (!oldR.equals(newR)) {
                code = Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED;
            }
        }

        return code == null ? null : Collections.singletonList(createDifference(code,
                Code.attachmentsFor(methods.oldElement, methods.newElement, "oldType", oldHR, "newType", newHR)));
    }

    private static boolean isPrimitiveOrVoid(TypeMirror type) {
        TypeKind kind = type.getKind();
        switch (kind) {
        case VOID:
            return true;
        case ARRAY:
            return isPrimitiveOrVoid(((ArrayType) type).getComponentType());
        default:
            return kind.isPrimitive();
        }
    }

    private boolean isCovariant(TypeMirror superType, TypeMirror subType) {
        return Util.isSubtype(subType, Collections.singletonList(superType), getNewTypeEnvironment().getTypeUtils());
    }
}
