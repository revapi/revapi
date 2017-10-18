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

import java.util.List;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import org.revapi.Archive;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 */
final class AttributeArrayValueEqualsExpression extends AbstractAttributeValueExpression {
    private final List<AbstractAttributeValueExpression> expectedMatches;

    AttributeArrayValueEqualsExpression(List<AbstractAttributeValueExpression> matches) {
        this.expectedMatches = matches;
    }

    @Override
    public FilterMatch matches(AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return value.accept(new SimpleAnnotationValueVisitor8<FilterMatch, Void>(FilterMatch.DOESNT_MATCH) {
            @Override
            public FilterMatch visitArray(List<? extends AnnotationValue> vals, Void __) {
                if (expectedMatches.size() != vals.size()) {
                    return FilterMatch.DOESNT_MATCH;
                }

                int len = expectedMatches.size();
                for (int i = 0; i < len; ++i) {
                    AnnotationValue value = vals.get(i);
                    AbstractAttributeValueExpression match = expectedMatches.get(i);

                    FilterMatch partialResult = match.matches(i, value, archive, env);
                    if (partialResult != FilterMatch.MATCHES) {
                        return partialResult;
                    }
                }

                return FilterMatch.MATCHES;
            }
        }, null);
    }
}
