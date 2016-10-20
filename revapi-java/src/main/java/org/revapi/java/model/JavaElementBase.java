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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

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
    private boolean initializedChildren;
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
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public SortedSet<JavaElement> getChildren() {
        if (!initializedChildren && environment.isScanningComplete()) {
            SortedSet<JavaElement> set = (SortedSet<JavaElement>) super.getChildren();

            DeclaredType currentType = findContainingType().getModelRepresentation();
            Element currentElement =  getDeclaringElement();
            Types types = environment.getTypeUtils();

            BiFunction<Element, Boolean, JavaElementBase<?, ?>> processElement = (e, includePrivate) -> {
                if (!includePrivate
                        && Collections.disjoint(e.getModifiers(), Arrays.asList(Modifier.PUBLIC, Modifier.PROTECTED))) {
                    return null;
                }

                TypeMirror t = types.asMemberOf(currentType, e);

                JavaElementBase<?, ?> child = JavaElementFactory.elementFor(e, t, environment, archive);
                if (child != null) {
                    if (set.add(child)) {
                        child.setParent(this);
                    }
                }

                return child;
            };

            for (Element e : currentElement.getEnclosedElements()) {
                //leave out types - those have been handled by the classpath scanner and also can lead to nasty
                //recursions if the member classes inherit from the outer class
                if (e instanceof javax.lang.model.element.TypeElement) {
                    continue;
                }
                processElement.apply(e, true);
            }

            getSuperTypesForInheritance().forEach(t -> {
                Element e = types.asElement(t);
                if (e != null && e instanceof javax.lang.model.element.TypeElement
                        && environment.getTypeMap().containsKey(e)) {
                    for (Element child : e.getEnclosedElements()) {
                        //leave out types - those have been handled by the classpath scanner and also can lead to nasty
                        //recursions if the member classes inherit from the outer class
                        if (child instanceof javax.lang.model.element.TypeElement) {
                            continue;
                        }
                        JavaElementBase<?, ?> childE = processElement.apply(child, false);
                        if (childE != null) {
                            childE.setInherited(true);
                        }
                    }
                }
            });

            for (AnnotationMirror m : getDeclaringElement().getAnnotationMirrors()) {
                set.add(new AnnotationElement(environment, archive, m));
            }

            initializedChildren = true;
        }

        return (SortedSet<JavaElement>) super.getChildren();
    }

    @Override public boolean isInherited() {
        return inherited;
    }

    public void setInherited(boolean inherited) {
        this.inherited = inherited;
    }

    protected List<TypeMirror> getSuperTypesForInheritance() {
        return Collections.emptyList();
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

    private JavaTypeElement findContainingType() {
        org.revapi.Element ret = this;
        while (ret != null && !(ret instanceof JavaTypeElement)) {
            ret = ret.getParent();
        }

        return (JavaTypeElement) ret;
    }
}
