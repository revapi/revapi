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

import org.revapi.ChangeSeverity;
import org.revapi.CompatibilityType;
import org.revapi.MatchReport;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NoLongerInherited extends AbstractInheritedCheck {

    protected MatchReport.Problem createProblem() {
        return Code.ANNOTATION_NO_LONGER_INHERITED
            .initializeNewProblem(configuration.getLocale())
            .addClassification(CompatibilityType.METADATA, ChangeSeverity.POTENTIALLY_BREAKING).build();
    }

    protected Code getCodeToTransform() {
        return Code.ANNOTATION_REMOVED;
    }
}
