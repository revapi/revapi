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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.AbstractAnnotationValueVisitor8;

import org.revapi.Archive;
import org.revapi.FilterMatch;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * @author Lukas Krejci
 */
final class AttributeGreaterLessThanExpression extends AbstractAttributeValueExpression {
    private final Number expectedValue;
    private final boolean less;
    
    AttributeGreaterLessThanExpression(Number expectedValue, boolean less) {
        this.expectedValue = expectedValue;
        this.less = less;
    }

    @Override
    public FilterMatch matches(AnnotationValue value, Archive archive, ProbingEnvironment env) {
        return FilterMatch.fromBoolean(value.accept(new AbstractAnnotationValueVisitor8<Boolean, Void>() {
            @Override
            public Boolean visitBoolean(boolean b, Void __) {
                return false;
            }

            @Override
            public Boolean visitByte(byte b, Void __) {
                return less ? b < expectedValue.byteValue() : b > expectedValue.byteValue();
            }

            @Override
            public Boolean visitChar(char c, Void __) {
                return false;
            }

            @Override
            public Boolean visitDouble(double d, Void __) {
                return less ? d < expectedValue.doubleValue() : d > expectedValue.doubleValue();
            }

            @Override
            public Boolean visitFloat(float f, Void __) {
                return less ? f < expectedValue.floatValue() : f > expectedValue.floatValue();
            }

            @Override
            public Boolean visitInt(int i, Void __) {
                return less ? i < expectedValue.intValue() : i > expectedValue.intValue();
            }

            @Override
            public Boolean visitLong(long i, Void __) {
                return less ? i < expectedValue.longValue() : i > expectedValue.longValue();
            }

            @Override
            public Boolean visitShort(short s, Void __) {
                return less ? s < expectedValue.shortValue() : s > expectedValue.shortValue();
            }

            @Override
            public Boolean visitString(String s, Void __) {
                return false;
            }

            @Override
            public Boolean visitType(TypeMirror t, Void __) {
                return false;
            }

            @Override
            public Boolean visitEnumConstant(VariableElement c, Void __) {
                return false;
            }

            @Override
            public Boolean visitAnnotation(AnnotationMirror a, Void __) {
                return false;
            }

            @Override
            public Boolean visitArray(List<? extends AnnotationValue> vals, Void __) {
                return false;
            }
        }, null));
    }
}
