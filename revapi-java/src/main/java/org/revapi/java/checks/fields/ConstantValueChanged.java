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

import org.revapi.Report;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class ConstantValueChanged extends BothFieldsRequiringCheck {

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (!shouldCheck(oldField, newField)) {
            return;
        }

        Object oldC = oldField.getConstantValue();
        Object newC = newField.getConstantValue();

        if (oldC != null && newC != null && !oldC.equals(newC)) {
            pushActive(oldField, newField);
        }
    }

    @Override
    protected List<Report.Difference> doEnd() {
        ActiveElements<VariableElement> fields = popIfActive();

        if (fields == null) {
            return null;
        }

        return Collections.singletonList(
            createDifference(Code.FIELD_CONSTANT_VALUE_CHANGED,
                fields.oldElement.getConstantValue().toString(),
                fields.newElement.getConstantValue().toString()));
    }
}
