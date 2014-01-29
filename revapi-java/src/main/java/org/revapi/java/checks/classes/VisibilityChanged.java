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

import org.revapi.ChangeSeverity;
import org.revapi.MatchReport;
import org.revapi.java.CheckBase;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class VisibilityChanged extends AbstractJavaCheck {
    private final Code code;
    private final ChangeSeverity sourceSeverity;
    private final ChangeSeverity binarySeverity;

    protected VisibilityChanged(Code code, ChangeSeverity binarySeverity, ChangeSeverity sourceSeverity) {
        this.code = code;
        this.sourceSeverity = sourceSeverity;
        this.binarySeverity = binarySeverity;
    }

    @Override
    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
        if (oldType != null && newType != null) {
            pushActive(oldType, newType);
        }
    }

    @Override
    protected List<MatchReport.Problem> doEnd() {
        CheckBase.ActiveElements<TypeElement> types = popIfActive();
        if (types != null) {
            Modifier oldVisibility = getVisibility(types.oldElement);
            Modifier newVisibility = getVisibility(types.newElement);

            //public == 0, private == 3
            if (isProblem(getModifierRank(oldVisibility), getModifierRank(newVisibility))) {
                return Collections.singletonList(report(oldVisibility, newVisibility));
            }
        }

        return null;
    }

    protected abstract boolean isProblem(int oldVisibilityRank, int newVisibilityRank);

    private Modifier getVisibility(TypeElement t) {
        for (Modifier m : t.getModifiers()) {
            if (m == Modifier.PUBLIC || m == Modifier.PROTECTED || m == Modifier.PRIVATE) {
                return m;
            }
        }

        return null;
    }

    private int getModifierRank(Modifier modifier) {
        if (modifier == null) {
            //package private
            return 2;
        }

        switch (modifier) {
        case PUBLIC:
            return 0;
        case PROTECTED:
            return 1;
        case PRIVATE:
            return 3;
        default:
            return Integer.MAX_VALUE;
        }
    }

    private MatchReport.Problem report(Modifier oldVisibility, Modifier newVisibility) {
        return createProblem(code, binarySeverity, sourceSeverity,
            new String[]{modifier(oldVisibility), modifier(newVisibility)}, oldVisibility, newVisibility);
    }

    private String modifier(Modifier m) {
        return m == null ? "package" : m.name().toLowerCase();
    }

}
