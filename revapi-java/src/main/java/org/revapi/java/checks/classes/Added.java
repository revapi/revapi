/*
 * Copyright 2015 Lukas Krejci
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Added extends CheckBase {
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();
        if (types != null) {
            TypeElement typeInOld = getOldTypeEnvironment().getElementUtils().getTypeElement(types.newElement
                    .getQualifiedName());

            Difference difference = typeInOld == null ? createDifference(Code.CLASS_ADDED) :
                    createDifference(Code.CLASS_EXTERNAL_CLASS_EXPOSED_IN_API);

            return Collections.singletonList(difference);
        }

        return null;
    }

    @Override
    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
        if (oldType == null && newType != null && isAccessible(newType, getNewTypeEnvironment())) {
            pushActive(null, newType);
        }
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }
}
