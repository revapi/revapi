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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
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
public abstract class AbstractDifferenceReferringTransform
    implements DifferenceTransform<Element> {

    private final String extensionId;
    private Collection<DifferenceMatchRecipe> configuredRecipes;
    private Collection<DifferenceMatchRecipe> activeRecipes;
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
    protected ModelNode getRecipesConfigurationAndInitialize() {
        return analysisContext.getConfiguration();
    }

    protected abstract DifferenceMatchRecipe newRecipe(ModelNode configNode) throws IllegalArgumentException;

    @Override
    public final void initialize(@Nonnull AnalysisContext analysisContext) {
        this.analysisContext = analysisContext;
        configuredRecipes = new ArrayList<>();

        ModelNode myNode = getRecipesConfigurationAndInitialize();

        if (myNode.getType() != ModelType.LIST) {
            this.codes = new Pattern[0];
            return;
        }

        List<Pattern> codes = new ArrayList<>();

        for (ModelNode config : myNode.asList()) {
            DifferenceMatchRecipe recipe = newRecipe(config);
            codes.add(
                recipe.codeRegex == null ? Pattern.compile("^" + Pattern.quote(recipe.code) + "$") :
                    recipe.codeRegex);
            configuredRecipes.add(recipe);
        }
        this.codes = codes.toArray(new Pattern[0]);
    }

    @Override
    public TransformationResult tryTransform(@Nullable Element oldElement, @Nullable Element newElement,
            Difference difference) {

        if (activeRecipes == null) {
            return TransformationResult.keep();
        }

        for (DifferenceMatchRecipe r : activeRecipes) {
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
    public boolean startTraversal(ApiAnalyzer apiAnalyzer, ArchiveAnalyzer oldArchiveAnalyzer,
            ArchiveAnalyzer newArchiveAnalyzer) {
        if (configuredRecipes == null) {
            return false;
        }

        activeRecipes = configuredRecipes.stream()
                .filter(r -> r.startWithAnalyzers(oldArchiveAnalyzer, newArchiveAnalyzer))
                .collect(Collectors.toList());

        return !activeRecipes.isEmpty();
    }

    @Override
    public boolean startElements(@Nullable Element oldElement, @Nullable Element newElement) {
        activeRecipes.forEach(r -> r.startElements(oldElement, newElement));
        return true;
    }

    @Override
    public void endElements(@Nullable Element oldElement, @Nullable Element newElement) {
        activeRecipes.forEach(r -> r.endElements(oldElement, newElement));
    }

    @Override
    public void endTraversal(ApiAnalyzer apiAnalyzer) {
        activeRecipes.forEach(DifferenceMatchRecipe::finishMatching);
    }

    @Override
    public void endAnalysis(ApiAnalyzer apiAnalyzer) {
        activeRecipes.forEach(DifferenceMatchRecipe::cleanup);
        activeRecipes = null;
    }
}
