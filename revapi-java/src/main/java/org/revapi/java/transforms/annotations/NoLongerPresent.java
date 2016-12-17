/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java.transforms.annotations;

import java.io.Reader;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ElementKind;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @author Lukas Krejci
 * @since 0.3.0
 */
public class NoLongerPresent implements DifferenceTransform<JavaTypeElement> {
    private Locale locale;

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[]{Pattern.compile("java\\.class\\.removed")};
    }

    @Override
    public @Nullable Difference transform(@Nullable JavaTypeElement oldElement, @Nullable JavaTypeElement newElement,
                                          @Nonnull Difference difference) {
        if (oldElement == null || newElement != null) {
            return difference;
        }

        if (oldElement.getDeclaringElement().getKind() == ElementKind.ANNOTATION_TYPE) {
            return Code.ANNOTATION_NO_LONGER_PRESENT.createDifference(locale,
                    Code.attachmentsFor(oldElement, newElement));
        } else {
            return difference;
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String[] getConfigurationRootPaths() {
        return null;
    }

    @Override
    public @Nullable Reader getJSONSchema(String configurationRootPath) {
        return null;
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
        this.locale = analysisContext.getLocale();
    }
}
