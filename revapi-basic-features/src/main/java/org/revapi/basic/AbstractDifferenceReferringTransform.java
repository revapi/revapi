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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class AbstractDifferenceReferringTransform<Recipe extends DifferenceMatchRecipe>
    implements DifferenceTransform<Element> {

    private final String extensionId;
    private Collection<Recipe> configuredRecipes;
    private Pattern[] codes;

    protected AbstractDifferenceReferringTransform(@Nonnull String extensionId) {
        this.extensionId = extensionId;
    }

    @Nullable @Override public String getExtensionId() {
        return extensionId;
    }

    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return codes;
    }

    @Nonnull
    protected abstract Recipe newRecipe(AnalysisContext context, ModelNode configNode)
        throws IllegalArgumentException;

    @Override
    public final void initialize(@Nonnull AnalysisContext analysisContext) {
        configuredRecipes = new ArrayList<>();

        ModelNode myNode = analysisContext.getConfiguration();

        if (myNode.getType() != ModelType.LIST) {
            this.codes = new Pattern[0];
            return;
        }

        List<Pattern> codes = new ArrayList<>();

        for (ModelNode config : myNode.asList()) {
            Recipe recipe = newRecipe(analysisContext, config);
            codes.add(
                recipe.codeRegex == null ? Pattern.compile("^" + Pattern.quote(recipe.code) + "$") :
                    recipe.codeRegex);
            configuredRecipes.add(recipe);
        }
        this.codes = codes.toArray(new Pattern[codes.size()]);
    }

    @Nullable
    @Override
    public final Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
        @Nonnull Difference difference) {

        if (configuredRecipes == null) {
            return difference;
        }

        for (Recipe r : configuredRecipes) {
            if (r.matches(difference, oldElement, newElement)) {
                return r.transformMatching(difference, oldElement, newElement);
            }
        }

        return difference;
    }
}
