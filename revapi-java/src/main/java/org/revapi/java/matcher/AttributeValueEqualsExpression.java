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

import java.util.Objects;
import java.util.regex.Pattern;

import javax.lang.model.element.AnnotationValue;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

import org.revapi.Archive;
import org.revapi.ElementGateway;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
class AttributeValueEqualsExpression extends AbstractAttributeValueExpression {
    private final String expectedValue;
    private final Pattern expectedPattern;

    AttributeValueEqualsExpression(String expectedValue) {
        this.expectedValue = expectedValue;
        this.expectedPattern = null;
    }

    AttributeValueEqualsExpression(Pattern expectedPattern) {
        this.expectedPattern = expectedPattern;
        this.expectedValue = null;
    }

    @Override
    public FilterMatch matches(ElementGateway.AnalysisStage stage, AnnotationValue value, Archive archive,
            ProbingEnvironment env) {
        return FilterMatch.fromBoolean(value.accept(new SimpleAnnotationValueVisitor8<Boolean, Void>() {
            @Override
            protected Boolean defaultAction(Object o, Void __) {
                String val = Util.toHumanReadableString(value);
                return valueMatches(val);
            }

            @Override
            public Boolean visitString(String s, Void __) {
                return valueMatches(s);
            }

            private boolean valueMatches(String s) {
                if (expectedValue != null) {
                    return Objects.equals(expectedValue, s);
                } else if (expectedPattern != null) {
                    return expectedPattern.matcher(s).matches();
                } else {
                    throw new IllegalStateException("Neither exact value or regex specified.");
                }
            }
        }, null));
    }
}
