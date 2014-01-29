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

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.ChangeSeverity;
import org.revapi.MatchReport;
import org.revapi.java.CheckBase;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NowImplementsInterface extends AbstractJavaCheck {

    @Override
    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
        if (oldType == null || newType == null) {
            return;
        }

        List<? extends TypeMirror> newInterfaces = newType.getInterfaces();
        List<? extends TypeMirror> oldInterfaces = oldType.getInterfaces();

        for (TypeMirror newIface : newInterfaces) {
            if (!Util.isSubtype(newIface, oldInterfaces, newTypeEnvironment.getTypeUtils())) {
                pushActive(oldType, newType);
                break;
            }
        }
    }

    @Override
    protected List<MatchReport.Problem> doEnd() {
        CheckBase.ActiveElements<TypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        List<MatchReport.Problem> result = new ArrayList<>();

        List<? extends TypeMirror> newInterfaces = types.newElement.getInterfaces();
        List<? extends TypeMirror> oldInterfaces = types.oldElement.getInterfaces();

        for (TypeMirror newIface : newInterfaces) {
            if (!Util.isSubtype(newIface, oldInterfaces, newTypeEnvironment.getTypeUtils())) {
                result.add(
                    createProblem(Code.CLASS_NOW_IMPLEMENTS_INTERFACE, ChangeSeverity.BREAKING, ChangeSeverity.BREAKING,
                        new String[]{
                            Util.toHumanReadableString(newIface)}, newIface));
            }
        }

        return result;
    }
}
