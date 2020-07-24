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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A generic difference transform that can ignore differences based on the difference code ({@link
 * org.revapi.Difference#code}) and on the old or new elements' full human representations
 * ({@link org.revapi.Element#getFullHumanReadableString()}).
 *
 * <p>The transform is configured using properties in the general form of:
 * <pre><code>
 *  {
 *      "revapi" : {
 *          "ignore" : [
 *              {
 *                  "regex" : false,
 *                  "code" : "PROBLEM_CODE",
 *                  "old" : "FULL_REPRESENTATION_OF_THE_OLD_ELEMENT",
 *                  "new" : "FULL_REPRESENTATION_OF_THE_NEW_ELEMENT",
 *                  "justification": "blah"
 *              },
 *              ...
 *          ]
 *      }
 *  }
 * </code></pre>
 * The {@code code} is mandatory (obviously). The {@code old} and {@code new} properties are optional and the rule will
 * match when all the specified properties of it match. If regex attribute is "true" (defaults to "false"), all the
 * code, old and new are understood as regexes (java regexes, not javascript ones).
 *
 * @author Lukas Krejci
 * @since 0.1
 * @deprecated This is superseded by {@link DifferencesTransform}
 */
@Deprecated
public class IgnoreDifferenceTransform extends DifferencesTransform {

    private static final Logger LOG = LoggerFactory.getLogger(IgnoreDifferenceTransform.class);

    public IgnoreDifferenceTransform() {
        super("revapi.ignore");
    }

    @Override
    protected ModelNode getRecipesConfigurationAndInitialize() {
        ModelNode ret = analysisContext.getConfiguration();
        if (ret.isDefined()) {
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
    protected DifferenceRecipe newRecipe(ModelNode config) {
        config = config.clone();
        config.get("ignore").set(true);
        return new DifferenceRecipe(config, analysisContext);
    }

    @Override
    public void close() {
    }
}
