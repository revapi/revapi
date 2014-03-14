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
import java.util.List;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

import org.revapi.Difference;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class DefaultValueChanged extends AbstractJavaCheck {

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<ExecutableElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        AnnotationValue oldValue = methods.oldElement.getDefaultValue();
        AnnotationValue newValue = methods.newElement.getDefaultValue();

        String attribute = methods.oldElement.getSimpleName().toString();
        String annotationType = ((TypeElement) methods.oldElement.getEnclosingElement()).getQualifiedName().toString();
        String ov = oldValue == null ? null : Util.toHumanReadableString(oldValue);
        String nv = newValue == null ? null : Util.toHumanReadableString(newValue);

        Difference difference;

        if (ov == null) {
            difference = createDifference(Code.METHOD_DEFAULT_VALUE_ADDED);
        } else if (nv == null) {
            difference = createDifference(Code.METHOD_DEFAULT_VALUE_REMOVED);
        } else {
            difference = createDifference(Code.METHOD_DEFAULT_VALUE_CHANGED,
                new String[]{attribute, annotationType, ov, nv}, oldValue, newValue);
        }

        return Collections.singletonList(difference);
    }

    @Override
    protected void doVisitMethod(ExecutableElement oldMethod, ExecutableElement newMethod) {
        if (oldMethod == null || newMethod == null || isBothPrivate(oldMethod, newMethod)) {
            return;
        }

        AnnotationValue oldVal = oldMethod.getDefaultValue();
        AnnotationValue newVal = newMethod.getDefaultValue();

        boolean equal =
            oldVal != null && newVal != null && Util.isEqual(oldVal, newVal) || (oldVal == null && newVal == null);


        if (!equal) {
            pushActive(oldMethod, newMethod);
        }
    }

}
