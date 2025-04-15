/*
 * Copyright 2014-2025 Lukas Krejci
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import org.revapi.Difference;
import org.revapi.java.spi.CheckBase;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaMethodElement;

public final class Varargs extends CheckBase {

    @Override
    public EnumSet<Type> getInterest() {
        return EnumSet.of(Type.METHOD);
    }

    @Override
    protected void doVisitMethod(JavaMethodElement oldMethod, JavaMethodElement newMethod) {
        if (newMethod != null && newMethod.getDeclaringElement().isVarArgs()) {
            pushActive(oldMethod, newMethod);
        }
    }

    @Override
    protected List<Difference> doEnd() {
        ActiveElements<JavaMethodElement> methods = popIfActive();
        if (methods == null) {
            return null;
        }

        JavaMethodElement method = methods.newElement;
        List<? extends TypeMirror> methodParams = method.getModelRepresentation().getParameterTypes();

        Types types = method.getTypeEnvironment().getTypeUtils();

        List<JavaMethodElement> methodsDifferingOnlyInVarargType = getOverloads(method).filter(m -> {
            if (!m.getDeclaringElement().isVarArgs()) {
                return false;
            }

            List<? extends TypeMirror> thisParams = m.getModelRepresentation().getParameterTypes();
            if (thisParams.size() != methodParams.size()) {
                return false;
            }

            for (int i = 0; i < methodParams.size() - 1; ++i) {
                if (!types.isSameType(methodParams.get(i), thisParams.get(i))) {
                    return false;
                }
            }

            return !types.isSameType(methodParams.get(methodParams.size() - 1), thisParams.get(thisParams.size() - 1));
        }).collect(Collectors.toList());

        if (!methodsDifferingOnlyInVarargType.isEmpty()) {
            return Collections.singletonList(createDifferenceWithExplicitParams(
                    Code.METHOD_VARARG_OVERLOADS_ONLY_DIFFER_IN_VARARG_PARAMETER,
                    Code.attachmentsFor(methods.oldElement, method), methodsDifferingOnlyInVarargType.toString()));
        }

        return null;
    }

    private Stream<JavaMethodElement> getOverloads(JavaMethodElement method) {
        return method.getParent().stream(JavaMethodElement.class, false).filter(m -> m.getDeclaringElement()
                .getSimpleName().toString().equals(method.getDeclaringElement().getSimpleName().toString()));
    }
}
