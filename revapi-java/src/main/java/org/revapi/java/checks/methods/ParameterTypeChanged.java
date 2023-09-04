/*
 * Copyright 2014-2023 Lukas Krejci
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
import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaMethodParameterElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class ParameterTypeChanged extends CheckBase {

    private boolean skip;

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD, Type.METHOD_PARAMETER);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        skip = oldMethod == null || newMethod == null || oldMethod.getModelRepresentation().getParameterTypes()
                .size() != newMethod.getModelRepresentation().getParameterTypes().size();
    }

    @Override
    protected void doVisitMethodParameter(@Nullable JavaMethodParameterElement oldParameter,
            @Nullable JavaMethodParameterElement newParameter) {

        if (skip || oldParameter == null || newParameter == null) {
            // will be handled by nof parameters changed...
            return;
        }

        if (!isBothAccessible(oldParameter.getParent(), newParameter.getParent())) {
            return;
        }

        TypeMirror oldType = oldParameter.getModelRepresentation();
        String oldParam = Util.toUniqueString(oldType);
        String oldErasedParam = Util.toUniqueString(
                getOldTypeEnvironment().getTypeUtils().erasure(oldParameter.getDeclaringElement().asType()));

        TypeMirror newType = newParameter.getModelRepresentation();
        String newParam = Util.toUniqueString(newType);
        String newErasedParam = Util.toUniqueString(
                getNewTypeEnvironment().getTypeUtils().erasure(newParameter.getDeclaringElement().asType()));

        if (!oldParam.equals(newParam) || !oldErasedParam.equals(newErasedParam)) {
            pushActive(oldParameter, newParameter, oldParam, oldErasedParam, newParam, newErasedParam);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {

        ActiveElements<JavaMethodParameterElement> params = popIfActive();
        if (params == null) {
            return null;
        }

        String oldParam = (String) params.context[0];
        String oldErasedParam = (String) params.context[1];
        String newParam = (String) params.context[2];
        String newErasedParam = (String) params.context[3];

        String oldType = Util.toHumanReadableString(params.oldElement.getModelRepresentation());
        String newType = Util.toHumanReadableString(params.newElement.getModelRepresentation());

        if (!oldErasedParam.equals(newErasedParam)) {
            if (oldParam.equals(newParam)) {
                oldType = Util.toHumanReadableString(getOldTypeEnvironment().getTypeUtils()
                        .erasure(params.oldElement.getDeclaringElement().asType()));
                newType = Util.toHumanReadableString(getNewTypeEnvironment().getTypeUtils()
                        .erasure(params.newElement.getDeclaringElement().asType()));
                return Collections.singletonList(createDifference(Code.METHOD_PARAMETER_TYPE_ERASURE_CHANGED, Code
                        .attachmentsFor(params.oldElement, params.newElement, "oldType", oldType, "newType", newType)));
            } else {
                return Collections.singletonList(createDifference(Code.METHOD_PARAMETER_TYPE_CHANGED, Code
                        .attachmentsFor(params.oldElement, params.newElement, "oldType", oldType, "newType", newType)));
            }
        } else {
            return Collections.singletonList(createDifference(Code.METHOD_PARAMETER_TYPE_PARAMETER_CHANGED,
                    Code.attachmentsFor(params.oldElement, params.newElement, "oldType", oldType, "newType", newType)));
        }
    }
}
