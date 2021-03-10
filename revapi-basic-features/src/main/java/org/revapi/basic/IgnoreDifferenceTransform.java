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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic difference transform that can ignore differences based on the difference code
 * ({@link org.revapi.Difference#code}) and on the old or new elements' full human representations
 * ({@link org.revapi.Element#getFullHumanReadableString()}) or result of comparing using specified matcher.
 *
 * <p>
 * See {@code META-INF/ignore-schema.json} for the JSON schema of the configuration.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 * 
 * @deprecated This is superseded by {@link DifferencesTransform}
 */
@Deprecated
public class IgnoreDifferenceTransform extends DifferencesTransform {

    private static final Logger LOG = LoggerFactory.getLogger(IgnoreDifferenceTransform.class);

    public IgnoreDifferenceTransform() {
        super("revapi.ignore");
    }

    @Override
    protected JsonNode getRecipesConfigurationAndInitialize() {
        JsonNode ret = analysisContext.getConfigurationNode();
        if (!ret.isNull()) {
            LOG.warn("The `revapi.ignore` extension is deprecated. Consider using the `revapi.differences` instead.");
        }

        return ret;
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/ignore-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Nonnull
    @Override
    protected DifferenceRecipe newRecipe(JsonNode config) {
        ObjectNode cfg = config.deepCopy();
        cfg.put("ignore", true);
        return new DifferenceRecipe(cfg, analysisContext);
    }

    @Override
    public void close() {
    }
}
