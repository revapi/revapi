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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class SuperTypeParametersChanged extends AbstractJavaCheck {

    @Override
    protected void doVisitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
        if (oldType == null || newType == null ||
            !isBothAccessibleOrInApi(oldType, getOldTypeEnvironment(), newType, getNewTypeEnvironment())) {
            return;
        }

        List<? extends TypeMirror> oldSuperTypes = getOldTypeEnvironment().getTypeUtils().directSupertypes(
            oldType.asType());

        List<? extends TypeMirror> newSuperTypes = getNewTypeEnvironment().getTypeUtils().directSupertypes(
            newType.asType());

        if (oldSuperTypes.size() != newSuperTypes.size()) {
            //super types changed, handled elsewhere
            return;
        }

        Map<String, TypeMirror> erasedOld = new LinkedHashMap<>();
        Map<String, TypeMirror> erasedNew = new LinkedHashMap<>();

        for (TypeMirror t : oldSuperTypes) {
            erasedOld.put(Util.toUniqueString(getOldTypeEnvironment().getTypeUtils().erasure(t)), t);
        }

        for (TypeMirror t : newSuperTypes) {
            erasedNew.put(Util.toUniqueString(getNewTypeEnvironment().getTypeUtils().erasure(t)), t);
        }

        if (!erasedOld.keySet().equals(erasedNew.keySet())) {
            //super types changed, handled elsewhere
            return;
        }

        Map<TypeMirror, TypeMirror> changed = new LinkedHashMap<>();

        for (Map.Entry<String, TypeMirror> e : erasedOld.entrySet()) {
            TypeMirror oldT = e.getValue();
            TypeMirror newT = erasedNew.get(e.getKey());
            String oldS = Util.toUniqueString(oldT);
            String newS = Util.toUniqueString(newT);

            if (!oldS.equals(newS)) {
                changed.put(oldT, newT);
            }
        }

        if (!changed.isEmpty()) {
            pushActive(oldType, newType, changed);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<TypeMirror, TypeMirror> changed = (Map<TypeMirror, TypeMirror>) types.context[0];

        List<Difference> ret = new ArrayList<>();
        for (Map.Entry<TypeMirror, TypeMirror> e : changed.entrySet()) {
            ret.add(createDifference(Code.CLASS_SUPER_TYPE_TYPE_PARAMETERS_CHANGED,
                new String[]{Util.toHumanReadableString(e.getKey()), Util.toHumanReadableString(e.getValue())},
                e.getKey(), e.getValue()));
        }

        return ret;
    }
}
