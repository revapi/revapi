/*
 * Copyright 2014-2023 Lukas Krejci
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
import javax.lang.model.element.AnnotationMirror;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class AnnotationElement extends AbstractJavaElement implements JavaAnnotationElement {
    private final AnnotationMirror annotation;
    private String comparableSignature;

    public AnnotationElement(ProbingEnvironment environment, Archive archive, AnnotationMirror annotation) {
        super(environment);
        this.annotation = annotation;
        setArchive(archive);
    }

    @SuppressWarnings("ConstantConditions")
    @Nonnull
    @Override
    public JavaModelElement getParent() {
        return (JavaModelElement) super.getParent();
    }

    @Nonnull
    @Override
    public API getApi() {
        return environment.getApi();
    }

    @Nonnull
    @Override
    public AnnotationMirror getAnnotation() {
        return annotation;
    }

    @Nonnull
    @Override
    public TypeEnvironment getTypeEnvironment() {
        return environment;
    }

    @Override
    public int compareTo(@Nonnull JavaElement o) {
        if (!(o instanceof AnnotationElement)) {
            return JavaElementFactory.compareByType(this, o);
        }

        return getComparableSignature().compareTo(((AnnotationElement) o).getComparableSignature());
    }

    @Override
    public @Nonnull String getFullHumanReadableString() {
        return Util.toHumanReadableString(annotation);
    }

    @Override
    public String toString() {
        return getFullHumanReadableString();
    }

    @Override
    public AnnotationElement clone() {
        return (AnnotationElement) super.clone();
    }

    private String getComparableSignature() {
        if (comparableSignature == null) {
            comparableSignature = "@" + Util.toHumanReadableString(annotation.getAnnotationType());
        }

        return comparableSignature;
    }
}
