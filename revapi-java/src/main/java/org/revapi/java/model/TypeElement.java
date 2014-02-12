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

import org.revapi.java.Util;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class TypeElement extends JavaElementBase<javax.lang.model.element.TypeElement> {
    private final String binaryName;
    private final String canonicalName;

    /**
     * This is a helper constructor used only during probing the class files. All these fields are here to gather
     * information needed to generate a meaningful probe class.
     *
     * @param env probing environment
     */
    public TypeElement(ProbingEnvironment env, String binaryName, String canonicalName) {
        super(env, null);
        this.binaryName = binaryName;
        this.canonicalName = canonicalName;
    }

    public TypeElement(ProbingEnvironment env, javax.lang.model.element.TypeElement element) {
        super(env, element);
        binaryName = env.getElementUtils().getBinaryName(element).toString();
        canonicalName = element.getQualifiedName().toString();
    }

    @Override
    public javax.lang.model.element.TypeElement getModelElement() {
        if (element == null && environment.getElementUtils() != null) {
            element = environment.getElementUtils().getTypeElement(canonicalName);
        }
        return element;
    }

    public String getBinaryName() {
        return binaryName;
    }

    public String getCanonicalName() {
        return canonicalName;
    }

    @Override
    public int compareTo(org.revapi.Element o) {
        if (!(o instanceof TypeElement)) {
            return JavaElementFactory.compareByType(this, o);
        }

        return binaryName.compareTo(((TypeElement) o).binaryName);
    }

    @Override
    public String toString() {
        javax.lang.model.element.TypeElement el = getModelElement();
        return el == null ? canonicalName : Util.toHumanReadableString(el);
    }
}
