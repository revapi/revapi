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

import org.revapi.java.JavaModelElement;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
final class JavaElementFactory {
    private JavaElementFactory() {

    }

    @SuppressWarnings("unchecked")
    public static JavaModelElement elementFor(Element modelElement, ProbingEnvironment env) {
        if (modelElement instanceof javax.lang.model.element.TypeElement) {
            return new TypeElement(env, (javax.lang.model.element.TypeElement) modelElement);
        } else if (modelElement instanceof VariableElement &&
            modelElement.getEnclosingElement() instanceof javax.lang.model.element.TypeElement) {
            return new FieldElement(env, (VariableElement) modelElement);
        } else if (modelElement instanceof VariableElement &&
            modelElement.getEnclosingElement() instanceof ExecutableElement) {
            return new MethodParameterElement(env, (VariableElement) modelElement);
        } else if (modelElement instanceof ExecutableElement) {
            return new MethodElement(env, (ExecutableElement) modelElement);
        } else {
            throw new IllegalArgumentException("Unsupported model element: " + modelElement.getClass());
        }

        //TODO I could see use for PackageElement, because packages can have annotations on them
    }

    public static int compareByType(org.revapi.Element a, org.revapi.Element b) {
        int ar = a == null ? -1 : getModelTypeRank(a.getClass());
        int br = b == null ? -1 : getModelTypeRank(b.getClass());
        return ar - br;
    }

    private static int getModelTypeRank(Class<?> cls) {
        if (cls == AnnotationElement.class) {
            return 4;
        } else if (cls == FieldElement.class) {
            return 1;
        } else if (cls == MethodElement.class) {
            return 2;
        } else if (cls == MethodParameterElement.class) {
            return 3;
        } else if (cls == TypeElement.class) {
            return 0;
        } else {
            return -1;
        }
    }
}
