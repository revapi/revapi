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

package org.revapi.java;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.ElementAnalyzer;
import org.revapi.MatchReport;
import org.revapi.java.compilation.CompilationValve;
import org.revapi.java.model.AnnotationElement;
import org.revapi.java.model.FieldElement;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class JavaElementAnalyzer implements ElementAnalyzer {
    private final Iterable<Check> checks;
    private final CompilationValve oldCompilationValve;
    private final CompilationValve newCompilationValve;

    // NOTE: this doesn't have to be a stack of lists only because of the fact that annotations
    // are always sorted as last amongst sibling model elements.
    // So, when reported for their parent element, we can be sure that there are no more children
    // coming for given parent.
    private List<MatchReport.Problem> lastAnnotationResults;

    public JavaElementAnalyzer(Configuration configuration, TypeEnvironment oldClasses, CompilationValve oldValve,
        TypeEnvironment newClasses, CompilationValve newValve) {
        this(configuration, oldClasses, oldValve, newClasses, newValve,
            ServiceLoader.load(Check.class, JavaElementAnalyzer.class.getClassLoader()));
    }

    public JavaElementAnalyzer(Configuration configuration, TypeEnvironment oldClasses, CompilationValve oldValve,
        TypeEnvironment newClasses, CompilationValve newValve, Iterable<Check> checks) {
        this.oldCompilationValve = oldValve;
        this.newCompilationValve = newValve;
        this.checks = checks;
        for (Check c : checks) {
            c.initialize(configuration);
            c.setOldTypeEnvironment(oldClasses);
            c.setNewTypeEnvironment(newClasses);
        }
    }

    @Override
    public void setup() {
    }

    @Override
    public void tearDown() {
        oldCompilationValve.removeCompiledResults();
        newCompilationValve.removeCompiledResults();
    }

    @Override
    public void beginAnalysis(Element oldElement, Element newElement) {
        if (conforms(oldElement, newElement, TypeElement.class)) {
            for (Check c : checks) {
                c.visitClass(oldElement == null ? null : ((TypeElement) oldElement).getModelElement(),
                    newElement == null ? null : ((TypeElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, AnnotationElement.class)) {
            // annotation are always terminal elements and they also always sort as last elements amongst siblings, so
            // treat them a bit differently
            if (lastAnnotationResults == null) {
                lastAnnotationResults = new ArrayList<>();
            }
            for (Check c : checks) {
                List<MatchReport.Problem> cps = c
                    .visitAnnotation(oldElement == null ? null : ((AnnotationElement) oldElement).getAnnotation(),
                        newElement == null ? null : ((AnnotationElement) newElement).getAnnotation());
                if (cps != null) {
                    lastAnnotationResults.addAll(cps);
                }
            }
        } else if (conforms(oldElement, newElement, FieldElement.class)) {
            for (Check c : checks) {
                c.visitField(oldElement == null ? null : ((FieldElement) oldElement).getModelElement(),
                    newElement == null ? null : ((FieldElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, MethodElement.class)) {
            for (Check c : checks) {
                c.visitMethod(oldElement == null ? null : ((MethodElement) oldElement).getModelElement(),
                    newElement == null ? null : ((MethodElement) newElement).getModelElement());
            }
        } else if (conforms(oldElement, newElement, MethodParameterElement.class)) {
            for (Check c : checks) {
                c.visitMethodParameter(
                    oldElement == null ? null : ((MethodParameterElement) oldElement).getModelElement(),
                    newElement == null ? null : ((MethodParameterElement) newElement).getModelElement());
            }
        }
    }

    @Override
    public MatchReport endAnalysis(Element oldElement, Element newElement) {
        if ((oldElement == null || oldElement instanceof AnnotationElement) &&
            (newElement == null || newElement instanceof AnnotationElement)) {

            //the annotations are always reported at the parent element
            return new MatchReport(Collections.<MatchReport.Problem>emptyList(), oldElement, newElement);
        }

        List<MatchReport.Problem> problems = new ArrayList<>();
        for (Check c : checks) {
            List<MatchReport.Problem> p = c.visitEnd();
            if (p != null) {
                problems.addAll(p);
            }
        }

        if (lastAnnotationResults != null && !lastAnnotationResults.isEmpty()) {
            problems.addAll(lastAnnotationResults);
            lastAnnotationResults.clear();
        }

        return new MatchReport(problems, oldElement, newElement);
    }

    private <T> boolean conforms(Object a, Object b, Class<T> cls) {
        boolean ca = a == null || cls.isAssignableFrom(a.getClass());
        boolean cb = b == null || cls.isAssignableFrom(b.getClass());

        return ca && cb;
    }
}
