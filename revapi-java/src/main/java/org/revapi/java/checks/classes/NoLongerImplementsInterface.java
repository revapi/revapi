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
 * 
 * @since 0.1
 */
public final class NoLongerImplementsInterface extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (!isBothAccessible(oldType, newType)) {
            return;
        }

        List<TypeMirror> newInterfaces = Util.getAllSuperInterfaces(getNewTypeEnvironment().getTypeUtils(),
                newType.getModelRepresentation());

        List<TypeMirror> oldInterfaces = Util.getAllSuperInterfaces(getOldTypeEnvironment().getTypeUtils(),
                oldType.getModelRepresentation());

        for (TypeMirror oldIface : oldInterfaces) {
            if (!Util.isSubtype(oldIface, newInterfaces, getOldTypeEnvironment().getTypeUtils())) {
                pushActive(oldType, newType, oldInterfaces, newInterfaces);
                break;
            }
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types == null) {
            return null;
        }

        List<Difference> result = new ArrayList<>();

        @SuppressWarnings("unchecked")
        List<TypeMirror> oldInterfaces = (List<TypeMirror>) types.context[0];

        @SuppressWarnings("unchecked")
        List<TypeMirror> newInterfaces = (List<TypeMirror>) types.context[1];

        for (TypeMirror oldIface : oldInterfaces) {
            if (!Util.isSubtype(oldIface, newInterfaces, getOldTypeEnvironment().getTypeUtils())) {
                result.add(createDifference(Code.CLASS_NO_LONGER_IMPLEMENTS_INTERFACE, Code.attachmentsFor(
                        types.oldElement, types.newElement, "interface", Util.toHumanReadableString(oldIface))));
            }
        }

        return result;
    }
}
