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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
final class AttributeExpression implements MatchExpression {
    private final ComparisonOperator operator;
    private final @Nullable Object expectedValue;
    private final boolean explicitValueRequired;
    private final @Nullable String attributeName;
    private final @Nullable Pattern attributePattern;

    public AttributeExpression(@Nullable String attributeName, @Nullable String attributePattern, @Nullable ComparisonOperator operator, @Nullable Object expectedValue, boolean explicitValueRequired) {
        this.operator = operator == null ? ComparisonOperator.EQ : operator;
        this.expectedValue = expectedValue;
        this.explicitValueRequired = explicitValueRequired;
        this.attributeName = attributeName;

        //noinspection ConstantConditions
        this.attributePattern = attributePattern == null
                ? null
                : Pattern.compile(attributeName);
    }

    @Override
    public boolean matches(JavaModelElement element) {
        return false;
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        AnnotationMirror am = annotation.getAnnotation();
        Map<? extends ExecutableElement, ? extends AnnotationValue> attrs;

        if (explicitValueRequired) {
            attrs = am.getElementValues();
        } else {
            attrs = annotation.getTypeEnvironment().getElementUtils()
                    .getElementValuesWithDefaults(am);
        }

        AnnotationValue val = findAttributeValue(attrs);
        if (val == null) {
            return false;
        }

        return matchValue(val);
    }

    @Override
    public boolean matches(TypeMirror type) {
        return false;
    }

    private @Nullable AnnotationValue findAttributeValue(Map<? extends ExecutableElement, ? extends AnnotationValue> attrs) {
        for(Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : attrs.entrySet()) {
            ExecutableElement m = e.getKey();
            AnnotationValue v = e.getValue();

            String name = m.getSimpleName().toString();

            if (matchName(name)) {
                return v;
            }
        }

        return null;
    }

    private boolean matchName(String name) {
        if (attributeName != null) {
            switch (operator) {
                case EQ:
                    return attributeName.equals(name);
                case NE:
                    return !attributeName.equals(name);
                default:
                    throw new IllegalArgumentException("String comparisons only support = and != operators.");
            }
        } else if (attributePattern != null) {
            switch (operator) {
                case EQ:
                    return attributePattern.matcher(name).matches();
                case NE:
                    return !attributePattern.matcher(name).matches();
                default:
                    throw new IllegalArgumentException("String comparisons only support = and != operators.");
            }
        } else {
            throw new IllegalStateException("Either string or regex comparison should be set for attribute name.");
        }
    }

    private boolean matchValue(AnnotationValue value) {
        Object actualValue = value.accept(new AnnotationValueVisitor<Object, Void>() {
            @Override
            public Object visit(AnnotationValue av, Void __) {
                return null;
            }

            @Override
            public Object visit(AnnotationValue av) {
                return null;
            }

            @Override
            public Object visitBoolean(boolean b, Void __) {
                return null;
            }

            @Override
            public Object visitByte(byte b, Void __) {
                return null;
            }

            @Override
            public Object visitChar(char c, Void __) {
                return null;
            }

            @Override
            public Object visitDouble(double d, Void __) {
                return null;
            }

            @Override
            public Object visitFloat(float f, Void __) {
                return null;
            }

            @Override
            public Object visitInt(int i, Void __) {
                return null;
            }

            @Override
            public Object visitLong(long i, Void __) {
                return null;
            }

            @Override
            public Object visitShort(short s, Void __) {
                return null;
            }

            @Override
            public Object visitString(String s, Void __) {
                return null;
            }

            @Override
            public Object visitType(TypeMirror t, Void __) {
                return null;
            }

            @Override
            public Object visitEnumConstant(VariableElement c, Void __) {
                return null;
            }

            @Override
            public Object visitAnnotation(AnnotationMirror a, Void __) {
                return null;
            }

            @Override
            public Object visitArray(List<? extends AnnotationValue> vals, Void __) {
                return null;
            }

            @Override
            public Object visitUnknown(AnnotationValue av, Void aVoid) {
                return null;
            }
        }, null);

        return Objects.equals(expectedValue, actualValue);
    }
}
