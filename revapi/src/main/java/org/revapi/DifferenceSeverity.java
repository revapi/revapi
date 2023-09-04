/*
 * Copyright 2014-2023 Lukas Krejci
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
package org.revapi;

/**
 * Enumerates the possible severities of differences found during the API analysis.
 *
 * <p>
 * The difference doesn't have a single severity, rather it can have different severity for each
 * {@link org.revapi.CompatibilityType}.
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public enum DifferenceSeverity {

    /**
     * The difference doesn't produce any visible change from the user's perspective.
     */
    EQUIVALENT,

    /**
     * The difference doesn't cause any breakage in given compatibility type
     */
    NON_BREAKING,

    /**
     * Under certain circumstances the difference is breaking the compatibility of given type.
     */
    POTENTIALLY_BREAKING,

    /**
     * The difference definitely breaks the compatibility of given type.
     */
    BREAKING;

    /**
     * Returns the difference severity represented by the provided string in camel case. I.e. "nonBreaking" parses to
     * {@link #NON_BREAKING}, etc.
     *
     * @param camelCaseValue
     *            the value in camel case
     *
     * @return the corresponding difference severity instance
     */
    public static DifferenceSeverity fromCamelCase(String camelCaseValue) {
        if (camelCaseValue == null) {
            return null;
        }

        switch (camelCaseValue) {
        case "equivalent":
            return EQUIVALENT;
        case "nonBreaking":
            return NON_BREAKING;
        case "potentiallyBreaking":
            return POTENTIALLY_BREAKING;
        case "breaking":
            return BREAKING;
        default:
            return null;
        }
    }
}
