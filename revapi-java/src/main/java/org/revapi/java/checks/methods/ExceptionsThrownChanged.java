/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi.java.checks.methods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleElementVisitor7;
import javax.lang.model.util.SimpleTypeVisitor7;

import org.revapi.CoIterator;
import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 *
 * @since 0.3.0
 */
public class ExceptionsThrownChanged extends CheckBase {

    private static final SimpleTypeVisitor7<TypeElement, Void> CONVERT_TO_ELEMENT = new SimpleTypeVisitor7<TypeElement, Void>() {
        @Override
        public TypeElement visitDeclared(DeclaredType t, Void ignored) {
            return t.asElement().accept(new SimpleElementVisitor7<TypeElement, Void>() {
                @Override
                public TypeElement visitType(TypeElement e, Void aVoid) {
                    return e;
                }
            }, null);
        }
    };

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        if (!isBothAccessible(oldMethod, newMethod)) {
            return;
        }

        List<? extends TypeMirror> oldExceptions = oldMethod.getModelRepresentation().getThrownTypes();
        List<? extends TypeMirror> newExceptions = newMethod.getModelRepresentation().getThrownTypes();

        Set<String> oldExceptionClassNames = oldExceptions.isEmpty() ? Collections.emptySet()
                : oldExceptions.stream().map(Util::toUniqueString).collect(Collectors.toSet());

        Set<String> newExceptionClassNames = newExceptions.isEmpty() ? Collections.emptySet()
                : newExceptions.stream().map(Util::toUniqueString).collect(Collectors.toSet());

        if (!(oldExceptions.isEmpty() && newExceptions.isEmpty())
                && !oldExceptionClassNames.equals(newExceptionClassNames)) {
            pushActive(oldMethod, newMethod);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaMethodElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        List<? extends TypeMirror> oldExceptions = new ArrayList<>(
                methods.oldElement.getModelRepresentation().getThrownTypes());
        List<? extends TypeMirror> newExceptions = new ArrayList<>(
                methods.newElement.getModelRepresentation().getThrownTypes());

        Comparator<TypeMirror> byClassName = Comparator.comparing(Util::toUniqueString);

        Collections.sort(oldExceptions, byClassName);
        Collections.sort(newExceptions, byClassName);

        CoIterator<TypeMirror> it = new CoIterator<>(oldExceptions.iterator(), newExceptions.iterator(), byClassName);

        List<String> removedRuntimeExceptions = new ArrayList<>();
        List<String> addedRuntimeExceptions = new ArrayList<>();
        List<String> removedCheckedExceptions = new ArrayList<>();
        List<String> addedCheckedExceptions = new ArrayList<>();
        boolean reportSomething = false;

        while (it.hasNext()) {
            it.next();
            TypeMirror oldType = it.getLeft();
            TypeMirror newType = it.getRight();

            if (oldType != null && newType != null) {
                // they match, so move on, nothing to report here
                continue;
            }

            reportSomething = true;

            TypeElement oldException = oldType == null ? null : oldType.accept(CONVERT_TO_ELEMENT, null);
            TypeElement newException = newType == null ? null : newType.accept(CONVERT_TO_ELEMENT, null);

            if (oldException != null) {
                if (isRuntimeException(oldException)) {
                    removedRuntimeExceptions.add(oldException.getQualifiedName().toString());
                } else {
                    removedCheckedExceptions.add(oldException.getQualifiedName().toString());
                }
            } else if (newException != null) {
                if (isRuntimeException(newException)) {
                    addedRuntimeExceptions.add(newException.getQualifiedName().toString());
                } else {
                    addedCheckedExceptions.add(newException.getQualifiedName().toString());
                }
            }
        }

        if (!reportSomething) {
            return null;
        }

        List<Difference> ret = new ArrayList<>();

        if (!removedRuntimeExceptions.isEmpty()) {
            removedRuntimeExceptions.forEach(ex -> ret.add(createDifference(Code.METHOD_RUNTIME_EXCEPTION_REMOVED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement, "exception", ex))));
        }

        if (!addedRuntimeExceptions.isEmpty()) {
            addedRuntimeExceptions.forEach(ex -> ret.add(createDifference(Code.METHOD_RUNTIME_EXCEPTION_ADDED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement, "exception", ex))));
        }

        if (!addedCheckedExceptions.isEmpty()) {
            addedCheckedExceptions.forEach(ex -> ret.add(createDifference(Code.METHOD_CHECKED_EXCEPTION_ADDED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement, "exception", ex))));
        }

        if (!removedCheckedExceptions.isEmpty()) {
            removedCheckedExceptions.forEach(ex -> ret.add(createDifference(Code.METHOD_CHECKED_EXCEPTION_REMOVED,
                    Code.attachmentsFor(methods.oldElement, methods.newElement, "exception", ex))));
        }

        return ret;
    }

    private boolean isRuntimeException(TypeElement exception) {
        // noinspection LoopStatementThatDoesntLoop
        while (exception != null) {
            Name fqn = exception.getQualifiedName();

            if (fqn.contentEquals("java.lang.RuntimeException")) {
                return true;
            }

            if (fqn.contentEquals("java.lang.Error")) {
                return true;
            }

            if (fqn.contentEquals("java.lang.Exception")) {
                return false;
            }

            if (fqn.contentEquals("java.lang.Throwable")) {
                return false;
            }

            exception = exception.getSuperclass().accept(CONVERT_TO_ELEMENT, null);
        }

        return false;
    }
}
