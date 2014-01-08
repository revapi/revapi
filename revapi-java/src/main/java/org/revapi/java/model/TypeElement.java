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

import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class TypeElement extends JavaElementBase<javax.lang.model.element.TypeElement> {
    private final String className;

    public TypeElement(ProbingEnvironment env, String className) {
        super(env, null);
        this.className = className;
    }

    public TypeElement(ProbingEnvironment env, javax.lang.model.element.TypeElement element) {
        super(env, element);
        this.className = element.getQualifiedName().toString();
    }

    @Override
    public javax.lang.model.element.TypeElement getModelElement() {
        if (element == null) {
            element = environment.getElementUtils().getTypeElement(className);
        }
        return element;
    }

    public String getExplicitClassName() {
        return className;
    }

    @Override
    public int compareTo(org.revapi.Element o) {
        if (!(o instanceof TypeElement)) {
            return 1;
        }

        return className.compareTo(((TypeElement) o).className);
    }

    @Override
    public String toString() {
        return className;
    }
}
