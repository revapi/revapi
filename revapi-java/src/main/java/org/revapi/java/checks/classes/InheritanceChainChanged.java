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

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

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

            int newTypeIdx = 0;
            for (TypeMirror nt : newSuperTypes) {
                boolean found = false;
                for (TypeMirror ot : oldSuperTypes) {
                    if (Util.isSameType(ot, nt)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    Code code = types.oldElement.getModifiers().contains(Modifier.FINAL)
                        ? Code.CLASS_FINAL_CLASS_INHERITS_FROM_NEW_CLASS
                        : Code.CLASS_NON_FINAL_CLASS_INHERITS_FROM_NEW_CLASS;

                    //only add the most concrete changes
                    //we exploit the fact that the Util.getAllSuperClasses() method returns the super types
                    //in a breadth-first-search manner with the direct super types first.
                    boolean mostConcrete = true;
                    Types newTypeEnv = getNewTypeEnvironment().getTypeUtils();
                    for (int i = newTypeIdx - 1; i >= 0; --i) {
                        TypeMirror previousNewSuperType = newSuperTypes.get(i);
                        if (newTypeEnv.isSubtype(previousNewSuperType, nt)) {
                            mostConcrete = false;
                        }
                    }

                    if (mostConcrete) {
                        ret.add(createDifference(code, new String[]{Util.toHumanReadableString(nt)}, nt));

                        //additionally add a difference about checked exceptions
                        if (changedToCheckedException(getNewTypeEnvironment().getTypeUtils(), nt, oldSuperTypes)) {
                            code = Code.CLASS_NOW_CHECKED_EXCEPTION;
                            ret.add(createDifference(code));
                        }
                    }
                }

                ++newTypeIdx;
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

    private boolean changedToCheckedException(Types newTypeEnv, TypeMirror newType, List<TypeMirror> oldTypes) {
        if ("java.lang.Exception".equals(Util.toHumanReadableString(newType))) {
            return isTypeThrowable(oldTypes);
        } else {
            for (TypeMirror sc : Util.getAllSuperClasses(newTypeEnv, newType)) {
                if ("java.lang.Exception".equals(Util.toHumanReadableString(sc))) {
                    return isTypeThrowable(oldTypes);
                }
            }
        }

        return false;
    }

    private boolean isTypeThrowable(List<TypeMirror> superClassesOfType) {
        for (TypeMirror sc : superClassesOfType) {
            if (Util.toHumanReadableString(sc).equals("java.lang.Throwable")) {
                return true;
            }
        }

        return false;
    }
}
