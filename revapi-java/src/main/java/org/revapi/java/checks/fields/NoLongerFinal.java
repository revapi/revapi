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

package org.revapi.java.checks.fields;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import org.revapi.java.checks.Code;
import org.revapi.java.checks.common.ModifierChanged;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NoLongerFinal extends ModifierChanged {
    public NoLongerFinal() {
        super(false, Code.FIELD_NO_LONGER_FINAL, Modifier.FINAL);
    }

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        super.doVisit(oldField, newField);
    }
}
