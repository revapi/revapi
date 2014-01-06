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

package org.revapi.java.model;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import org.revapi.java.JavaElement;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
class JavaElementFactory {
    private JavaElementFactory() {

    }

    @SuppressWarnings("unchecked")
    public static <T extends Element> JavaElement<T> elementFor(T modelElement, ProbingEnvironment env) {
        if (modelElement instanceof javax.lang.model.element.TypeElement) {
            return (JavaElement<T>) new TypeElement(env, (javax.lang.model.element.TypeElement) modelElement);
        } else if (modelElement instanceof VariableElement &&
            modelElement.getEnclosingElement() instanceof javax.lang.model.element.TypeElement) {
            return (JavaElement<T>) new FieldElement(env, (VariableElement) modelElement);
        } else if (modelElement instanceof VariableElement &&
            modelElement.getEnclosingElement() instanceof ExecutableElement) {
            return (JavaElement<T>) new MethodParameterElement(env, (VariableElement) modelElement);
        } else if (modelElement instanceof ExecutableElement) {
            return (JavaElement<T>) new MethodElement(env, (ExecutableElement) modelElement);
        } else {
            //TODO uncomment once all implemented
            //throw new IllegalArgumentException("Unsupported model element: " + modelElement.getClass());
            return null;
        }
    }
}
