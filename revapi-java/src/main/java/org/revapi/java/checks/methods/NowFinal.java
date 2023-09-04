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

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.Modifier;

import org.revapi.Difference;
import org.revapi.java.checks.common.ModifierChanged;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;

/**
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public final class NowFinal extends ModifierChanged {
    public NowFinal() {
        super(true, Code.METHOD_NOW_FINAL, Modifier.FINAL);
    }

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        doVisit(oldMethod, newMethod);
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaMethodElement> elements = popIfActive();
        if (elements == null) {
            return null;
        }

        // noinspection ConstantConditions
        if (elements.newElement.getParent().getDeclaringElement().getModifiers().contains(Modifier.FINAL)) {
            return Collections.singletonList(createDifference(Code.METHOD_NOW_FINAL_IN_FINAL_CLASS,
                    Code.attachmentsFor(elements.oldElement, elements.newElement, "oldModifiers",
                            stringify(elements.oldElement.getDeclaringElement().getModifiers()), "newModifiers",
                            stringify(elements.newElement.getDeclaringElement().getModifiers()))));
        } else {
            return Collections.singletonList(createDifference(Code.METHOD_NOW_FINAL,
                    Code.attachmentsFor(elements.oldElement, elements.newElement, "oldModifiers",
                            stringify(elements.oldElement.getDeclaringElement().getModifiers()), "newModifiers",
                            stringify(elements.newElement.getDeclaringElement().getModifiers()))));
        }
    }
}
