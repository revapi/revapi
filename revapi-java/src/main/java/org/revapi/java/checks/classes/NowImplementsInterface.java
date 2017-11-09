/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi.java.checks.classes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NowImplementsInterface extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (!isBothAccessible(oldType, newType)) {
            return;
        }

        //hmm, these might not be right I assume if the types are inner classes parameterized by the type parameters
        //of the containing class
        List<? extends TypeMirror> newInterfaces = newType.getDeclaringElement().getInterfaces();
        List<? extends TypeMirror> oldInterfaces = oldType.getDeclaringElement().getInterfaces();

        for (TypeMirror newIface : newInterfaces) {
            if (!Util.isSubtype(newIface, oldInterfaces, getNewTypeEnvironment().getTypeUtils())) {
                pushActive(oldType, newType);
                break;
            }
        }
    }

    @Override
    protected List<Difference> doEnd() {
        CheckBase.ActiveElements<JavaTypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        List<Difference> result = new ArrayList<>();

        List<? extends TypeMirror> newInterfaces = types.newElement.getDeclaringElement().getInterfaces();
        List<? extends TypeMirror> oldInterfaces = types.oldElement.getDeclaringElement().getInterfaces();

        for (TypeMirror newIface : newInterfaces) {
            if (!Util.isSubtype(newIface, oldInterfaces, getNewTypeEnvironment().getTypeUtils())) {
                result.add(
                    createDifference(Code.CLASS_NOW_IMPLEMENTS_INTERFACE,
                            Code.attachmentsFor(types.oldElement, types.newElement,
                                    "interface", Util.toHumanReadableString(newIface)))
                );
            }
        }

        return result;
    }
}
