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

package org.revapi.java.checks.fields;

import org.revapi.Difference;
import org.revapi.java.spi.Code;

import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class EnumConstantsOrderChanged extends BothFieldsRequiringCheck {
    private boolean isEnumClass;

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS, Type.FIELD);
    }

    @Override
    protected void doVisitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
        isEnumClass = newType != null && newType.getKind() == ElementKind.ENUM;
    }

    @Override
    protected boolean shouldCheck(VariableElement oldField, VariableElement newField) {
        return isEnumClass && super.shouldCheck(oldField, newField) && oldField.getKind() == ElementKind.ENUM_CONSTANT
                && newField.getKind() == ElementKind.ENUM_CONSTANT;
    }

    @Override
    @SuppressWarnings("ConstantConditions")
    protected void doVisitField(@Nullable VariableElement oldField, @Nullable VariableElement newField) {
        if (!shouldCheck(oldField, newField)) {
            return;
        }

        Predicate<VariableElement> isNotEnumConstant = v -> v.getKind() != ElementKind.ENUM_CONSTANT;

        List<? extends VariableElement> fields = ElementFilter.fieldsIn(oldField.getEnclosingElement()
                .getEnclosedElements());
        fields.removeIf(isNotEnumConstant);

        int oldIdx = fields.indexOf(oldField);

        fields = ElementFilter.fieldsIn(newField.getEnclosingElement().getEnclosedElements());
        fields.removeIf(isNotEnumConstant);

        int newIdx = fields.indexOf(newField);

        if (newIdx != oldIdx) {
            pushActive(oldField, newField, oldIdx, newIdx);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<VariableElement> fields = popIfActive();
        if (fields == null) {
            return null;
        }

        int oldIdx = (Integer) fields.context[0];
        int newIdx = (Integer) fields.context[1];

        return Collections.singletonList(createDifference(Code.FIELD_ENUM_CONSTANT_ORDER_CHANGED, oldIdx, newIdx));
    }
}
