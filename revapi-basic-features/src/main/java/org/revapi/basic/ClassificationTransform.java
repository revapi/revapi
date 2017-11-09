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
package org.revapi.basic;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;

import org.jboss.dmr.ModelNode;
import org.revapi.ElementMatcher;

/**
 * A generic difference transform that can change the classification of a difference. This can be used in situations
 * where one wants to consider certain differences differently than the defining extension declared them.
 *
 * <p>See {@code META-INF/classification-schema.json} for the JSON schema of the configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class ClassificationTransform
    extends AbstractDifferenceReferringTransform<ClassificationTransform.ClassificationRecipe> {

    public static class ClassificationRecipe extends DifferenceMatchRecipe {
        protected final Map<CompatibilityType, DifferenceSeverity> classification = new EnumMap<>(
            CompatibilityType.class);

        public ClassificationRecipe(Map<String, ElementMatcher> matchers, ModelNode node) {
            super(matchers, node, "classify");
            ModelNode classfications = node.get("classify");
            for (CompatibilityType ct : CompatibilityType.values()) {
                if (classfications.has(ct.name())) {
                    String val = classfications.get(ct.name()).asString();
                    DifferenceSeverity sev = DifferenceSeverity.valueOf(val);
                    classification.put(ct, sev);
                }
            }
        }

        @Override
        public Difference transformMatching(Difference difference, Element oldElement,
            Element newElement) {
            if (classification.isEmpty()) {
                return difference;
            } else {
                return Difference.builder().withCode(difference.code).withName(difference.name)
                    .withDescription(difference.description).addAttachments(difference.attachments)
                    .addClassifications(difference.classification).addClassifications(classification).build();
            }
        }
    }

    public ClassificationTransform() {
        super("revapi.reclassify");
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/classification-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Nonnull
    @Override
    protected ClassificationRecipe newRecipe(AnalysisContext context, ModelNode config) {
        return new ClassificationRecipe(context.getMatchers(), config);
    }

    @Override
    public void close() {
    }
}
