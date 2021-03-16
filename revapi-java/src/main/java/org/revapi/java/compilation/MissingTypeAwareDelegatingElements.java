/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.java.compilation;

import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

import org.revapi.java.model.MissingTypeElement;
import org.revapi.java.spi.IgnoreCompletionFailures;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
final class MissingTypeAwareDelegatingElements implements Elements {
    private final Elements elements;

    MissingTypeAwareDelegatingElements(Elements elements) {
        this.elements = elements;
    }

    @Override
    public PackageElement getPackageElement(CharSequence name) {
        return IgnoreCompletionFailures.in(elements::getPackageElement, name);
    }

    @Override
    public TypeElement getTypeElement(CharSequence name) {
        return IgnoreCompletionFailures.in(elements::getTypeElement, name);
    }

    @Override
    public Map<? extends ExecutableElement, ? extends AnnotationValue> getElementValuesWithDefaults(
            AnnotationMirror a) {

        return IgnoreCompletionFailures.in(elements::getElementValuesWithDefaults, a);
    }

    @Override
    public String getDocComment(Element e) {
        if (MissingTypeElement.isMissing(e)) {
            return "";
        }
        return IgnoreCompletionFailures.in(elements::getDocComment, e);
    }

    @Override
    public boolean isDeprecated(Element e) {
        if (MissingTypeElement.isMissing(e)) {
            return false;
        }

        return IgnoreCompletionFailures.in(elements::isDeprecated, e);
    }

    @Override
    public Name getBinaryName(TypeElement type) {
        if (MissingTypeElement.isMissing(type)) {
            return type.getQualifiedName();
        }

        return IgnoreCompletionFailures.in(elements::getBinaryName, type);
    }

    @Override
    public PackageElement getPackageOf(Element type) {
        if (MissingTypeElement.isMissing(type)) {
            String binaryName = ((MissingTypeElement) type).getQualifiedName().toString();
            int lastDot = binaryName.lastIndexOf('.');

            return elements.getPackageElement(binaryName.substring(0, lastDot));
        }

        return IgnoreCompletionFailures.in(elements::getPackageOf, type);
    }

    @Override
    public List<? extends Element> getAllMembers(TypeElement type) {
        if (MissingTypeElement.isMissing(type)) {
            return Collections.emptyList();
        }

        return IgnoreCompletionFailures.in(elements::getAllMembers, type);
    }

    @Override
    public List<? extends AnnotationMirror> getAllAnnotationMirrors(Element e) {
        if (MissingTypeElement.isMissing(e)) {
            return Collections.emptyList();
        }
        return IgnoreCompletionFailures.in(elements::getAllAnnotationMirrors, e);
    }

    @Override
    public boolean hides(Element hider, Element hidden) {
        if (MissingTypeElement.isMissing(hider) || MissingTypeElement.isMissing(hidden)) {
            return false;
        }

        return IgnoreCompletionFailures.in(elements::hides, hider, hidden);
    }

    @Override
    public boolean overrides(ExecutableElement overrider, ExecutableElement overridden, TypeElement type) {
        if (MissingTypeElement.isMissing(type)) {
            return false;
        }

        return IgnoreCompletionFailures.in(elements::overrides, overrider, overridden, type);
    }

    @Override
    public String getConstantExpression(Object value) {
        return IgnoreCompletionFailures.in(elements::getConstantExpression, value);
    }

    @Override
    public void printElements(Writer w, Element... elems) {
        IgnoreCompletionFailures.inVoid(elements::printElements, w, elems);
    }

    @Override
    public boolean isFunctionalInterface(TypeElement type) {
        return elements.isFunctionalInterface(type);
    }

    @Override
    public Name getName(CharSequence cs) {
        try {
            return elements.getName(cs);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
