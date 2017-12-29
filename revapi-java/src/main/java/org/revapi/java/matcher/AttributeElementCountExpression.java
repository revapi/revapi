/*
 * Copyright 2014-2017 Lukas Krejci
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

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import org.revapi.Archive;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 */
final class AttributeElementCountExpression extends AbstractAttributeValueExpression {
    private final int expectedCount;
    private final Boolean less;

    AttributeElementCountExpression(int expectedCount, Boolean less) {
        this.expectedCount = expectedCount;
        this.less = less;
    }

    @Override
    public FilterMatch matches(AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return FilterMatch.fromBoolean(value.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {
            @Override
            protected Boolean defaultAction(Object o, Void __) {
                return false;
            }

            @Override
            public Boolean visitArray(List<? extends AnnotationValue> vals, Void __) {
                return less == null
                        ? vals.size() == expectedCount
                        : (less ? vals.size() < expectedCount : vals.size() > expectedCount);
            }
        }, null));
    }
}
