/*
 * Copyright 2014-2020 Lukas Krejci
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

import static java.util.Collections.emptyMap;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DifferencesTransform extends AbstractDifferenceReferringTransform {
    private static final Logger LOG = LoggerFactory.getLogger(DifferencesTransform.class);

    private ModelNode bulkClassify;
    private ModelNode bulkIgnore;
    private ModelNode bulkJustification;
    private ModelNode bulkAttachments;
    private ModelNode bulkCriticality;

    public DifferencesTransform() {
        super("revapi.differences");
    }

    protected DifferencesTransform(String extensionId) {
        super(extensionId);
    }

    private static Map<CompatibilityType, DifferenceSeverity> parseClassification(ModelNode config) {
        Map<CompatibilityType, DifferenceSeverity> classification = new EnumMap<>(CompatibilityType.class);
        for (CompatibilityType ct : CompatibilityType.values()) {
            if (config.has(ct.name())) {
                String val = config.get(ct.name()).asString();
                DifferenceSeverity sev = DifferenceSeverity.valueOf(val);
                classification.put(ct, sev);
            }
        }

        return classification;
    }

    @Nonnull
    @Override
    protected DifferenceMatchRecipe newRecipe(ModelNode configNode) throws IllegalArgumentException {
        configNode = configNode.clone();

        if (!configNode.hasDefined("ignore") && bulkIgnore.isDefined()) {
            configNode.get("ignore").set(bulkIgnore.asBoolean());
        }

        if (!configNode.hasDefined("justification") && bulkJustification.isDefined()) {
            configNode.get("justification").set(bulkJustification.asString());
        }

        if (configNode.hasDefined("classify")) {
            ModelNode classify = configNode.get("classify");
            if (!classify.hasDefined("SOURCE") && bulkClassify.hasDefined("SOURCE")) {
                classify.get("SOURCE").set(bulkClassify.get("SOURCE").asString());
            }
            if (!classify.hasDefined("BINARY") && bulkClassify.hasDefined("BINARY")) {
                classify.get("BINARY").set(bulkClassify.get("BINARY").asString());
            }
            if (!classify.hasDefined("SEMANTIC") && bulkClassify.hasDefined("SEMANTIC")) {
                classify.get("SEMANTIC").set(bulkClassify.get("SEMANTIC").asString());
            }
            if (!classify.hasDefined("OTHER") && bulkClassify.hasDefined("OTHER")) {
                classify.get("OTHER").set(bulkClassify.get("OTHER").asString());
            }
        } else {
            if (bulkClassify.isDefined()) {
                configNode.get("classify").set(bulkClassify.asObject());
            }
        }

        if (configNode.hasDefined("attachments")) {
            ModelNode attachments = configNode.get("attachments");
            if (bulkAttachments.isDefined()) {
                for (String key : bulkAttachments.keys()) {
                    if (!attachments.hasDefined(key)) {
                        attachments.get(key).set(bulkAttachments.get(key));
                    }
                }
            }
        } else {
            if (bulkAttachments.isDefined()) {
                configNode.get("attachments").set(bulkAttachments.asObject());
            }
        }

        if (!configNode.hasDefined("criticality") && bulkCriticality.isDefined()) {
            configNode.get("criticality").set(bulkCriticality.asString());
        }

        return new DifferenceRecipe(configNode, analysisContext);
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    protected ModelNode getRecipesConfigurationAndInitialize() {
        ModelNode configNode = analysisContext.getConfiguration();
        bulkClassify = configNode.get("classify");
        bulkIgnore = configNode.get("ignore");
        bulkJustification = configNode.get("justification");
        bulkAttachments = configNode.get("attachments");
        bulkCriticality = configNode.get("criticality");

        return configNode.get("differences");
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/differences-schema.json"),
                StandardCharsets.UTF_8);
    }

    public static class DifferenceRecipe extends DifferenceMatchRecipe {
        protected final boolean ignore;
        protected final String justification;
        protected final Map<CompatibilityType, DifferenceSeverity> classification;
        protected final Map<String, String> newAttachments;
        protected final Criticality criticality;

        public DifferenceRecipe(ModelNode config, AnalysisContext ctx) {
            super(config, "classify", "ignore", "justification", "attachments", "criticality");
            ignore = config.get("ignore").asBoolean(false);
            justification = config.get("justification").asString(null);
            classification = parseClassification(config.get("classify"));
            if (config.hasDefined("attachments")) {
                ModelNode attachments = config.get("attachments");
                newAttachments = new HashMap<>();
                for (String key : attachments.keys()) {
                    newAttachments.put(key, attachments.get(key).asString(null));
                }
            } else {
                newAttachments = emptyMap();
            }

            if (config.hasDefined("criticality")) {
                String name = config.get("criticality").asString();
                criticality = ctx.getCriticalityByName(name);
                if (criticality == null) {
                    throw new IllegalArgumentException("Unknown criticality '" + name + "'.");
                }
            } else {
                criticality = null;
            }
        }

        @Override
        public Difference transformMatching(Difference difference, Element oldElement, Element newElement) {
            if (ignore) {
                return null;
            }

            // avoid creating a copy when no updates would be made...
            if (justification == null && classification.isEmpty() && newAttachments.isEmpty() && criticality == null) {
                return difference;
            }

            Difference.Builder copy = Difference.copy(difference);

            if (justification != null) {
                copy.withJustification(justification);
            }

            if (criticality != null) {
                copy.withCriticality(criticality);
            }

            copy.addClassifications(classification);

            copy.addAttachments(newAttachments);

            return copy.build();
        }
    }
}
