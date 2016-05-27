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

package org.revapi.java.checks.fields;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Removed extends CheckBase {

    private TypeElement activeNewType;

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.CLASS, Type.FIELD);
    }

    @Override
    protected void doVisitClass(@Nullable TypeElement oldType, @Nullable TypeElement newType) {
        activeNewType = newType;
    }

    @Override
    protected void doVisitField(VariableElement oldField, VariableElement newField) {
        if (oldField != null && newField == null && isAccessible(oldField, getOldTypeEnvironment())) {

            pushActive(oldField, null);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        CheckBase.ActiveElements<VariableElement> fields = popIfActive();

        if (fields == null) {
            return null;
        }

        boolean isConstant = fields.oldElement.getConstantValue() != null;

        //check if the field exists in a super-class in the new type environment
        if (activeNewType != null) {
            String oldFieldType = Util.toUniqueString(fields.oldElement.asType());

            //we know that the new type doesn't contain the field, so let's search its super classes
            for (TypeMirror superType : Util.getAllSuperClasses(getNewTypeEnvironment().getTypeUtils(),
                    activeNewType.asType())) {

                if (!(superType instanceof DeclaredType)) {
                    continue;
                }

                DeclaredType st = (DeclaredType) superType;

                List<VariableElement> superFields = ElementFilter.fieldsIn(st.asElement().getEnclosedElements());

                for (VariableElement superField : superFields) {
                    if (superField.getSimpleName().contentEquals(fields.oldElement.getSimpleName())) {
                        if (!isAccessible(superField, getNewTypeEnvironment())) {
                            continue;
                        }

                        String newFieldType = Util.toUniqueString(superField.asType());

                        if (oldFieldType.equals(newFieldType)) {
                            //so the field with the same name and type exists in one of the super classes in the
                            //new type environment... This is compatible...
                            return Collections.singletonList(createDifference(Code.FIELD_MOVED_TO_SUPER_CLASS,
                                    Util.toHumanReadableString(fields.oldElement.getEnclosingElement()),
                                    Util.toHumanReadableString(st.asElement())));
                        }
                    }
                }
            }
        }

        return Collections
            .singletonList(createDifference(isConstant ? Code.FIELD_CONSTANT_REMOVED : Code.FIELD_REMOVED));
    }
}
