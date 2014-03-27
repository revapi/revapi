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
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.VariableElement;

import org.revapi.Difference;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ParameterTypeChanged extends AbstractJavaCheck {
    @Override
    protected void doVisitMethodParameter(@Nullable VariableElement oldParameter,
        @Nullable VariableElement newParameter) {

        if (oldParameter == null || newParameter == null) {
            //will be handled by nof parameters changed...
            return;
        }

        String oldType = Util.toUniqueString(oldParameter.asType());
        String newType = Util.toUniqueString(newParameter.asType());

        if (!oldType.equals(newType)) {
            pushActive(oldParameter, newParameter);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {

        ActiveElements<VariableElement> params = popIfActive();
        if (params == null) {
            return null;
        }

        String oldType = Util.toHumanReadableString(params.oldElement.asType());
        String newType = Util.toHumanReadableString(params.newElement.asType());

        return Collections.singletonList(createDifference(Code.METHOD_PARAMETER_TYPE_CHANGED, oldType, newType));
    }
}
