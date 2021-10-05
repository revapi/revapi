/*
 * Copyright 2014-2021 Lukas Krejci
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
package org.revapi.examples.apianalyzer;

import java.util.Objects;

import javax.annotation.Nullable;

import org.revapi.CompatibilityType;
import org.revapi.DifferenceSeverity;
import org.revapi.Report;
import org.revapi.base.BaseDifferenceAnalyzer;

/**
 * Revapi uses difference analyzers to check whether two elements, one coming from the old API and the other from the
 * new API are considered different or not. Revapi does a parallel depth-first traversal of the element trees (created
 * by the archive analyzers) and can detect add, removed or changed elements. The job of the difference analyzer is to
 * produce a report detailing the differences that quantify the change on the elements.
 */
public class PropertiesDifferenceAnalyzer extends BaseDifferenceAnalyzer<PropertyElement> {

    @Override
    public void beginAnalysis(PropertyElement oldElement, PropertyElement newElement) {
        // here, we would start a descend into the element pair but since properties are not hierarchical, we actually
        // don't have to do anything here
    }

    @Override
    public Report endAnalysis(@Nullable PropertyElement oldProp, @Nullable PropertyElement newProp) {
        Report.Builder bld = Report.builder().withOld(oldProp).withNew(newProp);

        if (oldProp == null) {
            bld.addDifference().withCode("property.added")
                    .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.NON_BREAKING)
                    .withName("Property Added").done();
        } else if (newProp == null) {
            bld.addDifference().withCode("property.removed")
                    .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.BREAKING)
                    .withName("Property Removed").done();
        } else if (!Objects.equals(oldProp.getValue(), newProp.getValue())) {
            bld.addDifference().withCode("property.valueChanged")
                    .addClassification(CompatibilityType.SEMANTIC, DifferenceSeverity.POTENTIALLY_BREAKING)
                    .withName("Property Value Changed").done();
        }

        return bld.build();
    }
}
