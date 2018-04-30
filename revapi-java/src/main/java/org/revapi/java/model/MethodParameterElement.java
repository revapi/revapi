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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaMethodParameterElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MethodParameterElement extends JavaElementBase<VariableElement, TypeMirror> implements
    JavaMethodParameterElement {

    private final int index;

    public MethodParameterElement(ProbingEnvironment env, Archive archive, VariableElement element, TypeMirror type) {
        super(env, archive, element, type);
        if (element.getEnclosingElement() instanceof ExecutableElement) {
            index = ((ExecutableElement) element.getEnclosingElement()).getParameters().indexOf(element);
        } else {
            throw new IllegalArgumentException(
                "MethodParameterElement cannot be constructed using a VariableElement not representing a method parameter.");
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    @Override
    public JavaMethodElement getParent() {
        return (JavaMethodElement) super.getParent();
    }

    @Override public int getIndex() {
        return index;
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "parameter";
    }

    @Override
    public int compareTo(@Nonnull Element o) {
        if (!(o.getClass().equals(MethodParameterElement.class))) {
            return JavaElementFactory.compareByType(this, o);
        }

        MethodParameterElement other = (MethodParameterElement) o;
        return index - other.index;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (obj == this) {
            return true;
        }

        if (!(obj instanceof MethodParameterElement)) {
            return false;
        }

        MethodParameterElement other = (MethodParameterElement) obj;

        ExecutableElement myMethodElement = (ExecutableElement) getDeclaringElement().getEnclosingElement();
        ExecutableElement otherMethodElement = (ExecutableElement) other.getDeclaringElement().getEnclosingElement();

        if (myMethodElement.getParameters().size() != otherMethodElement.getParameters().size()) {
            return false;
        }

        String mySig = getComparableSignature();
        String otherSig = other.getComparableSignature();

        return mySig.equals(otherSig) && index == other.index;
    }

    @Override
    protected String createComparableSignature() {
        //not used
        return null;
    }

    @Override
    public MethodParameterElement clone() {
        return (MethodParameterElement) super.clone();
    }
}
