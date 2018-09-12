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
package org.revapi.java.matcher;

import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.simple.SimpleElement;

/**
 * This actually is not present in the "normal" model but we need it to be able to
 * capture the annotation attributes using the same logic as other elements in the model.
 *
 * @author Lukas Krejci
 */
final class AnnotationAttributeElement extends SimpleElement implements JavaElement {
    private final TypeEnvironment env;
    private final API api;
    private final Archive archive;
    private final ExecutableElement attributeMethod;
    private final AnnotationValue annotationValue;

    AnnotationAttributeElement(JavaAnnotationElement annotation, ExecutableElement attributeMethod,
                               AnnotationValue annotationValue) {
        this.env = annotation.getTypeEnvironment();
        this.api = annotation.getApi();
        this.archive = annotation.getArchive();
        this.attributeMethod = attributeMethod;
        this.annotationValue = annotationValue;
    }

    @Nonnull
    @Override
    public TypeEnvironment getTypeEnvironment() {
        return env;
    }

    @Nonnull
    @Override
    public API getApi() {
        return api;
    }

    @Nullable
    @Override
    public Archive getArchive() {
        return archive;
    }

    @Override
    public int compareTo(Element o) {
        if (!(o instanceof AnnotationAttributeElement)) {
            return MatchElementsAwareComparator.compareByType(this, o);
        }
        return attributeMethod.getSimpleName().toString()
                .compareTo(((AnnotationAttributeElement) o).attributeMethod.getSimpleName().toString());
    }

    public ExecutableElement getAttributeMethod() {
        return attributeMethod;
    }

    public AnnotationValue getAnnotationValue() {
        return annotationValue;
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public SortedSet<? extends JavaElement> getChildren() {
        return (SortedSet<? extends JavaElement>) super.getChildren();
    }
}
