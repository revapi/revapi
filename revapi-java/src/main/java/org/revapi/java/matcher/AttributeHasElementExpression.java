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

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import org.revapi.Archive;
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
    public boolean matches(AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return value.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {
            @Override
            protected Boolean defaultAction(Object o, Void aVoid) {
                return false;
            }

            @Override
            public Boolean visitArray(List<? extends AnnotationValue> vals, Void __) {
                if (elementIndex == null) {
                    if (elementMatch == null) {
                        return vals.size() > 0;
                    } else {
                        for (int i = 0; i < vals.size(); ++i) {
                            if (elementMatch.matches(i, vals.get(i), archive, env)) {
                                return true;
                            }
                        }
                        return false;
                    }
                } else {
                    if (elementMatch == null) {
                        return vals.size() > elementIndex;
                    } else {
                        return vals.size() > elementIndex && elementMatch.matches(elementIndex, vals.get(elementIndex),
                                archive, env);
                    }
                }
            }
        }, null);
    }
}
