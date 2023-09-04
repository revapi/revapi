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

import java.util.EnumSet;

import javax.lang.model.element.Modifier;

import org.revapi.java.checks.common.ModifierChanged;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaFieldElement;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class NowFinal extends ModifierChanged {

    public NowFinal() {
        super(true, Code.FIELD_NOW_FINAL, Modifier.FINAL);
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.FIELD);
    }

    @Override
    protected void doVisitField(JavaFieldElement oldField, JavaFieldElement newField) {
        super.doVisit(oldField, newField);
    }
}
