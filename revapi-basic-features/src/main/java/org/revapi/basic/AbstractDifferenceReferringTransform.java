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
public abstract class AbstractDifferenceReferringTransform<Recipe extends DifferenceMatchRecipe, ConfigContext>
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

    @Nullable
    protected abstract ConfigContext initConfiguration();

    @Nonnull
    protected abstract Recipe newRecipe(@Nullable ConfigContext context, ModelNode configNode)
        throws IllegalArgumentException;

    @Override
    public final void initialize(@Nonnull AnalysisContext analysisContext) {
        ConfigContext ctx = initConfiguration();
        configuredRecipes = new ArrayList<>();

        int idx = 0;
        ModelNode myNode = analysisContext.getConfiguration();

        if (myNode.getType() != ModelType.LIST) {
            this.codes = new Pattern[0];
            return;
        }

        List<Pattern> codes = new ArrayList<>();

        for (ModelNode config : myNode.asList()) {
            Recipe recipe = newRecipe(ctx, config);
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
