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

package org.revapi.java.checks.classes;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class InheritanceChainChanged extends AbstractJavaCheck {
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();
        if (types != null) {

            List<Difference> ret = new ArrayList<>();

            @SuppressWarnings("unchecked")
            List<TypeMirror> oldSuperTypes = (List<TypeMirror>) types.context[0];
            @SuppressWarnings("unchecked")
            List<TypeMirror> newSuperTypes = (List<TypeMirror>) types.context[1];

            for (TypeMirror ot : oldSuperTypes) {
                boolean found = false;
                for (TypeMirror nt : newSuperTypes) {
                    if (Util.isSameType(ot, nt)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    ret.add(createDifference(Code.CLASS_NO_LONGER_INHERITS_FROM_CLASS,
                        new String[]{Util.toHumanReadableString(ot)}, ot));
                }
            }

            for (TypeMirror nt : newSuperTypes) {
                boolean found = false;
                for (TypeMirror ot : oldSuperTypes) {
                    if (Util.isSameType(ot, nt)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    ret.add(createDifference(Code.CLASS_INHERITS_FROM_NEW_CLASS,
                        new String[]{Util.toHumanReadableString(nt)}, nt));
                }
            }

            return ret;
        }

        return null;
    }

    @Override
    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
        if (oldType == null || newType == null) {
            return;
        }

        if (isBothPrivate(oldType, newType)) {
            return;
        }

        List<TypeMirror> oldSuperTypes = Util
            .getAllSuperClasses(getOldTypeEnvironment().getTypeUtils(), oldType.asType());
        List<TypeMirror> newSuperTypes = Util
            .getAllSuperClasses(getNewTypeEnvironment().getTypeUtils(), newType.asType());

        if (oldSuperTypes.size() != newSuperTypes.size()) {
            pushActive(oldType, newType, oldSuperTypes, newSuperTypes);
        } else {
            for (int i = 0; i < oldSuperTypes.size(); ++i) {
                TypeMirror oldSuperClass = oldSuperTypes.get(i);
                TypeMirror newSuperClass = newSuperTypes.get(i);

                if (!Util.isSameType(oldSuperClass, newSuperClass)) {
                    pushActive(oldType, newType, oldSuperTypes, newSuperTypes);
                    break;
                }
            }
        }
    }
}
