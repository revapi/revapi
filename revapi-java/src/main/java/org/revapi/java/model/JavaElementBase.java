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
package org.revapi.java.model;

import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class JavaElementBase<E extends Element, T extends TypeMirror> extends SimpleElement
        implements JavaModelElement {

    protected final ProbingEnvironment environment;
    protected final E element;
    protected final T representation;
    private final Archive archive;
    private String comparableSignature;
    private boolean inherited = false;
    private String stringRepre;

    JavaElementBase(ProbingEnvironment env, Archive archive, E element, T representation) {
        this.environment = env;
        this.element = element;
        this.archive = archive;
        this.representation = representation;
    }

    @Nonnull
    protected abstract String getHumanReadableElementType();

    @Nullable @Override public JavaModelElement getParent() {
        return (JavaModelElement) super.getParent();
    }

    @Override public void setParent(@Nullable org.revapi.Element parent) {
        if (parent != null && !(parent instanceof JavaModelElement)) {
            throw new IllegalArgumentException("A parent must be a java model element.");
        }
        super.setParent(parent);
    }

    @Nonnull
    @Override
    public API getApi() {
        return environment.getApi();
    }

    @Nullable
    @Override
    public Archive getArchive() {
        return archive;
    }

    @Override
    public int compareTo(@Nonnull org.revapi.Element o) {
        if (getClass() != o.getClass()) {
            return JavaElementFactory.compareByType(this, o);
        }

        return getComparableSignature().compareTo(((JavaElementBase<?, ?>) o).getComparableSignature());
    }

    @Nonnull
    @Override
    public TypeEnvironment getTypeEnvironment() {
        return environment;
    }

    public E getDeclaringElement() {
        return element;
    }

    @Override public T getModelRepresentation() {
        return representation;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public SortedSet<JavaElement> getChildren() {
        return (SortedSet<JavaElement>) super.getChildren();
    }

    @Override public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    @Nonnull
    @Override
    public final String getFullHumanReadableString() {
        if (environment.isScanningComplete() && stringRepre != null) {
            return stringRepre;
        }

        String ret = createFullHumanReadableString();

        if (environment.isScanningComplete()) {
            stringRepre = ret;
        }

        return ret;
    }

    protected String createFullHumanReadableString() {
        String decl = Util.toHumanReadableString(getDeclaringElement());
        if (isInherited()) {
            org.revapi.Element parent = getParent();
            while (parent != null && !(parent instanceof JavaTypeElement)) {
                parent = parent.getParent();
            }
            JavaTypeElement parentType = (JavaTypeElement) parent;

            if (parentType != null) {
                decl += " @ " + Util.toHumanReadableString(parentType.getDeclaringElement());
            }
        }
        return getHumanReadableElementType() + " " + decl;
    }

    @Override
    public int hashCode() {
        return getFullHumanReadableString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        return obj != null && obj instanceof JavaElementBase &&
            getFullHumanReadableString().equals(((JavaElementBase<?, ?>) obj).getFullHumanReadableString());
    }

    @Override
    public String toString() {
        return getFullHumanReadableString();
    }

    protected String getComparableSignature() {
        if (comparableSignature == null) {
            comparableSignature = createComparableSignature();
        }

        return comparableSignature;
    }

    protected abstract String createComparableSignature();
}
