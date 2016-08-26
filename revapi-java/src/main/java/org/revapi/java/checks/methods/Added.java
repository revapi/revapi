/*
 * Copyright 2014 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi.java.checks.methods;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.SimpleElementVisitor7;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Added extends CheckBase {
    private static final Logger LOG = LoggerFactory.getLogger(Added.class);

    private final SimpleElementVisitor7<TypeElement, Void> enclosingClassExtractor = new SimpleElementVisitor7<TypeElement, Void>() {
        @Override
        protected TypeElement defaultAction(Element e, Void ignored) {
            return null;
        }

        @Override
        public TypeElement visitType(TypeElement e, Void ignored) {
            return e;
        }
    };

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(ExecutableElement oldMethod, ExecutableElement newMethod) {
        if (oldMethod == null && newMethod != null && isAccessible(newMethod, getNewTypeEnvironment())) {
            pushActive(null, newMethod);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<ExecutableElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        // we need to consider several cases here:
        // 1) method added to a interface
        // 2) method added to a final class
        // 3) concrete method added to a non-final class
        // 4) abstract method added to a non-final class
        // 5) previously inherited method is now declared in class

        ExecutableElement method = methods.newElement;

        TypeElement enclosingClass = method.getEnclosingElement().accept(enclosingClassExtractor, null);
        if (enclosingClass == null) {
            LOG.warn("Could not find an enclosing class of method " + method + ". That's weird.");
            return null;
        }

        String methodSignature = Removed.getMethodSignature(method.getSimpleName(),
                (ExecutableType) getNewTypeEnvironment().getTypeUtils().erasure(method.asType()));

        ExecutableElement previouslyInherited = null;

        TypeElement oldEnclosingClass =
                getOldTypeEnvironment().getElementUtils().getTypeElement(enclosingClass.getQualifiedName());
        if (oldEnclosingClass != null) {
            previouslyInherited =
                    Removed.getAccessiblyInheritedFromSuperTypesOf(this, methodSignature, oldEnclosingClass,
                            getOldTypeEnvironment());
        }

        Difference difference;

        if (enclosingClass.getKind() == ElementKind.INTERFACE) {
            if (method.isDefault()) {
                difference = createDifference(Code.METHOD_DEFAULT_METHOD_ADDED_TO_INTERFACE);
            } else if (method.getModifiers().contains(Modifier.STATIC)) {
                //statics on interface can only be called using the interface they are declared on, even if a method
                //with a same signature was declared on some of the super types in the old version, the users would
                //not have been able to call those methods using the current type. So we don't need to specialize here
                //based on the presence of a previously inherited method.
                difference = createDifference(Code.METHOD_STATIC_METHOD_ADDED_TO_INTERFACE);
            } else {
                if (previouslyInherited == null) {
                    difference = createDifference(Code.METHOD_ADDED_TO_INTERFACE);
                } else {
                    difference = createDifference(Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS,
                            new String[]{Util.toHumanReadableString(previouslyInherited.getEnclosingElement())},
                            previouslyInherited.getEnclosingElement());
                }
            }
        } else if (method.getModifiers().contains(Modifier.ABSTRACT)) {
            if (previouslyInherited == null) {
                difference = createDifference(Code.METHOD_ABSTRACT_METHOD_ADDED);
            } else if (previouslyInherited.getModifiers().contains(Modifier.ABSTRACT)) {
                difference = createDifference(Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS,
                        new String[]{Util.toHumanReadableString(previouslyInherited.getEnclosingElement())},
                        previouslyInherited.getEnclosingElement());
            } else {
                difference = createDifference(Code.METHOD_NOW_ABSTRACT);
            }
        } else if (method.getModifiers().contains(Modifier.FINAL) &&
            !enclosingClass.getModifiers().contains(Modifier.FINAL)) {

            if (previouslyInherited == null) {
                difference = createDifference(Code.METHOD_FINAL_METHOD_ADDED_TO_NON_FINAL_CLASS);
            } else if (previouslyInherited.getModifiers().contains(Modifier.FINAL)){
                difference = createDifference(Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS,
                        new String[]{Util.toHumanReadableString(previouslyInherited.getEnclosingElement())},
                        previouslyInherited.getEnclosingElement());
            } else {
                difference = createDifference(Code.METHOD_NOW_FINAL);
            }
        } else {
            if (previouslyInherited == null) {
                difference = createDifference(Code.METHOD_ADDED);
            } else {
                difference = createDifference(Code.METHOD_INHERITED_METHOD_MOVED_TO_CLASS,
                        new String[]{Util.toHumanReadableString(previouslyInherited.getEnclosingElement())},
                        previouslyInherited.getEnclosingElement());
            }
        }

        return Collections.singletonList(difference);
    }

}
