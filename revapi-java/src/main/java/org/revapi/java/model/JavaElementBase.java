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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.revapi.java.JavaElement;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
abstract class JavaElementBase<T extends Element> extends SimpleElement implements JavaElement {

    protected final ProbingEnvironment environment;
    protected T element;

    public JavaElementBase(ProbingEnvironment env, T element) {
        this.environment = env;
        this.element = element;
    }

    public T getModelElement() {
        return element;
    }

    @Override
    public String toString() {
        return getModelElement().toString();
    }

    @Override
    protected SortedSet<org.revapi.Element> newChildrenInstance() {
        SortedSet<org.revapi.Element> set = super.newChildrenInstance();

        for (Element e : getModelElement().getEnclosedElements()) {
            JavaElement child = JavaElementFactory.elementFor(e, environment);
            if (child != null) {
                child.setParent(this);

                set.add(child);
            }
        }

        for (AnnotationMirror m : getModelElement().getAnnotationMirrors()) {
            set.add(new AnnotationElement(environment, m));
        }

        return set;
    }
}
