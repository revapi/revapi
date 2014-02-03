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

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

import org.revapi.MatchReport;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class ModifierChanged extends AbstractJavaCheck {
    private final boolean added;
    private final Code code;
    private final Modifier modifier;

    protected ModifierChanged(boolean added, Code code, Modifier modifier) {
        this.added = added;
        this.code = code;
        this.modifier = modifier;
    }

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (oldField == null || newField == null) {
            return;
        }

        boolean oldHas = oldField.getModifiers().contains(modifier);
        boolean newHas = newField.getModifiers().contains(modifier);

        if ((added && !oldHas && newHas) || (!added && oldHas && !newHas)) {
            pushActive(oldField, newField);
        }
    }

    @Override
    protected List<MatchReport.Problem> doEnd() {
        ActiveElements<VariableElement> fields = popIfActive();
        if (fields == null) {
            return null;
        }

        return Collections.singletonList(createProblem(code));
    }
}
