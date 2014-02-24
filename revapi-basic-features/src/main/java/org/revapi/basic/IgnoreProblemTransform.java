/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

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
import org.revapi.Element;
import org.revapi.MatchReport;
import org.revapi.ProblemTransform;

/**
 * A generic problem transform that can ignore problems based on the problem code ({@link
 * org.revapi.MatchReport.Problem#code}) and on the old or new elements' full human representations
 * ({@link org.revapi.Element#getFullHumanReadableString()}).
 * <p/>
 * The transform is configured using properties in the general form of:
 * <pre><code>
 * revapi.ignore.1.regex=false
 * revapi.ignore.1.code=PROBLEM_CODE;
 * revapi.ignore.1.old=FULL_REPRESENTATION_OF_THE_OLD_ELEMENT
 * revapi.ignore.1.new=FULL_REPRESENTATION_OF_THE_NEW_ELEMENT
 * revapi.ignore.2.code=PROBLEM_CODE
 * ...
 * </code></pre>
 * The {@code code} is mandatory (obviously). The {@code old} and {@code new} properties are optional and the rule will
 * match when all the specified properties of it match. If regex attribute is "true" (defaults to "false"), all the
 * code, old and new are understood as regexes.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class IgnoreProblemTransform implements ProblemTransform {
    private static final Logger LOG = LoggerFactory.getLogger(IgnoreProblemTransform.class);

    public static final String CONFIG_PROPERTY_PREFIX = "revapi.ignore.";
    private static final int CONFIG_PROPERTY_PREFIX_LENGTH = CONFIG_PROPERTY_PREFIX.length();

    private static class IgnoreRecipe {
        boolean regex;
        String code;
        Pattern codeRegex;
        String oldElement;
        Pattern oldElementRegex;
        String newElement;
        Pattern newElementRegex;

        boolean shouldIgnore(MatchReport.Problem problem, Element oldElement, Element newElement) {
            if (regex) {
                return codeRegex.matcher(problem.code).matches() &&
                    (oldElementRegex == null ||
                        oldElementRegex.matcher(oldElement.getFullHumanReadableString()).matches()) &&
                    (newElementRegex == null ||
                        newElementRegex.matcher(newElement.getFullHumanReadableString()).matches());
            } else {
                return code.equals(problem.code) &&
                    (this.oldElement == null || this.oldElement.equals(oldElement.getFullHumanReadableString())) &&
                    (this.newElement == null || this.newElement.equals(newElement.getFullHumanReadableString()));
            }
        }
    }

    private Collection<IgnoreRecipe> toIgnore;

    @Override
    public void initialize(@Nonnull Configuration configuration) {
        Map<String, IgnoreRecipe> foundRecipes = new HashMap<>();

        for (Map.Entry<String, String> e : configuration.getProperties().entrySet()) {
            if (e.getKey().startsWith(CONFIG_PROPERTY_PREFIX)) {
                int dotIdx = e.getKey().indexOf('.', CONFIG_PROPERTY_PREFIX_LENGTH);
                if (dotIdx < 0) {
                    LOG.warn("Property name '" + e.getKey() + "' does not have supported format.");
                    continue;
                }

                String recipeId = e.getKey().substring(CONFIG_PROPERTY_PREFIX_LENGTH, dotIdx);

                IgnoreRecipe recipe = foundRecipes.get(recipeId);
                if (recipe == null) {
                    recipe = new IgnoreRecipe();
                    foundRecipes.put(recipeId, recipe);
                }

                String what = e.getKey().substring(dotIdx + 1);
                switch (what) {
                case "code":
                    recipe.code = e.getValue();
                    break;
                case "old":
                    recipe.oldElement = e.getValue();
                    break;
                case "new":
                    recipe.newElement = e.getValue();
                    break;
                case "regex":
                    recipe.regex = Boolean.parseBoolean(e.getValue());
                    break;
                }
            }
        }

        //check if all recipes have code specified and init them
        for (Map.Entry<String, IgnoreRecipe> e : foundRecipes.entrySet()) {
            IgnoreRecipe r = e.getValue();

            if (r.code == null) {
                throw new IllegalArgumentException(
                    CONFIG_PROPERTY_PREFIX + e.getKey() + " doesn't define the problem code.");
            }

            if (r.regex) {
                r.codeRegex = Pattern.compile(r.code);
                if (r.oldElement != null) {
                    r.oldElementRegex = Pattern.compile(r.oldElement);
                }
                if (r.newElement != null) {
                    r.newElementRegex = Pattern.compile(r.newElement);
                }
            }
        }

        toIgnore = foundRecipes.isEmpty() ? null : foundRecipes.values();
    }

    @Nullable
    @Override
    public MatchReport.Problem transform(@Nullable Element oldElement, @Nullable Element newElement,
        @Nonnull MatchReport.Problem problem) {
        if (toIgnore == null) {
            return problem;
        }

        for (IgnoreRecipe r : toIgnore) {
            if (r.shouldIgnore(problem, oldElement, newElement)) {
                return null;
            }
        }

        return problem;
    }
}
