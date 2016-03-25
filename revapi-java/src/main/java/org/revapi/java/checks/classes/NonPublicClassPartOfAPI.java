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

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NonPublicClassPartOfAPI extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return isSkipUseTracking() ? EnumSet.noneOf(Type.class) : EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(TypeElement oldType, TypeElement newType) {
        if (newType == null) {
            return;
        }

        if (isAccessible(newType, getNewTypeEnvironment()) && !isAccessibleByModifier(newType)) {
            pushActive(oldType, newType);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<TypeElement> types = popIfActive();

        if (types == null) {
            return null;
        }

        return Collections
            .singletonList(createDifference(Code.CLASS_NON_PUBLIC_PART_OF_API, new Object[]{types.newElement},
                Util.toHumanReadableString(types.newElement)));
    }

    private boolean isAccessibleByModifier(Element e) {
        return !isMissing(e) && (e.getModifiers().contains(Modifier.PUBLIC) ||
                e.getModifiers().contains(Modifier.PROTECTED));    }
}
