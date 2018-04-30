/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java.model;

import javax.annotation.Nonnull;
import javax.lang.model.type.DeclaredType;

import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MissingClassElement extends TypeElement {

    private final MissingTypeElement element;

    public MissingClassElement(ProbingEnvironment env, String binaryName, String canonicalName) {
        super(env, null, binaryName, canonicalName);
        element = new MissingTypeElement(canonicalName);
    }

    @Override public javax.lang.model.element.TypeElement getDeclaringElement() {
        return element;
    }

    @Override public DeclaredType getModelRepresentation() {
        return (DeclaredType) element.asType();
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "missing-class";
    }

    @Override
    public int compareTo(@Nonnull Element o) {
        if (!(o instanceof MissingClassElement)) {
            return JavaElementFactory.compareByType(this, o);
        }

        return getBinaryName().compareTo(((MissingClassElement) o).getBinaryName());
    }

    @Override
    public MissingClassElement clone() {
        return (MissingClassElement) super.clone();
    }
}
