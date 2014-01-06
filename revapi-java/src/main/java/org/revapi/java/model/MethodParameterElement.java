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

import javax.lang.model.element.VariableElement;

import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class MethodParameterElement extends JavaElementBase<VariableElement> {
    public MethodParameterElement(ProbingEnvironment env, VariableElement element) {
        super(env, element);
    }

    @Override
    public int compareTo(Element o) {
        if (!(o instanceof MethodParameterElement)) {
            return 1;
        }

        return toString().compareTo(o.toString());
    }

    @Override
    public String toString() {
        return getModelElement().getSimpleName().toString();
    }
}
