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
 * A generic difference transform that can change the classification of a difference. This can be used in situations
 * where one wants to consider certain differences differently than the defining extension declared them.
 *
 * <p>The transform can be configured like so:
 * <pre><code>
 * {
 *  "revapi" : {
 *      "reclassify" : [
 *          {
 *              "regex" : false,
 *              "code" : "PROBLEM_CODE",
 *              "old" : "FULL_REPRESENTATION_OF_THE_OLD_ELEMENT",
 *              "new" : "FULL_REPRESENTATION_OF_THE_NEW_ELEMENT",
 *              classify : {
 *                  "NEW_COMPATIBILITY_TYPE": "NEW_SEVERITY",
 *                  "NEW_COMPATIBILITY_TYPE_2": "NEW_SEVERITY_2",
 *              }
 *          },
 *          ...
 *      ]
 *  }
 * }
 * </code></pre>
 *
 * <p>The {@code code} is mandatory (obviously). The {@code old} and {@code new} properties are optional and the rule will
 * match when all the specified properties of it match. If regex attribute is "true" (defaults to "false"), all the
 * code, old and new are understood as regexes (java regexes, not javascript ones).
 *
 * <p>The {@code NEW_COMPATIBILITY_TYPE} corresponds to one of the names of the {@link org.revapi.CompatibilityType}
 * enum and the {@code NEW_SEVERITY} corresponds to one of the names of the {@link org.revapi.DifferenceSeverity}
 * enum. The reclassified difference inherits its classification (i.e. the compatibility type + severity pairs) and
 * only redefines the ones explicitly defined in the configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 * @deprecated This is superseded by {@link DifferencesTransform}
 */
@Deprecated
public class ClassificationTransform extends DifferencesTransform {

    private static final Logger LOG = LoggerFactory.getLogger(ClassificationTransform.class);

    public ClassificationTransform() {
        super("revapi.reclassify");
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/classification-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Override
    protected ModelNode getRecipesConfigurationAndInitialize() {
        ModelNode ret = analysisContext.getConfiguration();
        if (ret.isDefined()) {
            LOG.warn("The `revapi.reclassify` extension is deprecated. Consider using the `revapi.differences` instead.");
        }

        return ret;
    }

    @Nonnull
    @Override
    protected DifferenceMatchRecipe newRecipe(ModelNode config) {
        return new DifferenceRecipe(config, analysisContext);
    }

    @Override
    public void close() {
    }
}
