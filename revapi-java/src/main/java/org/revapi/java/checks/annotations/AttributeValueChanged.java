/*
 * Copyright 2014-2017 Lukas Krejci
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
package org.revapi.java.checks.annotations;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class AttributeValueChanged extends CheckBase {
    @Override
    protected List<Difference> doVisitAnnotation(JavaAnnotationElement oldElement,
        JavaAnnotationElement newElement) {

        if (oldElement == null || newElement == null || !isAccessible(newElement.getParent())) {
            return null;
        }

        AnnotationMirror oldAnnotation = oldElement.getAnnotation();
        AnnotationMirror newAnnotation = newElement.getAnnotation();

        List<Difference> result = new ArrayList<>();

        Map<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> oldAttrs = Util
            .keyAnnotationAttributesByName(oldAnnotation.getElementValues());
        Map<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> newAttrs = Util
            .keyAnnotationAttributesByName(newAnnotation.getElementValues());

        for (Map.Entry<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> oldE : oldAttrs
            .entrySet()) {

            String name = oldE.getKey();
            Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> oldValue = oldE.getValue();
            Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> newValue = newAttrs.get(name);

            if (newValue == null) {
                result.add(
                    createDifference(Code.ANNOTATION_ATTRIBUTE_REMOVED, Code.attachmentsFor(oldElement.getParent(),
                            newElement.getParent(),
                            "annotationType", Util.toHumanReadableString(newElement.getAnnotation().getAnnotationType()),
                            "annotation", Util.toHumanReadableString(newElement.getAnnotation()),
                            "attribute", name,
                            "value", Util.toHumanReadableString(oldValue.getValue())))
                );
            } else if (!Util.isEqual(oldValue.getValue(), newValue.getValue())) {
                result.add(createDifference(Code.ANNOTATION_ATTRIBUTE_VALUE_CHANGED,
                        Code.attachmentsFor(oldElement.getParent(), newElement.getParent(),
                                "annotationType", Util.toHumanReadableString(newElement.getAnnotation().getAnnotationType()),
                                "annotation", Util.toHumanReadableString(newElement.getAnnotation()),
                                "attribute", name,
                                "oldValue", Util.toHumanReadableString(oldValue.getValue()),
                                "newValue", Util.toHumanReadableString(newValue.getValue()))
                ));
            }

            newAttrs.remove(name);
        }

        for (Map.Entry<String, Map.Entry<? extends ExecutableElement, ? extends AnnotationValue>> newE : newAttrs
            .entrySet()) {
            String name = newE.getKey();
            Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> newValue = newE.getValue();
            Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> oldValue = oldAttrs.get(name);

            if (oldValue == null) {
                result.add(
                    createDifference(Code.ANNOTATION_ATTRIBUTE_ADDED, Code.attachmentsFor(oldElement.getParent(),
                            newElement.getParent(),
                            "annotationType", Util.toHumanReadableString(newElement.getAnnotation().getAnnotationType()),
                            "annotation", Util.toHumanReadableString(newElement.getAnnotation()),
                            "attribute", name,
                            "value", Util.toHumanReadableString(newValue.getValue())))
                );
            }
        }

        return result.isEmpty() ? null : result;
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.ANNOTATION);
    }
}
