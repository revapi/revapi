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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;
import org.revapi.TransformationResult;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class AbstractDifferenceReferringTransform<E extends Element<E>> implements DifferenceTransform<E> {

    private final String extensionId;
    private Collection<DifferenceMatchRecipe> configuredRecipes;
    private Collection<MatchingProgress<?>> activeRecipes;
    private Pattern[] codes;
    protected AnalysisContext analysisContext;

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

    /**
     * @return a list node where the difference recipes are stored
     */
    protected JsonNode getRecipesConfigurationAndInitialize() {
        return analysisContext.getConfigurationNode();
    }

    protected abstract DifferenceMatchRecipe newRecipe(JsonNode configNode) throws IllegalArgumentException;

    @Override
    public final void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
        configuredRecipes = new ArrayList<>();

        JsonNode myNode = getRecipesConfigurationAndInitialize();

        if (!myNode.isArray()) {
            this.codes = new Pattern[0];
            return;
        }

        List<Pattern> codes = new ArrayList<>();

        for (JsonNode config : myNode) {
            DifferenceMatchRecipe recipe = newRecipe(config);
            codes.add(
                recipe.codeRegex == null ? Pattern.compile("^" + Pattern.quote(recipe.code) + "$") :
                    recipe.codeRegex);
            configuredRecipes.add(recipe);
        }
        this.codes = codes.toArray(new Pattern[0]);
    }

    @Override
    public TransformationResult tryTransform(@Nullable E oldElement, @Nullable E newElement,
            Difference difference) {

        if (activeRecipes == null) {
            return TransformationResult.keep();
        }

        for (MatchingProgress<?> r : activeRecipes) {
            if (r.matches(difference, oldElement, newElement)) {
                Difference d = r.transformMatching(difference, oldElement, newElement);
                if (d == null) {
                    return TransformationResult.discard();
                } else if (d == difference) {
                    return TransformationResult.keep();
                } else {
                    return TransformationResult.replaceWith(d);
                }
            }
        }

        return TransformationResult.keep();
    }

    @Override
    public <X extends Element<X>> Optional<TraversalTracker<X>> startTraversal(ApiAnalyzer<X> apiAnalyzer, ArchiveAnalyzer<X> oldArchiveAnalyzer,
            ArchiveAnalyzer<X> newArchiveAnalyzer) {
        if (configuredRecipes == null) {
            return Optional.empty();
        }

        List<MatchingProgress<X>> recipes = configuredRecipes.stream()
                .map(r -> r.startWithAnalyzers(oldArchiveAnalyzer, newArchiveAnalyzer))
                .collect(Collectors.toList());

        //noinspection unchecked,rawtypes,rawtypes
        activeRecipes = (Collection<MatchingProgress<?>>) (Collection) recipes;

        return Optional.of(new TraversalTracker<X>() {
            @Override
            public boolean startElements(@Nullable X oldElement, @Nullable X newElement) {
                boolean ret = false;
                for (MatchingProgress<X> p : recipes) {
                    ret |= p.startElements(oldElement, newElement);
                }
                return ret;
            }

            @Override
            public void endElements(@Nullable X oldElement, @Nullable X newElement) {
                for (MatchingProgress<X> p : recipes) {
                    p.endElements(oldElement, newElement);
                }
            }

            @Override
            public void endTraversal() {
                for (MatchingProgress<X> p : recipes) {
                    p.endTraversal();
                }
            }
        });
    }

    @Override
    public void endTraversal(TraversalTracker<?> tracker) {
        activeRecipes = null;
    }
}
