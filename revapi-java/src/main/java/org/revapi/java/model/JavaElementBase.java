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
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.revapi.API;
import org.revapi.java.JavaElement;
import org.revapi.java.JavaModelElement;
import org.revapi.java.Util;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.simple.SimpleElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class JavaElementBase<T extends Element> extends SimpleElement implements JavaModelElement {

    protected final ProbingEnvironment environment;
    protected T element;
    private boolean initializedChildren;

    public JavaElementBase(ProbingEnvironment env, T element) {
        this.environment = env;
        this.element = element;
    }

    @Override
    public API getApi() {
        return environment.getApi();
    }

    @Override
    public Types getTypeUtils() {
        return environment.getTypeUtils();
    }

    @Override
    public Elements getElementUtils() {
        return environment.getElementUtils();
    }

    public T getModelElement() {
        return element;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SortedSet<JavaElement> getChildren() {
        if (!initializedChildren) {
            SortedSet<JavaElement> set = (SortedSet<JavaElement>) super.getChildren();

            if (getModelElement() == null) {
                //wait with the initialization until we have the model element ready
                return set;
            }

            for (Element e : getModelElement().getEnclosedElements()) {
                JavaModelElement child = JavaElementFactory.elementFor(e, environment);
                if (child != null) {
                    child.setParent(this);

                    set.add(child);
                }
            }

            for (AnnotationMirror m : getModelElement().getAnnotationMirrors()) {
                set.add(new AnnotationElement(environment, m));
            }

            initializedChildren = true;
        }

        return (SortedSet<JavaElement>) super.getChildren();
    }

    @Override
    public String toString() {
        return Util.toHumanReadableString(getModelElement());
    }
}
