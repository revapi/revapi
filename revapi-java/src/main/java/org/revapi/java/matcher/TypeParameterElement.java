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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.API;
import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 */
final class TypeParameterElement extends SimpleElement implements JavaModelElement {
    private final ProbingEnvironment env;
    private final API api;
    private final Archive archive;
    private final TypeMirror type;
    private final String typeRepresentation;

    public TypeParameterElement(ProbingEnvironment env, API api, Archive archive, TypeMirror type) {
        this.env = env;
        this.api = api;
        this.archive = archive;
        this.type = type;
        this.typeRepresentation = Util.toHumanReadableString(type);
    }

    @Nonnull
    @Override
    public ProbingEnvironment getTypeEnvironment() {
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

    public TypeMirror getType() {
        return type;
    }

    @Override
    public int compareTo(Element o) {
        if (!(o instanceof TypeParameterElement)) {
            return MatchElementsAwareComparator.compareByType(this, o);
        }

        return typeRepresentation.compareTo(((TypeParameterElement) o).typeRepresentation);
    }

    @Override
    public TypeMirror getModelRepresentation() {
        return type;
    }

    @Override
    public javax.lang.model.element.Element getDeclaringElement() {
        return env.getTypeUtils().asElement(type);
    }

    @Nullable
    @Override
    public JavaModelElement getParent() {
        return (JavaModelElement) super.getParent();
    }

    @Override
    public boolean isInherited() {
        return false;
    }
}
