package org.revapi.basic;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.revapi.Configuration;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class AbstractDifferenceReferringTransform<Recipe extends DifferenceMatchRecipe, ConfigContext>
    implements DifferenceTransform {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDifferenceReferringTransform.class);

    private final String propertyPrefix;
    private final int propertyPrefixLength;
    private Collection<Recipe> configuredRecipes;

    protected AbstractDifferenceReferringTransform(@Nonnull String propertyPrefix) {
        this.propertyPrefix = propertyPrefix + ".";
        this.propertyPrefixLength = propertyPrefix.length() + 1;
    }

    @Nullable
    protected abstract ConfigContext initConfiguration();

    @Nonnull
    protected abstract Recipe newRecipe(@Nullable ConfigContext context);

    protected void assignToRecipe(@Nullable ConfigContext context, @Nonnull Recipe recipe, @Nonnull String key,
        @Nullable String value) {

        switch (key) {
        case "code":
            recipe.code = value;
            break;
        case "old":
            recipe.oldElement = value;
            break;
        case "new":
            recipe.newElement = value;
            break;
        case "regex":
            recipe.regex = value != null && Boolean.parseBoolean(value);
            break;
        }
    }

    protected void finalize(@Nullable ConfigContext context, @Nonnull Recipe recipe) throws IllegalArgumentException {
        if (recipe.code == null) {
            throw new IllegalArgumentException("Difference code not defined");
        }

        if (recipe.regex) {
            recipe.codeRegex = Pattern.compile(recipe.code);
            if (recipe.oldElement != null) {
                recipe.oldElementRegex = Pattern.compile(recipe.oldElement);
            }
            if (recipe.newElement != null) {
                recipe.newElementRegex = Pattern.compile(recipe.newElement);
            }
        }
    }

    @Override
    public final void initialize(@Nonnull Configuration configuration) {
        Map<String, Recipe> foundRecipes = new HashMap<>();
        ConfigContext ctx = initConfiguration();

        for (Map.Entry<String, String> e : configuration.getProperties().entrySet()) {
            if (e.getKey().startsWith(propertyPrefix)) {
                int dotIdx = e.getKey().indexOf('.', propertyPrefixLength);
                if (dotIdx < 0) {
                    LOG.warn("Property name '" + e.getKey() + "' does not have supported format.");
                    continue;
                }

                String recipeId = e.getKey().substring(propertyPrefixLength, dotIdx);

                Recipe recipe = foundRecipes.get(recipeId);
                if (recipe == null) {
                    recipe = newRecipe(ctx);
                    foundRecipes.put(recipeId, recipe);
                }

                String what = e.getKey().substring(dotIdx + 1);
                assignToRecipe(ctx, recipe, what, e.getValue());
            }
        }

        //check if all recipes have code specified and init them
        for (Map.Entry<String, Recipe> e : foundRecipes.entrySet()) {
            Recipe r = e.getValue();

            try {
                finalize(ctx, r);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                    "Property " + propertyPrefix + e.getKey() + " is not properly defined: " + ex.getMessage());
            }
        }

        configuredRecipes = foundRecipes.isEmpty() ? null : foundRecipes.values();
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
