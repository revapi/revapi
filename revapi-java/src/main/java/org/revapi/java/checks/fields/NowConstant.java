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

package org.revapi.java.checks.fields;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.VariableElement;

import org.revapi.Difference;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NowConstant extends BothFieldsRequiringCheck {
    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (!shouldCheck(oldField, newField)) {
            return;
        }

        if (oldField.getConstantValue() == null && newField.getConstantValue() != null) {
            pushActive(oldField, newField);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<VariableElement> fields = popIfActive();
        if (fields == null) {
            return null;
        }

        return Collections.singletonList(
            createDifference(Code.FIELD_NOW_CONSTANT, fields.newElement.getConstantValue()));
    }
}
