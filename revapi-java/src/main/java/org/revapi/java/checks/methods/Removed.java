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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Removed extends CheckBase {

    static ExecutableElement getAccessiblyInheritedFromSuperTypesOf(CheckBase self, String methodSignature,
                                                                            TypeElement type, TypeEnvironment env) {
        return Util.getAllSuperTypes(env.getTypeUtils(), type.asType()).stream()
                .map(t -> ((DeclaredType) t).asElement())
                .flatMap(e -> ElementFilter.methodsIn(e.getEnclosedElements()).stream())
                .filter(m -> {
                    if (!self.isAccessible(m, env)) {
                        return false;
                    }

                    String oldSig = getMethodSignature(m.getSimpleName(),
                            (ExecutableType) env.getTypeUtils().erasure(m.asType()));

                    return methodSignature.equals(oldSig);
                })
                .findFirst().orElse(null);
    }

    static String getMethodSignature(@Nonnull CharSequence methodName, @Nonnull ExecutableType erasedMethod) {
        StringBuilder bld = new StringBuilder(methodName);

        bld.append("(");
        for (TypeMirror p : erasedMethod.getParameterTypes()) {
            bld.append(Util.toUniqueString(p)).append(";");
        }
        bld.append(")");

        bld.append(Util.toUniqueString(erasedMethod.getReturnType()));

        return bld.toString();
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable ExecutableElement oldMethod, @Nullable ExecutableElement newMethod) {
        if (oldMethod != null && newMethod == null && isAccessible(oldMethod, getOldTypeEnvironment())) {
            pushActive(oldMethod, null);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<ExecutableElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        Difference difference = null;

        //try to find the removed method in some of the superclasses in the new environment
        ExecutableElement oldMethod = methods.oldElement;
        TypeElement type = (TypeElement) oldMethod.getEnclosingElement();
        boolean oldMethodAbstract = oldMethod.getModifiers().contains(Modifier.ABSTRACT);
        boolean oldMethodFinal = oldMethod.getModifiers().contains(Modifier.FINAL);

        String methodSignature = getMethodSignature(oldMethod.getSimpleName(),
            (ExecutableType) getOldTypeEnvironment().getTypeUtils().erasure(
                oldMethod.asType())
        );

        //find the type in the new environment
        TypeElement newType = getNewTypeEnvironment().getElementUtils().getTypeElement(type.getQualifiedName());
        if (newType == null) {
            throw new IllegalStateException(
                "Failed to find the type " + type.getQualifiedName() + " in the new version" +
                    " of API even though a method from it has been detected as removed."
            );
        }

        List<TypeMirror> superClasses = Util
            .getAllSuperTypes(getNewTypeEnvironment().getTypeUtils(), newType.asType());

        outer:
        for (TypeMirror superClass : superClasses) {
            Element superClassEl = ((DeclaredType) superClass).asElement();
            List<? extends ExecutableElement> superMethods = ElementFilter
                .methodsIn(superClassEl.getEnclosedElements());

            for (ExecutableElement superMethod : superMethods) {
                String superMethodSignature = getMethodSignature(superMethod.getSimpleName(),
                    (ExecutableType) getNewTypeEnvironment().getTypeUtils().erasure(superMethod.asType()));

                if (superMethodSignature.equals(methodSignature)) {
                    //ok, we got the method somewhere up in the superclasses

                    //has it changed to abstract?
                    if (!oldMethodAbstract && superMethod.getModifiers().contains(Modifier.ABSTRACT)) {
                        difference = createDifference(Code.METHOD_REPLACED_BY_ABSTRACT_METHOD_IN_SUPERCLASS,
                            new String[]{Util.toHumanReadableString(superClass)}, superMethod);
                    } else if (!oldMethodFinal && superMethod.getModifiers().contains(Modifier.FINAL)) {
                        difference = createDifference(Code.METHOD_NON_FINAL_METHOD_REPLACED_BY_FINAL_IN_SUPERCLASS,
                            new String[]{Util.toHumanReadableString(superClass)}, superMethod);
                    } else {
                        difference = createDifference(Code.METHOD_MOVED_TO_SUPERCLASS,
                                new String[]{Util.toHumanReadableString(superClass)}, superMethod);
                    }

                    break outer;
                }
            }
        }

        if (difference == null) {
            difference = createDifference(Code.METHOD_REMOVED);
        }

        return Collections.singletonList(difference);
    }
}
