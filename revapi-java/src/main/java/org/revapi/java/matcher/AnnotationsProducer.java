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
package org.revapi.java.matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.TypeMirror;

import org.revapi.Element;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.TypeEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
final class AnnotationsProducer implements ChoiceProducer {
    private final boolean onlyDirect;

    AnnotationsProducer(boolean onlyDirect) {
        this.onlyDirect = onlyDirect;
    }

    @Override
    public Stream<? extends JavaElement> choiceFor(JavaElement element) {
        if (onlyDirect) {
            return element.stream(JavaAnnotationElement.class, false);
        } else {
            ArrayList<JavaAnnotationElement> ret = new ArrayList<>();
            fillAlsoWithInheritedAnnotations(element, ret, element.getTypeEnvironment());
            return ret.stream();
        }
    }

    private void fillAlsoWithInheritedAnnotations(Element current, List<JavaAnnotationElement> els,
                                                  TypeEnvironment typeEnv) {
        current.searchChildren(els, JavaAnnotationElement.class, false, null);

        Function<JavaTypeElement, JavaTypeElement> getSuperClass = type -> {
            TypeMirror superClass = type.getDeclaringElement().getSuperclass();
            return typeEnv.getModelElement(superClass);
        };

        //annotation inheritance is only applied to classes
        if (current instanceof JavaTypeElement) {
            JavaTypeElement type = getSuperClass.apply((JavaTypeElement) current);
            while (type != null) {
                List<JavaAnnotationElement> annos = type.searchChildren(JavaAnnotationElement.class, false, null);
                els.addAll(annos);

                for (JavaAnnotationElement a : annos) {
                    if (isInherited(a.getAnnotation())) {
                        els.add(a);
                    }
                }

                TypeMirror superClass = type.getDeclaringElement().getSuperclass();

                type = typeEnv.getModelElement(superClass);
            }
        }
    }

    private boolean isInherited(AnnotationMirror a) {
        for (AnnotationMirror metaAnno : a.getAnnotationType().getAnnotationMirrors()) {
            if ("@java.lang.annotation.Inherited".equals(Util.toHumanReadableString(metaAnno))) {
                return true;
            }
        }

        return false;
    }
}
