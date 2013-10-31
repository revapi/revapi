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

package org.revapi.java.checks;

import org.revapi.MatchReport;
import org.revapi.java.CheckBase;
import org.revapi.java.elements.MethodElement;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public class MethodAdded extends CheckBase {

    @Override
    public MatchReport endCheckMethods(MethodElement a, MethodElement b) {
        return a == null ? generateReport(b) : null;
    }

    private MatchReport generateReport(MethodElement newMethod) {
        newMethod.getParent();
        return null; //TODO implement
    }

}
