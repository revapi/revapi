/*
 * Copyright 2014-2025 Lukas Krejci
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
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.DifferenceTransform;
import org.revapi.TransformationResult;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 *
 * @since 0.12.0
 */
public final class DownplayHarmlessAnnotationChanges implements DifferenceTransform<JavaModelElement> {
    private boolean skip = false;

    private static final Set<String> HARMLESS_ANNOTATIONS = new HashSet<>(
            Arrays.asList("java.lang.FunctionalInterface", "java.lang.annotation.Documented",
                    "jdk.internal.HotSpotIntrinsicCandidate", "jdk.internal.vm.annotation.IntrinsicCandidate"));

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[] { exact("java.annotation.added"), exact("java.annotation.removed"),
                exact("java.annotation.attributeValueChanged"), exact("java.annotation.attributeAdded"),
                exact("java.annotation.attributeRemoved") };
    }

    @Nonnull
    @Override
    public List<Predicate<String>> getDifferenceCodePredicates() {
        // Single Predicate that uses optimized string matching as all codes begin with the same prefix.
        return Collections.singletonList(code -> {
            if (code == null) {
                return false;
            }

            // Doesn't start with the common prefix.
            if (!code.startsWith("java.annotation.")) {
                return false;
            }

            int length = code.length();
            if (length == 21) {
                return code.endsWith("added");
            } else if (length == 23) {
                return code.endsWith("removed");
            } else if (length == 30) {
                return code.endsWith("attributeAdded");
            } else if (length == 32) {
                return code.endsWith("attributeRemoved");
            } else if (length == 37) {
                return code.endsWith("attributeValueChanged");
            }

            return false;
        });
    }

    @Override
    public TransformationResult tryTransform(@Nullable JavaModelElement oldElement,
            @Nullable JavaModelElement newElement, Difference difference) {
        if (skip) {
            return TransformationResult.keep();
        }

        String annotationType = difference.attachments.get("annotationType");
        if (annotationType == null) {
            return TransformationResult.keep();
        }

        if (HARMLESS_ANNOTATIONS.contains(annotationType)) {
            return TransformationResult.replaceWith(Difference.copy(difference).clearClassifications()
                    .addClassifications(reclassify(difference.classification)).build());
        } else {
            return TransformationResult.keep();
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "revapi.java.downplayHarmlessAnnotationChanges";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new StringReader("{\"type\": \"boolean\"}");
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        JsonNode conf = analysisContext.getConfigurationNode().path("downplayHarmlessAnnotationChanges");

        skip = !conf.asBoolean(true);
    }

    private static Pattern exact(String string) {
        return Pattern.compile("^" + Pattern.quote(string) + "$");
    }

    private static Map<CompatibilityType, DifferenceSeverity> reclassify(
            Map<CompatibilityType, DifferenceSeverity> orig) {
        return orig.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), v -> DifferenceSeverity.EQUIVALENT));
    }
}
