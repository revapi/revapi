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

import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class MethodElement extends JavaElementBase<ExecutableElement> {

    public MethodElement(ProbingEnvironment env, ExecutableElement element) {
        super(env, element);
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "method";
    }

    @Override
    public int compareTo(@Nonnull Element o) {
        if (!(o instanceof MethodElement)) {
            return JavaElementFactory.compareByType(this, o);
        }

        return getModelElement().getSimpleName().toString()
            .compareTo(((MethodElement) o).getModelElement().getSimpleName().toString());
    }

    @Nonnull
    @Override
    protected SortedSet<Element> newChildrenInstance() {
        SortedSet<Element> ret = super.newChildrenInstance();

        for (VariableElement v : getModelElement().getParameters()) {
            MethodParameterElement p = new MethodParameterElement(environment, v);
            p.setParent(this);
            ret.add(p);
        }

        return ret;
    }
}
