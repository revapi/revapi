/*
 * Copyright 2014-2019 Lukas Krejci
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
package org.revapi.java.transforms.annotations;

import java.io.Reader;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.JavaTypeElement;

/**
 * @deprecated This transform is no longer used: removed annotations are treated
 *             as removed classes.
 *
 * @author Lukas Krejci
 * @since 0.3.0
 */
@Deprecated
public class NoLongerPresent implements DifferenceTransform<JavaTypeElement> {

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[0];
    }

    @Override
    public @Nullable Difference transform(@Nullable JavaTypeElement oldElement, @Nullable JavaTypeElement newElement,
                                          @Nonnull Difference difference) {
        return difference;
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String getExtensionId() {
        return "<<<non-configurable-java-annotation-transform>>>";
    }

    @Override
    public @Nullable Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(AnalysisContext analysisContext) {
    }
}
