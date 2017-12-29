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
package org.revapi.java.transforms;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.util.regex.Pattern;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
public final class DeprecatedElement implements DifferenceTransform<JavaModelElement> {
    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[0];
    }

    @Nullable
    @Override
    public Difference transform(@Nullable JavaModelElement oldElement, @Nullable JavaModelElement newElement, @Nonnull Difference difference) {
        return null;
    }

    @Override
    public void close() throws Exception {

    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "deprecated-elements-reporting";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {

    }
}
