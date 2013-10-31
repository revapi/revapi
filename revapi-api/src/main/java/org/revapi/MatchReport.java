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

package org.revapi;

import java.util.HashMap;

/**
 * @author Lukas Krejci
 * @since 1.0
 */
public final class MatchReport {
    public static final class StructuredDescription extends HashMap<String, StructuredDescription> {
        private String simpleText;

        public String getSimpleText() {
            return isEmpty() ? simpleText : null;
        }

        public void setSimpleText(String simpleText) {
            this.simpleText = simpleText;
            clear();
        }
    }

    private final String code;
    private final StructuredDescription description;
    private final MismatchSeverity mismatchSeverity;
    private final CompatibilityType compatibilityType;
    private final Element oldElement;
    private final Element newElement;

    public MatchReport(CompatibilityType compatibilityType, String code, StructuredDescription description,
        MismatchSeverity mismatchSeverity, Element oldElement, Element newElement) {

        this.code = code;
        this.compatibilityType = compatibilityType;
        this.description = description;
        this.mismatchSeverity = mismatchSeverity;
        this.oldElement = oldElement;
        this.newElement = newElement;
    }

    /**
     * @return Language dependent unique identification of the report problem
     */
    public String getCode() {
        return code;
    }

    public Element getNewElement() {
        return newElement;
    }

    public Element getOldElement() {
        return oldElement;
    }

    public CompatibilityType getCompatibilityType() {
        return compatibilityType;
    }

    public StructuredDescription getDescription() {
        return description;
    }

    public MismatchSeverity getMismatchSeverity() {
        return mismatchSeverity;
    }
}
