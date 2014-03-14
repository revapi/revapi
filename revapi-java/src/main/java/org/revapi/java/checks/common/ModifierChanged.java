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
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class ModifierChanged extends AbstractJavaCheck {
    private final boolean added;
    private final Code code;
    private final Modifier modifier;

    protected ModifierChanged(boolean added, Code code, Modifier modifier) {
        this.added = added;
        this.code = code;
        this.modifier = modifier;
    }

    protected final void doVisit(Element oldElement, Element newElement) {
        if (oldElement == null || newElement == null) {
            return;
        }

        if (isBothPrivate(oldElement, newElement)) {
            return;
        }

        boolean oldHas = oldElement.getModifiers().contains(modifier);
        boolean newHas = newElement.getModifiers().contains(modifier);

        if ((added && !oldHas && newHas) || (!added && oldHas && !newHas)) {
            pushActive(oldElement, newElement);
        }
    }

    @Override
    protected final List<Difference> doEnd() {
        ActiveElements<Element> elements = popIfActive();
        if (elements == null) {
            return null;
        }

        return Collections.singletonList(createDifference(code));
    }

}
