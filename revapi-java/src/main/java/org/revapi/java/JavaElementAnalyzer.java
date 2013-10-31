/*
 * Copyright 2013 Lukas Krejci
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

import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;

import org.revapi.Element;
import org.revapi.ElementAnalyzer;
import org.revapi.MatchReport;
import org.revapi.java.elements.ClassElement;
import org.revapi.java.elements.FieldElement;
import org.revapi.java.elements.MethodElement;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class JavaElementAnalyzer implements ElementAnalyzer {
    private final Set<Check> checks;

    public JavaElementAnalyzer() {
        this(new HashSet<Check>());

        for (Check c : ServiceLoader.load(Check.class, getClass().getClassLoader())) {
            checks.add(c);
        }
    }

    public JavaElementAnalyzer(Set<Check> checks) {
        this.checks = checks;
    }

    @Override
    public void beginAnalysis(Element oldElement, Element newElement) {
        if (oldElement instanceof ClassElement) {
            for (Check c : checks) {
                c.beginCheckClasses((ClassElement) oldElement, (ClassElement) newElement);
            }
        } else if (oldElement instanceof MethodElement) {
            for (Check c : checks) {
                c.beginCheckMethods((MethodElement) oldElement, (MethodElement) newElement);
            }
        } else if (oldElement instanceof FieldElement) {
            for (Check c : checks) {
                c.beginCheckFields((FieldElement) oldElement, (FieldElement) newElement);
            }
        } else {
            for (Check c : checks) {
                c.beginCheckUnknown(oldElement, newElement);
            }
        }
        //TODO implement
    }

    @Override
    public MatchReport endAnalysis(Element oldElement, Element newElement) {
        return null;  //TODO implement
    }
}
