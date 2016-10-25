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

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NonPublicClassPartOfAPI extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (newType == null) {
            return;
        }

        if (newType.isInAPI() && !isAccessible(newType)) {
            pushActive(oldType, newType);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();

        if (types == null) {
            return null;
        }

        return Collections
            .singletonList(createDifference(Code.CLASS_NON_PUBLIC_PART_OF_API, new Object[]{types.newElement},
                Util.toHumanReadableString(types.newElement.getModelRepresentation())));
    }
}
