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
 * @author Lukas Krejci
 * @since 1.0
 */
public interface Check {

    //TODO add begin/end pairs for all java element types. Also create a "Simple" or "Base" impl of this iface that would
    //do nothing in all methods to ease the impl for the subclasses.

    void beginCheckClasses(ClassElement a, ClassElement b);

    MatchReport endCheckClasses(ClassElement a, ClassElement b);

    void beginCheckFields(FieldElement a, FieldElement b);

    MatchReport endCheckFields(FieldElement a, FieldElement b);

    void beginCheckMethods(MethodElement a, MethodElement b);

    MatchReport endCheckMethods(MethodElement a, MethodElement b);

    void beginCheckUnknown(Element a, Element b);

    MatchReport endCheckUnknown(Element a, Element b);
}
