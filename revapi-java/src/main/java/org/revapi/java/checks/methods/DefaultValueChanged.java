/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java.checks.methods;

import java.util.List;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

import org.revapi.MatchReport;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class DefaultValueChanged extends AbstractJavaCheck {

    @Override
    protected List<MatchReport.Problem> doEnd() {
        ActiveElements<ExecutableElement> methods = popIfActive();
        if (methods != null) {
            AnnotationValue oldValue = methods.oldElement.getDefaultValue();
            AnnotationValue newValue = methods.newElement.getDefaultValue();

            //TODO implement
        }

        return null;
    }

    @Override
    protected void doVisitMethod(ExecutableElement oldMethod, ExecutableElement newMethod) {
        if (oldMethod == null || newMethod == null) {
            return;
        }

        AnnotationValue oldVal = oldMethod.getDefaultValue();
        AnnotationValue newVal = newMethod.getDefaultValue();

        boolean equal = Util.isEqual(oldVal, newVal);

        if (!equal) {
            pushActive(oldMethod, newMethod);
        }
    }

}
