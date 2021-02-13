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
package org.revapi.java.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class JavaElementBase<E extends Element, T extends TypeMirror> extends AbstractJavaElement
        implements JavaModelElement {

    protected final E element;
    protected final T representation;
    private String comparableSignature;
    private boolean inherited = false;
    private String stringRepre;
    private Map<Class<?>, Map<String, Object>> childrenByTypeAndSignature;

    JavaElementBase(ProbingEnvironment env, Archive archive, E element, T representation) {
        super(env);
        this.element = element;
        setArchive(archive);
        this.representation = representation;
    }

    @Nonnull
    protected abstract String getHumanReadableElementType();

    @Nullable
    @Override
    public JavaModelElement getParent() {
        return (JavaModelElement) super.getParent();
    }

    @Override
    public void setParent(@Nullable JavaElement parent) {
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

    @Override
    public int compareTo(@Nonnull JavaElement o) {
        if (getClass() != o.getClass()) {
            return JavaElementFactory.compareByType(this, o);
        }

        return getComparableSignature().compareTo(((JavaElementBase<?, ?>) o).getComparableSignature());
    }

    public E getDeclaringElement() {
        return element;
    }

    @Override
    public T getModelRepresentation() {
        return representation;
    }

    @Override
    public boolean isInherited() {
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
            JavaTypeElement parentType = getParentType();

            if (parentType != null) {
                decl += " @ " + Util.toHumanReadableString(parentType.getDeclaringElement());
            }
        }
        return getHumanReadableElementType() + " " + decl;
    }

    @Override
    public int hashCode() {
        return getDeclaringElement().hashCode() * getModelRepresentation().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        return obj instanceof JavaElementBase &&
                getFullHumanReadableString().equals(((JavaElementBase<?, ?>) obj).getFullHumanReadableString());
    }

    @Override
    public String toString() {
        return getFullHumanReadableString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public JavaElementBase<E, T> clone() {
        return (JavaElementBase<E, T>) super.clone();
    }

    /**
     * Clones this element and tries to add it under the new parent. If the parent already contains an element
     * equivalent to this one, the returned optional is empty, otherwise it contains the clone.
     *
     * @param newParent the parent to add the clone to
     * @return optional with the clone or an empty optional if the new parent already contains an equivalent element
     */
    public Optional<JavaElementBase<E, T>> cloneUnder(JavaElementBase<?, ?> newParent) {
        JavaElementBase<E, T> copy = clone();
        if (newParent.getChildren().add(copy)) {
            return Optional.of(copy);
        } else {
            return Optional.empty();
        }
    }

    @Nullable
    public <X extends JavaElementBase<?, ?>> X lookupChildElement(Class<X> childType, String comparableSignature) {
        if (!environment.isScanningComplete()) {
            return null;
        }

        if (childrenByTypeAndSignature == null) {
            childrenByTypeAndSignature = buildChildrenByTypeAndSignature();
        }

        Map<String, Object> children = childrenByTypeAndSignature.get(childType);
        if (children == null) {
            return null;
        }

        return childType.cast(children.get(comparableSignature));
    }

    protected String getComparableSignature() {
        if (comparableSignature == null) {
            comparableSignature = createComparableSignature();
        }

        return comparableSignature;
    }

    protected abstract String createComparableSignature();

    private Map<Class<?>, Map<String, Object>> buildChildrenByTypeAndSignature() {
        Map<Class<?>, Map<String, Object>> ret = new HashMap<>();

        for (JavaElement el : getChildren()) {
            if (!(el instanceof JavaElementBase)) {
                continue;
            }

            Class<?> type = el.getClass();
            String sig = ((JavaElementBase<?, ?>) el).getComparableSignature();

            ret.computeIfAbsent(type, __ -> new HashMap<>()).put(sig, el);
        }
        return ret;
    }
}
