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

import java.util.List;
import java.util.SortedSet;

import javax.annotation.Nonnull;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MethodElement extends JavaElementBase<ExecutableElement, ExecutableType> implements JavaMethodElement {

    public MethodElement(ProbingEnvironment env, Archive archive, ExecutableElement element, ExecutableType type) {
        super(env, archive, element, type);
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "method";
    }

    @Override
    protected String createComparableSignature() {
        //the choice of '#' for a separator between the name and signature is because it precedes both '(' and any
        //legal character in a method name in the ASCII table
        return getDeclaringElement().getSimpleName() + "#" +
            Util.toUniqueString(getModelRepresentation());
    }

    @Nonnull
    @Override
    protected SortedSet<Element> newChildrenInstance() {
        SortedSet<Element> ret = super.newChildrenInstance();

        List<? extends TypeMirror> pts = getModelRepresentation ().getParameterTypes();
        List<? extends VariableElement> pes = getDeclaringElement().getParameters();

        for (int i = 0; i < pts.size(); ++i) {
            TypeMirror t = pts.get(i);
            VariableElement e = pes.get(i);

            MethodParameterElement p = new MethodParameterElement(environment, getArchive(), e, t);
            p.setParent(this);
            ret.add(p);
        }

        return ret;
    }
}
