/*
 * Copyright 2014 Lukas Krejci
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

package org.revapi.java.transforms.annotations;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.Code;
import org.revapi.java.spi.ElementPairVisitor;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
abstract class AbstractAnnotationPresenceCheck implements DifferenceTransform<JavaModelElement> {
    protected AnalysisContext analysisContext;
    private final String annotationQualifiedName;
    private final Code transformedCode;
    private final Pattern[] codes;

    protected AbstractAnnotationPresenceCheck(String annotationQualifiedName, Code annotationCheckCode,
        Code transformedCode) {
        this.annotationQualifiedName = annotationQualifiedName;
        this.transformedCode = transformedCode;
        String regex = "^" + Pattern.quote(annotationCheckCode.code()) + "$";
        codes = new Pattern[]{Pattern.compile(regex)};
    }

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return codes;
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return null;
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Nullable
    @Override
    public Difference transform(@Nullable final JavaModelElement oldElement,
        @Nullable final JavaModelElement newElement, @Nonnull final Difference difference) {
        //we're checking for change of presence of an annotation on an element. Thus both the old and new version
        //of the element must be non-null.
        if (oldElement == null || newElement == null) {
            return null;
        }

        String affectedAnnotation = difference.attachments.get("annotationType");
        if (!annotationQualifiedName.equals(affectedAnnotation)) {
            return difference;
        }

        return oldElement.getDeclaringElement().accept(new ElementPairVisitor<Difference>() {
            @Override
            protected Difference unmatchedAction(@Nonnull javax.lang.model.element.Element element,
                                                 @Nullable javax.lang.model.element.Element otherElement) {
                return difference;
            }

            @Override
            protected Difference visitType(@Nonnull TypeElement oldElement,
                                           @Nonnull TypeElement newElement) {
                return common();
            }

            @Override
            protected Difference visitPackage(@Nonnull PackageElement element,
                                              @Nonnull PackageElement otherElement) {
                return common();
            }

            @Override
            protected Difference visitVariable(@Nonnull VariableElement element,
                                               @Nonnull VariableElement otherElement) {
                return common();
            }

            @Override
            protected Difference visitExecutable(@Nonnull ExecutableElement element,
                                                 @Nonnull ExecutableElement otherElement) {
                return common();
            }

            private Difference common() {
                return transformedCode.createDifference(analysisContext.getLocale(),
                        new LinkedHashMap<>(difference.attachments));
            }
        }, newElement.getDeclaringElement());
    }

    @Override
    public void close() throws Exception {
    }
}
