/*
 * Copyright 2014-2018 Lukas Krejci
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

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Added extends CheckBase {
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaTypeElement> types = popIfActive();
        if (types != null) {
            TypeElement typeInOld = getOldTypeEnvironment().getElementUtils()
                    .getTypeElement(types.newElement.getDeclaringElement().getQualifiedName());

            LinkedHashMap<String, String> attachments = Code.attachmentsFor(types.oldElement, types.newElement);
            Difference difference = typeInOld == null
                    ? createDifference(Code.CLASS_ADDED, attachments)
                    : createDifference(Code.CLASS_EXTERNAL_CLASS_EXPOSED_IN_API, attachments);

            return Collections.singletonList(difference);
        }

        return null;
    }

    @Override
    protected void doVisitClass(JavaTypeElement oldType, JavaTypeElement newType) {
        if (oldType == null && newType != null && isAccessible(newType)) {
            pushActive(null, newType);
        }
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS);
    }
}
