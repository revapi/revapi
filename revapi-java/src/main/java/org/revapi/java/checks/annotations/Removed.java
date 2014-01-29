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

package org.revapi.java.checks.annotations;

import java.util.Collections;
import java.util.List;

import javax.lang.model.element.AnnotationMirror;

import org.revapi.ChangeSeverity;
import org.revapi.MatchReport;
import org.revapi.java.Util;
import org.revapi.java.checks.AbstractJavaCheck;
import org.revapi.java.checks.Code;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public final class Removed extends AbstractJavaCheck {
    @Override
    protected List<MatchReport.Problem> doVisitAnnotation(AnnotationMirror oldAnnotation,
        AnnotationMirror newAnnotation) {

        if (oldAnnotation != null && newAnnotation == null) {
            return Collections.singletonList(
                createProblem(Code.ANNOTATION_REMOVED, null, null, ChangeSeverity.POTENTIALLY_BREAKING, new String[]{
                    Util.toHumanReadableString(oldAnnotation.getAnnotationType())}, oldAnnotation));
        }

        return null;
    }
}
