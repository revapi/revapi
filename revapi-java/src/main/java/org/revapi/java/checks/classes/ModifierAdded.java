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

package org.revapi.java.checks.classes;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import org.revapi.MatchReport;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class ModifierAdded extends AbstractJavaCheck {
    private final Modifier modifier;
    private final Code code;

    protected ModifierAdded(Code code, Modifier modifier) {
        this.modifier = modifier;
        this.code = code;
    }

    @Override
    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
        if (oldType != null && newType != null && newType.getKind() == oldType.getKind()) {
            pushActive(oldType, newType);
        }
    }

    @Override
    protected List<MatchReport.Problem> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();
        if (types != null) {
            if (!types.oldElement.getModifiers().contains(modifier) &&
                types.newElement.getModifiers().contains(modifier)) {

                return Collections.singletonList(
                    createProblem(code));
            }
        }

        return null;
    }
}
