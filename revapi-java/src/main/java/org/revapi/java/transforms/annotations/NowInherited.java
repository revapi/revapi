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

import org.revapi.MatchReport;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class NowInherited extends AbstractInheritedCheck {
    @Override
    protected MatchReport.Problem createProblem() {
        return Code.ANNOTATION_NOW_INHERITED.createProblem(configuration.getLocale());
    }

    @Override
    protected Code getCodeToTransform() {
        return Code.ANNOTATION_ADDED;
    }
}
