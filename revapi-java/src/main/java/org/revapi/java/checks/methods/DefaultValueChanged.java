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

package org.revapi.java.checks.methods;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class DefaultValueChanged extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaMethodElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        AnnotationValue oldValue = methods.oldElement.getDeclaringElement().getDefaultValue();
        AnnotationValue newValue = methods.newElement.getDeclaringElement().getDefaultValue();

        String attribute = methods.oldElement.getDeclaringElement().getSimpleName().toString();
        String annotationType = ((TypeElement) methods.oldElement.getDeclaringElement().getEnclosingElement()).getQualifiedName().toString();
        String ov = oldValue == null ? null : Util.toHumanReadableString(oldValue);
        String nv = newValue == null ? null : Util.toHumanReadableString(newValue);

        Difference difference;

        if (ov == null) {
            difference = createDifference(Code.METHOD_DEFAULT_VALUE_ADDED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement,
                            "value", nv));
        } else if (nv == null) {
            difference = createDifference(Code.METHOD_DEFAULT_VALUE_REMOVED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement,
                            "value", ov));
        } else {
            difference = createDifferenceWithExplicitParams(Code.METHOD_DEFAULT_VALUE_CHANGED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement,
                            "oldValue", ov,
                            "newValue", nv),
                    attribute, annotationType, ov, nv);
        }

        return Collections.singletonList(difference);
    }

    @Override
    protected void doVisitMethod(JavaMethodElement oldMethod, JavaMethodElement newMethod) {
        if (oldMethod == null || newMethod == null || isBothPrivate(oldMethod, newMethod)) {
            return;
        }

        AnnotationValue oldVal = oldMethod.getDeclaringElement().getDefaultValue();
        AnnotationValue newVal = newMethod.getDeclaringElement().getDefaultValue();

        boolean equal =
            oldVal != null && newVal != null && Util.isEqual(oldVal, newVal) || (oldVal == null && newVal == null);


        if (!equal) {
            pushActive(oldMethod, newMethod);
        }
    }

}
