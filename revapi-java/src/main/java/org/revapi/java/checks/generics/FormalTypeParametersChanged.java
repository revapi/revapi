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

package org.revapi.java.checks.generics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Parameterizable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class FormalTypeParametersChanged extends CheckBase {


    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS, Type.METHOD);
    }

    @Override
    protected void doVisitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
        doVisit(oldType, newType);
    }

    @Override
    protected void doVisitMethod(@Nullable ExecutableElement oldMethod, @Nullable ExecutableElement newMethod) {
        doVisit(oldMethod, newMethod);
    }

    private void doVisit(@Nullable Parameterizable oldElement, @Nullable Parameterizable newElement) {
        if (oldElement == null || newElement == null ||
            !isBothAccessibleOrInApi(oldElement, getOldTypeEnvironment(), newElement, getNewTypeEnvironment())) {
            return;
        }

        List<? extends TypeParameterElement> oldPars = oldElement.getTypeParameters();
        List<? extends TypeParameterElement> newPars = newElement.getTypeParameters();

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
        ActiveElements<Parameterizable> els = popIfActive();
        if (els == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        List<TypeParameterElement> added = (List<TypeParameterElement>) els.context[0];
        @SuppressWarnings("unchecked")
        List<TypeParameterElement> removed = (List<TypeParameterElement>) els.context[1];
        @SuppressWarnings("unchecked")
        Map<TypeParameterElement, TypeParameterElement> changed =
            (Map<TypeParameterElement, TypeParameterElement>) els.context[2];

        List<Difference> diffs = new ArrayList<>();
        if (els.oldElement.getTypeParameters().isEmpty()) {
            diffs.add(createDifference(Code.GENERICS_ELEMENT_NOW_PARAMETERIZED));
        }

        for (TypeParameterElement e : added) {
            diffs.add(
                createDifference(Code.GENERICS_FORMAL_TYPE_PARAMETER_ADDED, new String[]{Util.toHumanReadableString(e)},
                    e)
            );
        }

        for (TypeParameterElement e : removed) {
            diffs.add(createDifference(Code.GENERICS_FORMAL_TYPE_PARAMETER_REMOVED,
                new String[]{Util.toHumanReadableString(e)}, e));
        }

        for (Map.Entry<TypeParameterElement, TypeParameterElement> e : changed.entrySet()) {
            diffs.add(createDifference(Code.GENERICS_FORMAL_TYPE_PARAMETER_CHANGED,
                new String[]{Util.toHumanReadableString(e.getKey()), Util.toHumanReadableString(e.getValue())},
                e.getKey(), e.getValue()));
        }

        return diffs;
    }
}
