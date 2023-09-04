/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java.checks.generics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeParameterElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class FormalTypeParametersChanged extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS, Type.METHOD);
    }

    @Override
    protected void doVisitClass(@Nullable JavaTypeElement oldType, @Nullable JavaTypeElement newType) {
        doVisit(oldType, newType);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        doVisit(oldMethod, newMethod);
    }

    private void doVisit(@Nullable JavaModelElement oldElement, @Nullable JavaModelElement newElement) {
        if (!isBothAccessible(oldElement, newElement)) {
            return;
        }

        assert oldElement != null;
        assert newElement != null;

        Parameterizable oldEl = (Parameterizable) oldElement.getDeclaringElement();
        Parameterizable newEl = (Parameterizable) newElement.getDeclaringElement();

        List<? extends TypeParameterElement> oldPars = oldEl.getTypeParameters();
        List<? extends TypeParameterElement> newPars = newEl.getTypeParameters();

        if (oldPars.size() == 0 && oldPars.size() == newPars.size()) {
            return;
        }

        List<TypeParameterElement> added = new ArrayList<>();
        List<TypeParameterElement> removed = new ArrayList<>();
        Map<TypeParameterElement, TypeParameterElement> changed = new LinkedHashMap<>();

        Iterator<? extends TypeParameterElement> oldIt = oldPars.iterator();
        Iterator<? extends TypeParameterElement> newIt = newPars.iterator();

        while (oldIt.hasNext() && newIt.hasNext()) {
            TypeParameterElement oldT = oldIt.next();
            TypeParameterElement newT = newIt.next();
            String oldS = Util.toUniqueString(oldT.asType());
            String newS = Util.toUniqueString(newT.asType());

            if (!oldS.equals(newS)) {
                changed.put(oldT, newT);
            }
        }

        while (oldIt.hasNext()) {
            removed.add(oldIt.next());
        }

        while (newIt.hasNext()) {
            added.add(newIt.next());
        }

        if (!added.isEmpty() || !removed.isEmpty() || !changed.isEmpty()) {
            pushActive(oldElement, newElement, added, removed, changed);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaModelElement> els = popIfActive();
        if (els == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<TypeParameterElement> added = (List<TypeParameterElement>) els.context[0];
        @SuppressWarnings("unchecked")
        List<TypeParameterElement> removed = (List<TypeParameterElement>) els.context[1];
        @SuppressWarnings("unchecked")
        Map<TypeParameterElement, TypeParameterElement> changed = (Map<TypeParameterElement, TypeParameterElement>) els.context[2];

        Parameterizable oldT = (Parameterizable) els.oldElement.getDeclaringElement();

        List<Difference> diffs = new ArrayList<>();
        if (oldT.getTypeParameters().isEmpty()) {
            diffs.add(createDifference(Code.GENERICS_ELEMENT_NOW_PARAMETERIZED,
                    Code.attachmentsFor(els.oldElement, els.newElement)));
        }

        for (TypeParameterElement e : added) {
            diffs.add(createDifferenceWithExplicitParams(Code.GENERICS_FORMAL_TYPE_PARAMETER_ADDED,
                    Code.attachmentsFor(els.oldElement, els.newElement, "typeParameter", Util.toHumanReadableString(e)),
                    Util.toHumanReadableString(e)));
        }

        for (TypeParameterElement e : removed) {
            diffs.add(createDifferenceWithExplicitParams(Code.GENERICS_FORMAL_TYPE_PARAMETER_REMOVED,
                    Code.attachmentsFor(els.oldElement, els.newElement, "typeParameter", Util.toHumanReadableString(e)),
                    Util.toHumanReadableString(e)));
        }

        for (Map.Entry<TypeParameterElement, TypeParameterElement> e : changed.entrySet()) {
            String oldP = Util.toHumanReadableString(e.getKey());
            String newP = Util.toHumanReadableString(e.getValue());
            diffs.add(createDifferenceWithExplicitParams(Code.GENERICS_FORMAL_TYPE_PARAMETER_CHANGED, Code
                    .attachmentsFor(els.oldElement, els.newElement, "oldTypeParameter", oldP, "newTypeParameter", newP),
                    oldP, newP));
        }

        return diffs;
    }
}
