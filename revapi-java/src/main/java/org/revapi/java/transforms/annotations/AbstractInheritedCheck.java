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

package org.revapi.java.transforms.annotations;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor7;

import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.MatchReport;
import org.revapi.ProblemTransform;
import org.revapi.java.ElementPairVisitor;
import org.revapi.java.JavaModelElement;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class AbstractInheritedCheck implements ProblemTransform {
    protected Configuration configuration;

    @Override
    public void initialize(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public MatchReport.Problem transform(final Element oldElement, final Element newElement,
        final MatchReport.Problem problem) {
        if (Code.fromCode(problem.code) != getCodeToTransform()) {
            return problem;
        }

        AnnotationMirror removedAnnotation = (AnnotationMirror) problem.attachments.get(0);

        return removedAnnotation.getAnnotationType().asElement()
            .accept(new SimpleElementVisitor7<MatchReport.Problem, Void>() {
                @Override
                protected MatchReport.Problem defaultAction(javax.lang.model.element.Element e, Void ignored) {
                    return problem;
                }

                @Override
                public MatchReport.Problem visitType(TypeElement e, Void ignored) {
                    if (!"java.lang.annotation.Inherited".equals(e.getQualifiedName().toString())) {
                        return problem;
                    }

                    JavaModelElement oldE = (JavaModelElement) oldElement;
                    JavaModelElement newE = (JavaModelElement) newElement;

                    return oldE.getModelElement().accept(new ElementPairVisitor<MatchReport.Problem>() {
                        @Override
                        protected MatchReport.Problem unmatchedAction(javax.lang.model.element.Element element,
                            javax.lang.model.element.Element otherElement) {
                            return problem;
                        }

                        @Override
                        protected MatchReport.Problem visitType(TypeElement oldElement, TypeElement newElement) {
                            if (oldElement.getKind() != ElementKind.ANNOTATION_TYPE ||
                                newElement.getKind() != ElementKind.ANNOTATION_TYPE) {

                                return problem;
                            } else {
                                return createProblem();
                            }
                        }
                    }, newE.getModelElement());
                }
            }, null);
    }

    protected abstract MatchReport.Problem createProblem();

    protected abstract Code getCodeToTransform();
}
