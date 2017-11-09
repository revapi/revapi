/*
 * Copyright 2014-2017 Lukas Krejci
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Removed extends CheckBase {

    private static String getMethodSignature(@Nonnull CharSequence methodName, @Nonnull ExecutableType erasedMethod) {
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
    protected void doVisitMethod(@Nullable JavaMethodElement oldMethod, @Nullable JavaMethodElement newMethod) {
        if (oldMethod != null && newMethod == null && isAccessible(oldMethod)) {
            pushActive(oldMethod, null);
        }
    }

    @Nullable
    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaMethodElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        return Collections.singletonList(createDifference(Code.METHOD_REMOVED,
                Code.attachmentsFor(methods.oldElement, methods.newElement)));
    }
}
