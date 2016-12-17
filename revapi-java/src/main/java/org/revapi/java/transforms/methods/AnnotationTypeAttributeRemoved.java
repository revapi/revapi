/*
 * Copyright 2015 Lukas Krejci
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
 */

package org.revapi.java.transforms.methods;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public class AnnotationTypeAttributeRemoved implements DifferenceTransform<JavaModelElement> {
    private Locale locale;
    private final Pattern[] codes;

    public AnnotationTypeAttributeRemoved() {
        codes = new Pattern[]{Pattern.compile("^" + Pattern.quote(Code.METHOD_REMOVED.code()) + "$")};
    }

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return codes;
    }

    @Nullable
    @Override
    public String[] getConfigurationRootPaths() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema(@Nonnull String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        locale = analysisContext.getLocale();
    }

    @Nullable
    @Override
    public Difference transform(@Nullable JavaModelElement oldElement, @Nullable JavaModelElement newElement,
        @Nonnull Difference difference) {

        if (oldElement == null) {
            throw new IllegalStateException("Annotation type attribute detection called with one of the elements null."
                    + " That should never be the case.");
        }

        ExecutableElement method = (ExecutableElement) oldElement.getDeclaringElement();

        if (method.getEnclosingElement().getKind() == ElementKind.ANNOTATION_TYPE) {
            return Code.METHOD_ATTRIBUTE_REMOVED_FROM_ANNOTATION_TYPE.createDifference(locale,
                    new LinkedHashMap<>(difference.attachments));
        }

        return difference;
    }

    @Override
    public void close() {
    }
}
