/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java.checks.methods;

import static org.revapi.java.checks.common.ModifierChanged.stringify;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;

/**
 * @author Lukas Krejci
 * @since 0.4.0
 */
public class NowDefault extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        if (!isBothAccessible(oldMethod, newMethod)) {
            return;
        }

        assert oldMethod != null;
        assert newMethod != null;

        boolean onInterfaces = oldMethod.getParent().getDeclaringElement().getKind() == ElementKind.INTERFACE
                && newMethod.getParent().getDeclaringElement().getKind() == ElementKind.INTERFACE;

        if (onInterfaces && !oldMethod.getDeclaringElement().isDefault() && newMethod.getDeclaringElement().isDefault()) {
            pushActive(oldMethod, newMethod);
        }
    }

    @Override
    protected @Nullable List<Difference> doEnd() {
        CheckBase.ActiveElements<JavaMethodElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        return Collections.singletonList(createDifference(Code.METHOD_NOW_DEFAULT,
                Code.attachmentsFor(methods.oldElement, methods.newElement,
                        "oldModifiers", stringify(methods.oldElement.getDeclaringElement().getModifiers()),
                        "newModifiers", stringify(methods.newElement.getDeclaringElement().getModifiers()))));
    }
}
