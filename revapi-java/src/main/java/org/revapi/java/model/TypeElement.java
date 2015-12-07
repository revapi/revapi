/*
 * Copyright 2015 Lukas Krejci
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

package org.revapi.java.model;

import javax.annotation.Nonnull;

import org.revapi.Archive;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class TypeElement extends JavaElementBase<javax.lang.model.element.TypeElement> implements JavaTypeElement {
    private final String binaryName;
    private final String canonicalName;

    /**
     * This is a helper constructor used only during probing the class files. This is to ensure that we have a
     * "bare bones" type element available even before we have functioning compilation infrastructure in
     * the environment.
     *
     * @param env           probing environment
     * @param binaryName    the binary name of the class
     * @param canonicalName the canonical name of the class
     */
    public TypeElement(ProbingEnvironment env, Archive archive, String binaryName, String canonicalName) {
        super(env, archive, null);
        this.binaryName = binaryName;
        this.canonicalName = canonicalName;
    }

    /**
     * This constructor is used under "normal working conditions" when the probing environment already has
     * the compilation infrastructure available (which is assumed since otherwise it would not be possible to obtain
     * instances of the javax.lang.model.element.TypeElement interface).
     *
     * @param env     the probing environment
     * @param element the model element to be represented
     */
    public TypeElement(ProbingEnvironment env, Archive archive, javax.lang.model.element.TypeElement element) {
        super(env, archive, element);
        binaryName = env.getElementUtils().getBinaryName(element).toString();
        canonicalName = element.getQualifiedName().toString();
    }

    public boolean isInnerClass() {
        int dotPos = -1;

        do {
            dotPos = canonicalName.indexOf('.', dotPos + 1);
            if (dotPos >= 0 && binaryName.charAt(dotPos) == '$') {
                return true;
            }
        } while (dotPos >= 0);

        return false;
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "class";
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public javax.lang.model.element.TypeElement getModelElement() {
        //even though environment.getElementUtils() is marked @Nonnull, we do the check here, because
        //it actually IS null for a while during initialization of the forest during compilation.
        //we do this so that toString() works even under those conditions.
        if (element == null && environment.hasProcessingEnvironment()) {
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
    public int compareTo(@Nonnull org.revapi.Element o) {
        if (!(o.getClass().equals(TypeElement.class))) {
            return JavaElementFactory.compareByType(this, o);
        }

        return binaryName.compareTo(((TypeElement) o).binaryName);
    }

    @Nonnull
    @Override
    @SuppressWarnings("ConstantConditions")
    public String getFullHumanReadableString() {
        javax.lang.model.element.TypeElement el = getModelElement();
        //see getModelElement() for why we do the null check here even if getModelElement() is @Nonnull
        return getHumanReadableElementType() + " " + (el == null ? canonicalName : Util.toHumanReadableString(el));
    }

    @Override
    protected String createComparableSignature() {
        //this isn't used, because compareTo is implemented differently
        return null;
    }
}
