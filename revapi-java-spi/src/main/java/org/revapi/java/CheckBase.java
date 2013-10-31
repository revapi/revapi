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

import org.revapi.Element;
import org.revapi.MatchReport;
import org.revapi.java.elements.ClassElement;
import org.revapi.java.elements.FieldElement;
import org.revapi.java.elements.MethodElement;

/**
 * An "empty" implementation of the {@link Check} interface to ease the implementation of this large interface.
 *
 * @author Lukas Krejci
 * @since 1.0
 */
public class CheckBase implements Check {

    protected CheckBase() {

    }

    @Override
    public void beginCheckClasses(ClassElement a, ClassElement b) {

    }

    @Override
    public MatchReport endCheckClasses(ClassElement a, ClassElement b) {
        return null;
    }

    @Override
    public void beginCheckFields(FieldElement a, FieldElement b) {
    }

    @Override
    public MatchReport endCheckFields(FieldElement a, FieldElement b) {
        return null;
    }

    @Override
    public void beginCheckMethods(MethodElement a, MethodElement b) {
    }

    @Override
    public MatchReport endCheckMethods(MethodElement a, MethodElement b) {
        return null;
    }

    @Override
    public void beginCheckUnknown(Element a, Element b) {
    }

    @Override
    public MatchReport endCheckUnknown(Element a, Element b) {
        return null;
    }
}
