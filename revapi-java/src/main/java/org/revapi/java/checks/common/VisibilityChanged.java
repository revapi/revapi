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

package org.revapi.java.checks.common;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class VisibilityChanged extends CheckBase {
    private final Code code;
    private final boolean reportIncrease;

    protected VisibilityChanged(Code code, boolean reportIncrease) {
        this.code = code;
        this.reportIncrease = reportIncrease;
    }

    protected final void doVisit(Element oldElement, Element newElement) {
        if (oldElement != null && newElement != null) {
            pushActive(oldElement, newElement);
        }
    }

    @Override
    protected final List<Difference> doEnd() {
        CheckBase.ActiveElements<Element> elements = popIfActive();
        if (elements != null) {
            Modifier oldVisibility = getVisibility(elements.oldElement);
            Modifier newVisibility = getVisibility(elements.newElement);

            //public == 0, private == 3
            if (isProblem(getModifierRank(oldVisibility), getModifierRank(newVisibility))) {
                return Collections.singletonList(report(oldVisibility, newVisibility));
            }
        }

        return null;
    }

    private boolean isProblem(int oldVisibilityRank, int newVisibilityRank) {
        return (reportIncrease && oldVisibilityRank > newVisibilityRank) ||
            (!reportIncrease && oldVisibilityRank < newVisibilityRank);
    }

    private Modifier getVisibility(Element t) {
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

    private Difference report(Modifier oldVisibility, Modifier newVisibility) {
        return createDifference(code, new String[]{modifier(oldVisibility), modifier(newVisibility)}, oldVisibility,
            newVisibility);
    }

    private String modifier(Modifier m) {
        return m == null ? "package" : m.name().toLowerCase();
    }

}
