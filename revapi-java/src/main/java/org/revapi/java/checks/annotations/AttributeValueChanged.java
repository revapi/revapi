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

package org.revapi.java.checks.annotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;

import org.revapi.MatchReport;
import org.revapi.MismatchSeverity;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class AttributeValueChanged extends AbstractJavaCheck {
    @Override
    protected List<MatchReport.Problem> doVisitAnnotation(AnnotationMirror oldAnnotation,
        AnnotationMirror newAnnotation) {

        if (oldAnnotation == null || newAnnotation == null) {
            return null;
        }

        List<MatchReport.Problem> result = new ArrayList<>();

        Map<String, AnnotationValue> oldAttrs = Util.convert(oldAnnotation.getElementValues());
        Map<String, AnnotationValue> newAttrs = Util.convert(newAnnotation.getElementValues());

        for (Map.Entry<String, AnnotationValue> oldE : oldAttrs.entrySet()) {
            String name = oldE.getKey();
            AnnotationValue oldValue = oldE.getValue();
            AnnotationValue newValue = newAttrs.get(name);

            //TODO should be probably passing the ExecutableElement instead of name as the attachment...
            if (newValue == null) {
                result.add(
                    createProblem(Code.ANNOTATION_ATTRIBUTE_REMOVED, MismatchSeverity.NOTICE, MismatchSeverity.NOTICE,
                        new String[]{name, Util.toHumanReadableString(oldAnnotation.getAnnotationType())}, name,
                        oldAnnotation));
            } else if (!Util.isEqual(oldValue, newValue)) {
                result.add(createProblem(Code.ANNOTATION_ATTRIBUTE_VALUE_CHANGED, MismatchSeverity.NOTICE,
                    MismatchSeverity.NOTICE,
                    new String[]{name, Util.toHumanReadableString(oldValue), Util.toHumanReadableString(newValue)},
                    name, oldValue, newValue));
            }
        }

        for (Map.Entry<String, AnnotationValue> newE : newAttrs.entrySet()) {
            String name = newE.getKey();
            AnnotationValue oldValue = oldAttrs.get(name);

            if (oldValue == null) {
                //TODO should be probably passing the ExecutableElement instead of name as the attachment...
                result.add(
                    createProblem(Code.ANNOTATION_ATTRIBUTE_ADDED, MismatchSeverity.NOTICE, MismatchSeverity.NOTICE,
                        new String[]{name, Util.toHumanReadableString(newAnnotation.getAnnotationType())}, name,
                        newAnnotation));
            }
        }

        //TODO implement
        return super.doVisitAnnotation(oldAnnotation, newAnnotation);
    }

    //TODO implement
}
