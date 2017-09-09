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

import java.util.Objects;
import java.util.regex.Pattern;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 */
final class AttributeTypeEqualsExpression implements MatchExpression {
    private final String expectedTypeName;
    private final Pattern expectedTypeNamePattern;

    public AttributeTypeEqualsExpression(String expectedTypeName) {
        this.expectedTypeName = expectedTypeName;
        this.expectedTypeNamePattern = null;
    }

    public AttributeTypeEqualsExpression(Pattern expectedTypeNamePattern) {
        this.expectedTypeName = null;
        this.expectedTypeNamePattern = expectedTypeNamePattern;
    }

    @Override
    public boolean matches(JavaModelElement element) {
        return false;
    }

    @Override
    public boolean matches(JavaAnnotationElement annotation) {
        return false;
    }

    @Override
    public boolean matches(TypeParameterElement typeParameter) {
        return false;
    }

    @Override
    public boolean matches(AnnotationAttributeElement attribute) {
        String valueType = Util.toHumanReadableString(attribute.getAttributeMethod().getReturnType());
        return typeMatches(valueType);
    }

    private Boolean typeMatches(String typeName) {
        if (expectedTypeName != null) {
            return Objects.equals(expectedTypeName, typeName);
        } else if (expectedTypeNamePattern != null) {
            return expectedTypeNamePattern.matcher(typeName).matches();
        } else {
            throw new IllegalStateException("Neither exact type name or regex specified.");
        }
    }

}
