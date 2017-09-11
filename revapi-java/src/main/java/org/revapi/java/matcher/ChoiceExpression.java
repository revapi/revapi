/*
 * Copyright 2015-2017 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 *
 */

package org.revapi.java.matcher;

import org.revapi.ElementMatcher.Result;
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
    public Result matches(JavaModelElement element) {
        return test(element);
    }

    @Override
    public Result matches(AnnotationAttributeElement attribute) {
        return test(attribute);
    }

    @Override
    public Result matches(TypeParameterElement typeParameter) {
        return test(typeParameter);
    }

    @Override
    public Result matches(JavaAnnotationElement annotation) {
        return test(annotation);
    }

    private Result test(JavaElement el) {
        return choiceProducer.choiceFor(el).reduce(Result.DOESNT_MATCH,
                (partial, check) -> partial.or(choiceMatch.matches(check)), Result::or);
    }
}
