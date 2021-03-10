/*
 * Copyright 2014-2021 Lukas Krejci
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
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.revapi.AnalysisContext;
import org.revapi.CompatibilityType;
import org.revapi.Criticality;
import org.revapi.Difference;
import org.revapi.DifferenceSeverity;
import org.revapi.Element;
import org.revapi.TreeFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DifferencesTransform<E extends Element<E>> extends AbstractDifferenceReferringTransform<E> {
    private static final Logger LOG = LoggerFactory.getLogger(DifferencesTransform.class);

    private JsonNode bulkClassify;
    private JsonNode bulkIgnore;
    private JsonNode bulkJustification;
    private JsonNode bulkAttachments;
    private JsonNode bulkCriticality;
    private JsonNode bulkMatcher;

    public DifferencesTransform() {
        super("revapi.differences");
    }

    protected DifferencesTransform(String extensionId) {
        super(extensionId);
    }

    private static Map<CompatibilityType, DifferenceSeverity> parseClassification(JsonNode config) {
        Map<CompatibilityType, DifferenceSeverity> classification = new EnumMap<>(CompatibilityType.class);
        for (CompatibilityType ct : CompatibilityType.values()) {
            if (config.has(ct.name())) {
                String val = config.path(ct.name()).asText();
                DifferenceSeverity sev = DifferenceSeverity.valueOf(val);
                classification.put(ct, sev);
            }
        }

        return classification;
    }

    @Nonnull
    @Override
    protected DifferenceMatchRecipe newRecipe(JsonNode node) throws IllegalArgumentException {
        ObjectNode configNode = node.deepCopy();

        if (!configNode.hasNonNull("ignore") && !bulkIgnore.isMissingNode()) {
            configNode.put("ignore", bulkIgnore.asBoolean());
        }

        if (!configNode.hasNonNull("justification") && !bulkJustification.isMissingNode()) {
            configNode.put("justification", bulkJustification.asText());
        }

        if (configNode.hasNonNull("classify")) {
            ObjectNode classify = (ObjectNode) configNode.path("classify");
            if (!classify.hasNonNull("SOURCE") && bulkClassify.hasNonNull("SOURCE")) {
                classify.put("SOURCE", bulkClassify.get("SOURCE").asText());
            }
            if (!classify.hasNonNull("BINARY") && bulkClassify.hasNonNull("BINARY")) {
                classify.put("BINARY", bulkClassify.get("BINARY").asText());
            }
            if (!classify.hasNonNull("SEMANTIC") && bulkClassify.hasNonNull("SEMANTIC")) {
                classify.put("SEMANTIC", bulkClassify.get("SEMANTIC").asText());
            }
            if (!classify.hasNonNull("OTHER") && bulkClassify.hasNonNull("OTHER")) {
                classify.put("OTHER", bulkClassify.get("OTHER").asText());
            }
        } else {
            if (!bulkClassify.isMissingNode()) {
                configNode.set("classify", bulkClassify);
            }
        }

        if (configNode.hasNonNull("attachments")) {
            ObjectNode attachments = (ObjectNode) configNode.get("attachments");
            if (bulkAttachments.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = bulkAttachments.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String key = e.getKey();
                    JsonNode bulkAttachment = e.getValue();

                    if (!attachments.hasNonNull(key)) {
                        attachments.set(key, bulkAttachment);
                    }
                }
            }
        } else {
            if (bulkAttachments.isObject()) {
                configNode.set("attachments", bulkAttachments);
            }
        }

        if (!configNode.hasNonNull("criticality") && !bulkCriticality.isMissingNode()) {
            configNode.put("criticality", bulkCriticality.asText());
        }

        if (!bulkMatcher.isMissingNode()) {
            if (configNode.path("old").isTextual()) {
                ObjectNode match = JsonNodeFactory.instance.objectNode();
                match.put("matcher", bulkMatcher.asText());
                match.put("match", configNode.get("old").asText());
                configNode.set("old", match);
            }
            if (configNode.path("new").isTextual()) {
                ObjectNode match = JsonNodeFactory.instance.objectNode();
                match.put("matcher", bulkMatcher.asText());
                match.put("match", configNode.get("new").asText());
                configNode.set("new", match);
            }
        }

        return new DifferenceRecipe(configNode, analysisContext);
    }

    @Override
    public void close() {
    }

    @Override
    protected JsonNode getRecipesConfigurationAndInitialize() {
        JsonNode configNode = analysisContext.getConfigurationNode();
        bulkClassify = configNode.path("classify");
        bulkIgnore = configNode.path("ignore");
        bulkJustification = configNode.path("justification");
        bulkAttachments = configNode.path("attachments");
        bulkCriticality = configNode.path("criticality");
        bulkMatcher = configNode.path("matcher");

        return configNode.path("differences");
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

        public DifferenceRecipe(JsonNode config, AnalysisContext ctx) {
            super(ctx.getMatchers(), config, "classify", "ignore", "justification", "attachments", "criticality");
            ignore = config.path("ignore").asBoolean(false);
            justification = config.path("justification").asText(null);
            classification = parseClassification(config.path("classify"));
            if (config.hasNonNull("attachments")) {
                JsonNode attachments = config.get("attachments");
                newAttachments = new HashMap<>();
                attachments.fields().forEachRemaining(e -> newAttachments.put(e.getKey(), e.getValue().asText(null)));
            } else {
                newAttachments = emptyMap();
            }

            if (config.hasNonNull("criticality")) {
                String name = config.path("criticality").asText();
                criticality = ctx.getCriticalityByName(name);
                if (criticality == null) {
                    throw new IllegalArgumentException("Unknown criticality '" + name + "'.");
                }
            } else {
                criticality = null;
            }
        }

        @Override
        protected <E extends Element<E>> MatchingProgress<E> createMatchingProgress(@Nullable TreeFilter<E> oldFilter,
                @Nullable TreeFilter<E> newFilter) {
            return new MatchingProgress<E>(regex, code, codeRegex, oldFilter, newFilter, attachments,
                    attachmentRegexes) {
                @Nullable
                @Override
                public Difference transformMatching(Difference difference, Element<?> oldElement,
                        Element<?> newElement) {
                    if (ignore) {
                        return null;
                    }

                    // avoid creating a copy when no updates would be made...
                    if (justification == null && classification.isEmpty() && newAttachments.isEmpty()
                            && criticality == null) {
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
            };
        }
    }
}
