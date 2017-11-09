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
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.Element;

import org.jboss.dmr.ModelNode;
import org.revapi.ElementMatcher;

/**
 * A generic difference transform that can ignore differences based on the difference code ({@link
 * org.revapi.Difference#code}) and on the old or new elements' full human representations
 * ({@link org.revapi.Element#getFullHumanReadableString()}) or result of comparing using specified matcher.
 *
 * <p>See {@code META-INF/ignore-schema.json} for the JSON schema of the configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class IgnoreDifferenceTransform
    extends AbstractDifferenceReferringTransform<IgnoreDifferenceTransform.IgnoreRecipe> {

    public static class IgnoreRecipe extends DifferenceMatchRecipe {
        public IgnoreRecipe(Map<String, ElementMatcher> matchers, ModelNode node) {
            super(matchers, node, "justification");
        }

        @Override
        public Difference transformMatching(Difference difference, Element oldElement,
            Element newElement) {

            //we ignore the matching elements, so null is the correct return value.
            return null;
        }
    }

    public IgnoreDifferenceTransform() {
        super("revapi.ignore");
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/ignore-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Nonnull
    @Override
    protected IgnoreRecipe newRecipe(AnalysisContext context, ModelNode config) {
        return new IgnoreRecipe(context.getMatchers(), config);
    }

    @Override
    public void close() {
    }
}
