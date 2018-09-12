/*
 * Copyright 2014-2018 Lukas Krejci
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
package org.revapi.java.matcher;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaFieldElement;
import org.revapi.java.spi.JavaMethodParameterElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;

abstract class AbstractFullScanRequiringExpression implements MatchExpression {
    private final MatchExpression scan;
    private final List<JavaElement> matchedInScan;
    private final List<JavaElement> undecidedInScan;

    AbstractFullScanRequiringExpression(MatchExpression scan) {
        this.scan = scan;
        matchedInScan = new ArrayList<>();
        undecidedInScan = new ArrayList<>();
    }

    protected static JavaTypeElement typeOf(JavaElement el) {
        if (el instanceof JavaTypeElement) {
            return (JavaTypeElement) el;
        } else if (el instanceof JavaFieldElement) {
            return el.getTypeEnvironment().getModelElement(((JavaFieldElement) el).getModelRepresentation());
        } else if (el instanceof JavaAnnotationElement) {
            return el.getTypeEnvironment().getModelElement(((JavaAnnotationElement) el).getAnnotation().getAnnotationType());
        } else if (el instanceof JavaMethodParameterElement) {
            return el.getTypeEnvironment().getModelElement(((JavaMethodParameterElement) el).getModelRepresentation());
        } else if (el instanceof AnnotationAttributeElement) {
            return el.getTypeEnvironment().getModelElement(((AnnotationAttributeElement) el).getAttributeMethod().getReturnType());
        } else {
            return null;
        }
    }

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        return prescanOrMatch(stage, element, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(JavaModelElement element);

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return prescanOrMatch(stage, annotation, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(JavaAnnotationElement element);

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
        return prescanOrMatch(stage, attribute, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(AnnotationAttributeElement element);

    @Override
    public final FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return prescanOrMatch(stage, typeParameter, this::matchesAfterScan);
    }

    protected abstract FilterMatch matchesAfterScan(TypeParameterElement element);

    protected List<JavaElement> getMatchedInScan() {
        return matchedInScan;
    }

    private <E extends JavaElement> FilterMatch prescanOrMatch(ElementGateway.AnalysisStage stage, E element, Function<E, FilterMatch> match) {
        if (stage == ElementGateway.AnalysisStage.FOREST_INCOMPLETE) {
            prescan(element);
            return FilterMatch.UNDECIDED;
        } else {
            completeUndecidedScans();
            return match.apply(element);
        }
    }

    private void prescan(JavaElement element) {
        FilterMatch match = scan.matches(ElementGateway.AnalysisStage.FOREST_INCOMPLETE, element);
        switch (match) {
            case MATCHES:
                matchedInScan.add(element);
                break;
            case UNDECIDED:
                undecidedInScan.add(element);
                break;
        }
    }

    private void completeUndecidedScans() {
        if (!undecidedInScan.isEmpty()) {
            for (JavaElement el : undecidedInScan) {
                FilterMatch match = scan.matches(ElementGateway.AnalysisStage.FOREST_COMPLETE, el);
                switch (match) {
                    case MATCHES:
                        matchedInScan.add(el);
                        break;
                    case UNDECIDED:
                        throw new IllegalStateException("Sub-expression [" + scan
                                + "] could not decidedly match element " + el + " even with a complete element forest" +
                                " available. This is a bug.");
                }
            }
            undecidedInScan.clear();
        }
    }
}
