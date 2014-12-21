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

import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class JavaElementBase<T extends Element> extends SimpleElement implements JavaModelElement {

    protected final ProbingEnvironment environment;
    protected T element;
    private boolean initializedChildren;
    private final Archive archive;
    private String comparableSignature;

    public JavaElementBase(ProbingEnvironment env, Archive archive, T element) {
        this.environment = env;
        this.element = element;
        this.archive = archive;
    }

    @Nonnull
    protected abstract String getHumanReadableElementType();

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
    public int compareTo(org.revapi.Element o) {
        if (getClass() != o.getClass()) {
            return JavaElementFactory.compareByType(this, o);
        }

        return getComparableSignature().compareTo(((JavaElementBase<?>) o).getComparableSignature());
    }

    @Nonnull
    @Override
    public TypeEnvironment getTypeEnvironment() {
        return environment;
    }

    @Nonnull
    public T getModelElement() {
        return element;
    }

    @Nonnull
    @Override
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    public SortedSet<JavaElement> getChildren() {
        if (!initializedChildren) {
            SortedSet<JavaElement> set = (SortedSet<JavaElement>) super.getChildren();

            //this actually CAN be null during probing... the @Nonnull annotation is there for library users, not for the
            //library itself
            if (getModelElement() == null) {
                //wait with the initialization until we have the model element ready
                return set;
            }

            for (Element e : getModelElement().getEnclosedElements()) {
                JavaModelElement child = JavaElementFactory.elementFor(e, environment, archive);
                if (child != null) {
                    child.setParent(this);

                    set.add(child);
                }
            }

            for (AnnotationMirror m : getModelElement().getAnnotationMirrors()) {
                set.add(new AnnotationElement(environment, archive, m));
            }

            initializedChildren = true;
        }

        return (SortedSet<JavaElement>) super.getChildren();
    }

    @Nonnull
    @Override
    public String getFullHumanReadableString() {
        return getHumanReadableElementType() + " " + Util.toHumanReadableString(getModelElement());
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
            getFullHumanReadableString().equals(((JavaElementBase<?>) obj).getFullHumanReadableString());
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
