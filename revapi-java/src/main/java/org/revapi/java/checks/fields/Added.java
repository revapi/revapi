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

import org.revapi.ChangeSeverity;
import org.revapi.MatchReport;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Added extends AbstractJavaCheck {

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (oldField == null && newField != null) {

            boolean add = newField.getModifiers().contains(Modifier.PUBLIC) ||
                newField.getModifiers().contains(Modifier.PROTECTED);

            if (add) {
                pushActive(null, newField);
            }
        }
    }

    @Override
    protected List<MatchReport.Problem> doEnd() {
        ActiveElements<VariableElement> fields = popIfActive();

        if (fields == null) {
            return null;
        }

        //do not bother with this on final classes - a new field on a final class cannot
        //cause any problem
        boolean fieldInFinalClass = fields.newElement.getEnclosingElement().getModifiers().contains(Modifier.FINAL);
        ChangeSeverity binarySeverity = fieldInFinalClass ? null : ChangeSeverity.POTENTIALLY_BREAKING;
        ChangeSeverity sourceSeverity =
            fieldInFinalClass ? ChangeSeverity.NON_BREAKING : ChangeSeverity.POTENTIALLY_BREAKING;

        return Collections
            .singletonList(
                createProblem(Code.FIELD_ADDED, binarySeverity, sourceSeverity, new String[0], fields.newElement));
    }
}
