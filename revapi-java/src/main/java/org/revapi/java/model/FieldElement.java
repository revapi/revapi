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
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.Archive;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaFieldElement;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class FieldElement extends JavaElementBase<VariableElement, TypeMirror> implements JavaFieldElement {

    public FieldElement(ProbingEnvironment env, Archive archive, VariableElement element, TypeMirror type) {
        super(env, archive, element, type);
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull @Override public JavaTypeElement getParent() {
        return (JavaTypeElement) super.getParent();
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "field";
    }

    @Override
    protected String createComparableSignature() {
        return getDeclaringElement().getSimpleName().toString();
    }

    @Override
    public FieldElement clone() {
        return (FieldElement) super.clone();
    }
}
