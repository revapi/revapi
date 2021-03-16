/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
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
 * limitations under the License.
 */
package org.revapi.java.checks.common;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public abstract class ModifierChanged extends CheckBase {
    private final boolean added;
    private final Code code;
    private final Modifier modifier;

    protected ModifierChanged(boolean added, Code code, Modifier modifier) {
        this.added = added;
        this.code = code;
        this.modifier = modifier;
    }

    protected final void doVisit(JavaModelElement oldElement, JavaModelElement newElement) {
        if (!isBothAccessible(oldElement, newElement)) {
            return;
        }

        boolean oldHas = oldElement.getDeclaringElement().getModifiers().contains(modifier);
        boolean newHas = newElement.getDeclaringElement().getModifiers().contains(modifier);

        if ((added && !oldHas && newHas) || (!added && oldHas && !newHas)) {
            pushActive(oldElement, newElement);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaModelElement> elements = popIfActive();
        if (elements == null) {
            return null;
        }

        return Collections.singletonList(createDifference(code,
                Code.attachmentsFor(elements.oldElement, elements.newElement, "oldModifiers",
                        stringify(elements.oldElement.getDeclaringElement().getModifiers()), "newModifiers",
                        stringify(elements.newElement.getDeclaringElement().getModifiers()))));
    }

    // ordered according to http://cr.openjdk.java.net/~alundblad/styleguide/index-v6.html
    public static String stringify(Set<Modifier> modifiers) {
        return modifiers.stream().sorted(Comparator.comparingInt(ModifierChanged::score))
                .map(m -> m.name().toLowerCase()).collect(Collectors.joining(" "));
    }

    private static int score(Modifier mod) {
        // public
        // private
        // protected
        // abstract
        // static
        // final
        // transient
        // volatile
        // default
        // synchronized
        // native
        // strictfp

        switch (mod) {
        case PUBLIC:
            return 0;
        case PRIVATE:
            return 1;
        case PROTECTED:
            return 2;
        case ABSTRACT:
            return 3;
        case STATIC:
            return 4;
        case FINAL:
            return 5;
        case TRANSIENT:
            return 6;
        case VOLATILE:
            return 7;
        case DEFAULT:
            return 8;
        case SYNCHRONIZED:
            return 9;
        case NATIVE:
            return 10;
        case STRICTFP:
            return 11;
        default:
            return 12;
        }
    }
}
