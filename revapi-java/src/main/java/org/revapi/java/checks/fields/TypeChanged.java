/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java.checks.fields;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaFieldElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class TypeChanged extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.FIELD);
    }

    @Override
    protected void doVisitField(JavaFieldElement oldField, JavaFieldElement newField) {
        if (!isBothAccessible(oldField, newField)) {
            return;
        }

        String oldType = Util.toUniqueString(
                oldField.getTypeEnvironment().getTypeUtils().erasure(oldField.getModelRepresentation()));
        String newType = Util.toUniqueString(
                newField.getTypeEnvironment().getTypeUtils().erasure(newField.getModelRepresentation()));

        if (!oldType.equals(newType)) {
            pushActive(oldField, newField);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaFieldElement> fields = popIfActive();
        if (fields == null) {
            return null;
        }

        String oldType = Util.toHumanReadableString(fields.oldElement.getModelRepresentation());
        String newType = Util.toHumanReadableString(fields.newElement.getModelRepresentation());

        return Collections.singletonList(createDifference(Code.FIELD_TYPE_CHANGED,
                Code.attachmentsFor(fields.oldElement, fields.newElement, "oldType", oldType, "newType", newType)));
    }
}
