/*
 * Copyright 2015-2017 Lukas Krejci
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
 *
 */

package org.revapi.java.matcher;

import java.util.List;
import java.util.stream.Stream;

import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleTypeVisitor8;

import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class TypeParameterProducer implements ChoiceProducer {
    private final Integer concreteIndex;

    TypeParameterProducer(Integer concreteIndex) {
        this.concreteIndex = concreteIndex;
    }

    @Override
    public Stream<? extends JavaElement> choiceFor(JavaElement element) {
        if (!(element instanceof JavaModelElement)) {
            return Stream.empty();
        }

        return ((JavaModelElement) element).getModelRepresentation().accept(new SimpleTypeVisitor8<Stream<? extends JavaElement>, Void>() {
            @Override
            protected Stream<? extends JavaElement> defaultAction(TypeMirror e, Void aVoid) {
                return Stream.empty();
            }

            @Override
            public Stream<? extends JavaElement> visitDeclared(DeclaredType t, Void aVoid) {
                return streamFrom(t.getTypeArguments());
            }

            @Override
            public Stream<? extends JavaElement> visitExecutable(ExecutableType t, Void aVoid) {
                return streamFrom(t.getTypeVariables());
            }

            private Stream<? extends JavaElement> streamFrom(List<? extends TypeMirror> params) {
                if (concreteIndex != null && concreteIndex >= 0) {
                    if (params.size() > concreteIndex) {
                        return Stream.of(new TypeParameterElement((ProbingEnvironment) element.getTypeEnvironment(), element.getApi(),
                                element.getArchive(), params.get(concreteIndex)));
                    } else {
                        return Stream.empty();
                    }
                } else {
                    return params.stream().map(t -> new TypeParameterElement((ProbingEnvironment) element.getTypeEnvironment(),
                            element.getApi(), element.getArchive(), t));
                }
            }
        }, null);
    }
}
