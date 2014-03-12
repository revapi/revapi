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
import org.revapi.java.CheckBase;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Removed extends AbstractJavaCheck {

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (oldField != null && newField == null && isAccessible(oldField) &&
            isAccessibleOrInAPI(oldField.getEnclosingElement(), getOldTypeEnvironment())) {

            pushActive(oldField, null);
        }
    }

    @Override
    protected List<Report.Difference> doEnd() {
        CheckBase.ActiveElements<VariableElement> fields = popIfActive();

        if (fields == null) {
            return null;
        }

        boolean isConstant = fields.oldElement.getConstantValue() != null;

        return Collections
            .singletonList(createDifference(isConstant ? Code.FIELD_CONSTANT_REMOVED : Code.FIELD_REMOVED));
    }
}
