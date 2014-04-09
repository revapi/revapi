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

package org.revapi;

/**
 * Enumerates the possible severities of differences found during the API analysis.
 * <p/>
 * The difference doesn't have a single severity, rather it can have different severity for each
 * {@link org.revapi.CompatibilityType}.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public enum DifferenceSeverity {
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
    BREAKING
}
