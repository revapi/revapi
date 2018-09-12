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

import java.util.List;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import org.revapi.Archive;
import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 */
final class AttributeHasElementExpression extends AbstractAttributeValueExpression {
    private final @Nullable
    AbstractAttributeValueExpression elementMatch;
    private final @Nullable Integer elementIndex;

    AttributeHasElementExpression(@Nullable AbstractAttributeValueExpression elementMatch,
                                  @Nullable Integer elementIndex) {
        this.elementMatch = elementMatch;
        this.elementIndex = elementIndex;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationValue value, Archive archive,
            ProbingEnvironment env) {
        return value.accept(new SimpleAnnotationValueVisitor8<FilterMatch, Void>() {
            @Override
            protected FilterMatch defaultAction(Object o, Void aVoid) {
                return FilterMatch.DOESNT_MATCH;
            }

            @Override
            public FilterMatch visitArray(List<? extends AnnotationValue> vals, Void __) {
                if (elementIndex == null) {
                    if (elementMatch == null) {
                        return FilterMatch.fromBoolean(vals.size() > 0);
                    } else {
                        boolean hasUndecided = false;
                        for (int i = 0; i < vals.size(); ++i) {
                            FilterMatch elementResult = elementMatch.matches(stage, i, vals.get(i), archive, env);

                            if (elementResult == FilterMatch.MATCHES) {
                                return FilterMatch.MATCHES;
                            } else if (elementResult == FilterMatch.UNDECIDED) {
                                hasUndecided = true;
                            }
                        }

                        return hasUndecided ? FilterMatch.UNDECIDED : FilterMatch.DOESNT_MATCH;
                    }
                } else {
                    if (elementMatch == null) {
                        return FilterMatch.fromBoolean(vals.size() > elementIndex);
                    } else {
                        if (vals.size() <= elementIndex) {
                            return FilterMatch.DOESNT_MATCH;
                        } else {
                            return elementMatch.matches(stage, elementIndex, vals.get(elementIndex), archive, env);
                        }
                    }
                }
            }
        }, null);
    }
}
