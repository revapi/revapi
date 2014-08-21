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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;

import org.revapi.Archive;
import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MethodElement extends JavaElementBase<ExecutableElement> implements JavaMethodElement {

    public MethodElement(ProbingEnvironment env, Archive archive, ExecutableElement element) {
        super(env, archive, element);
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "method";
    }

    @Override
    protected String createComparableSignature() {
        return getModelElement().getSimpleName() + ":" +
            Util.toUniqueString(getTypeEnvironment().getTypeUtils().erasure(getModelElement().asType()));
    }

    @Nonnull
    @Override
    protected SortedSet<Element> newChildrenInstance() {
        SortedSet<Element> ret = super.newChildrenInstance();

        for (VariableElement v : getModelElement().getParameters()) {
            MethodParameterElement p = new MethodParameterElement(environment, getArchive(), v);
            p.setParent(this);
            ret.add(p);
        }

        return ret;
    }
}
