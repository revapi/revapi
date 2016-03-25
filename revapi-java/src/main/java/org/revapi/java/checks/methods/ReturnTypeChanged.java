/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java.checks.methods;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ReturnTypeChanged extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable ExecutableElement oldMethod, @Nullable ExecutableElement newMethod) {
        if (oldMethod == null || newMethod == null || isBothPrivate(oldMethod, getOldTypeEnvironment(), newMethod,
                getNewTypeEnvironment())) {
            return;
        }

        String oldRet = Util.toUniqueString(oldMethod.getReturnType());
        String newRet = Util.toUniqueString(newMethod.getReturnType());

        if (!oldRet.equals(newRet)) {
            pushActive(oldMethod, newMethod);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<ExecutableElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        TypeMirror oldReturnType = methods.oldElement.getReturnType();
        TypeMirror newReturnType = methods.newElement.getReturnType();

        TypeMirror erasedOldType = getOldTypeEnvironment().getTypeUtils().erasure(oldReturnType);
        TypeMirror erasedNewType = getNewTypeEnvironment().getTypeUtils().erasure(newReturnType);

        String oldR = Util.toUniqueString(oldReturnType);
        String newR = Util.toUniqueString(newReturnType);

        String oldER = Util.toUniqueString(erasedOldType);
        String newER = Util.toUniqueString(erasedNewType);

        Code code = null;

        if (!oldER.equals(newER)) {
            code = Code.METHOD_RETURN_TYPE_CHANGED;
        } else {
            if (!oldR.equals(newR)) {
                code = Code.METHOD_RETURN_TYPE_TYPE_PARAMETERS_CHANGED;
            }
        }

        String oldHR = Util.toHumanReadableString(oldReturnType);
        String newHR = Util.toHumanReadableString(newReturnType);

        return code == null ? null : Collections.singletonList(createDifference(code, oldHR, newHR));
    }
}
