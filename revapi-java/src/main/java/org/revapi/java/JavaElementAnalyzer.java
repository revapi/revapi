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
import java.util.List;
import java.util.ServiceLoader;

import org.revapi.Configuration;
import org.revapi.Element;
import org.revapi.ElementAnalyzer;
import org.revapi.MatchReport;
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
    private final TypeEnvironment oldClasses;
    private final TypeEnvironment newClasses;
    private final Configuration configuration;
    private List<MatchReport.Problem> lastAnnotationResults;

    public JavaElementAnalyzer(Configuration configuration, TypeEnvironment oldClasses, TypeEnvironment newClasses) {
        this(configuration, oldClasses, newClasses,
            ServiceLoader.load(Check.class, JavaElementAnalyzer.class.getClassLoader()));
    }

    public JavaElementAnalyzer(Configuration configuration, TypeEnvironment oldClasses, TypeEnvironment newClasses,
        Iterable<Check> checks) {
        this.configuration = configuration;
        this.oldClasses = oldClasses;
        this.newClasses = newClasses;
        this.checks = checks;
        for (Check c : checks) {
            c.initialize(configuration);
            c.setOldTypeEnvironment(oldClasses);
            c.setNewTypeEnvironment(newClasses);
        }
    }

    @Override
    public void beginAnalysis(Element oldElement, Element newElement) {
        if (oldElement instanceof TypeElement && newElement instanceof TypeElement) {
            for (Check c : checks) {
                c.visitClass(((TypeElement) oldElement).getModelElement(),
                    ((TypeElement) newElement).getModelElement());
            }
        } else if (oldElement instanceof AnnotationElement && newElement instanceof AnnotationElement) {
            //annotation are always terminal elements, so treat them a bit differently
            if (lastAnnotationResults == null) {
                lastAnnotationResults = new ArrayList<>();
            }
            lastAnnotationResults.clear();
            for (Check c : checks) {
                List<MatchReport.Problem> cps = c.visitAnnotation(((AnnotationElement) oldElement).getAnnotation(),
                    ((AnnotationElement) newElement).getAnnotation());
                lastAnnotationResults.addAll(cps);
            }
        } else if (oldElement instanceof FieldElement && newElement instanceof FieldElement) {
            for (Check c : checks) {
                c.visitField(((FieldElement) oldElement).getModelElement(),
                    ((FieldElement) newElement).getModelElement());
            }
        } else if (oldElement instanceof MethodElement && newElement instanceof MethodElement) {
            for (Check c : checks) {
                c.visitMethod(((MethodElement) oldElement).getModelElement(),
                    ((MethodElement) newElement).getModelElement());
            }
        } else if (oldElement instanceof MethodParameterElement && newElement instanceof MethodParameterElement) {
            for (Check c : checks) {
                c.visitMethodParameter(((MethodParameterElement) oldElement).getModelElement(),
                    ((MethodParameterElement) newElement).getModelElement());
            }
        }
    }

    @Override
    public MatchReport endAnalysis(Element oldElement, Element newElement) {
        if ((oldElement == null || oldElement instanceof AnnotationElement) &&
            (newElement == null || newElement instanceof AnnotationElement)) {
            return new MatchReport(lastAnnotationResults, oldElement, newElement);
        }

        List<MatchReport.Problem> problems = new ArrayList<>();
        for (Check c : checks) {
            List<MatchReport.Problem> p = c.visitEnd();
            if (p != null) {
                problems.addAll(p);
            }
        }

        return new MatchReport(problems, oldElement, newElement);
    }
}
