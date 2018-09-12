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

import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class ChoiceExpression implements MatchExpression {
    private final MatchExpression choiceMatch;
    private final ChoiceProducer choiceProducer;

    public ChoiceExpression(MatchExpression choiceMatch, ChoiceProducer choiceProducer) {
        this.choiceMatch = choiceMatch;
        this.choiceProducer = choiceProducer;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaModelElement element) {
        return test(stage, element);
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationAttributeElement attribute) {
        return test(stage, attribute);
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, TypeParameterElement typeParameter) {
        return test(stage, typeParameter);
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, JavaAnnotationElement annotation) {
        return test(stage, annotation);
    }

    private FilterMatch test(ElementGateway.AnalysisStage stage, JavaElement el) {
        return choiceProducer.choiceFor(el).reduce(FilterMatch.DOESNT_MATCH,
                (partial, check) -> partial.or(choiceMatch.matches(stage, check)), FilterMatch::or);
    }
}
