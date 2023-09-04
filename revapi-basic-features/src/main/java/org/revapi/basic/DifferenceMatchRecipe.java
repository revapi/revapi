/*
 * Copyright 2014-2023 Lukas Krejci
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

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.TreeFilter;
import org.revapi.configuration.JSONUtil;

/**
 * A helper class to {@link org.revapi.basic.AbstractDifferenceReferringTransform} that defines the match of a
 * configuration element and a difference.
 *
 * @author Lukas Krejci
 *
 * @since 0.1
 */
public abstract class DifferenceMatchRecipe {
    final JsonNode config;
    final boolean regex;
    final String code;
    final Pattern codeRegex;
    final ElementMatcher.CompiledRecipe oldRecipe;
    final ElementMatcher.CompiledRecipe newRecipe;
    final Map<String, String> attachments;
    final Map<String, Pattern> attachmentRegexes;

    protected DifferenceMatchRecipe(Map<String, ElementMatcher> matchers, JsonNode config,
            String... additionalReservedProperties) {
        if (!config.hasNonNull("code")) {
            throw new IllegalArgumentException("Difference code has to be specified.");
        }

        Set<String> reservedProperties = new HashSet<>(4 + additionalReservedProperties.length);
        reservedProperties.add("regex");
        reservedProperties.add("code");
        reservedProperties.add("old");
        reservedProperties.add("new");
        reservedProperties.addAll(asList(additionalReservedProperties));

        regex = config.path("regex").asBoolean();
        code = config.get("code").asText();
        codeRegex = regex ? Pattern.compile(code) : null;
        oldRecipe = getRecipe(regex, config.path("old"), matchers);
        newRecipe = getRecipe(regex, config.path("new"), matchers);
        attachments = getAttachments(config, reservedProperties);
        if (regex) {
            attachmentRegexes = attachments.entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, e -> Pattern.compile(e.getValue())));
        } else {
            attachmentRegexes = null;
        }
        this.config = config;
    }

    @Nullable
    public <E extends Element<E>> MatchingProgress<E> startWithAnalyzers(ArchiveAnalyzer<E> oldAnalyzer,
            ArchiveAnalyzer<E> newAnalyzer) {
        TreeFilter<E> oldFilter = oldRecipe == null ? null : oldRecipe.filterFor(oldAnalyzer);
        TreeFilter<E> newFilter = newRecipe == null ? null : newRecipe.filterFor(newAnalyzer);
        return createMatchingProgress(oldFilter, newFilter);
    }

    protected abstract <E extends Element<E>> MatchingProgress<E> createMatchingProgress(
            @Nullable TreeFilter<E> oldFilter, @Nullable TreeFilter<E> newFilter);

    private static ElementMatcher.CompiledRecipe getRecipe(boolean regex, JsonNode elementRoot,
            Map<String, ElementMatcher> matchers) {
        if (elementRoot.isMissingNode()) {
            return null;
        }

        if (elementRoot.isTextual()) {
            String recipe = elementRoot.asText();
            return (regex ? new RegexElementMatcher() : new ExactElementMatcher()).compile(recipe)
                    .orElseThrow(() -> new IllegalArgumentException("Failed to compile the match recipe."));
        } else {
            String matcherId = elementRoot.path("matcher").asText();
            String recipe = elementRoot.path("match").asText();

            ElementMatcher matcher = matchers.get(matcherId);

            if (matcher == null) {
                throw new IllegalArgumentException("Matcher called '" + matcherId + "' not found.");
            }

            return matcher.compile(recipe)
                    .orElseThrow(() -> new IllegalArgumentException("Failed to compile the match recipe."));
        }
    }

    private static Map<String, String> getAttachments(JsonNode elementRoot, Set<String> reservedProperties) {
        if (JSONUtil.isNullOrUndefined(elementRoot)) {
            return Collections.emptyMap();
        }

        if (!elementRoot.isObject()) {
            return Collections.emptyMap();
        } else {
            Map<String, String> ret = new HashMap<>();

            Iterator<Map.Entry<String, JsonNode>> it = elementRoot.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (!reservedProperties.contains(e.getKey())) {
                    ret.put(e.getKey(), e.getValue().asText());
                }
            }

            return ret;
        }
    }
}
