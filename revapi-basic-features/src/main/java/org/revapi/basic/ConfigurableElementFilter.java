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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import org.revapi.AnalysisContext;
import org.revapi.Archive;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;
import org.revapi.TreeFilterProvider;
import org.revapi.base.OverridableIncludeExcludeTreeFilter;

/**
 * An element filter that can filter out elements based on matching their full human readable representations. Archive
 * filter can filter out elements that belong to specified archives.
 *
 * <p>
 * If no include or exclude filters are defined, everything is included. If at least 1 include filter is defined, only
 * elements matching it are included. Out of the included elements, some may be further excluded by the exclude filters.
 *
 * <p>
 * See {@code META-INF/filter-schema.json} for the schema of the configuration.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public class ConfigurableElementFilter implements TreeFilterProvider {
    private final List<ElementMatcher.CompiledRecipe> elementIncludeRecipes = new ArrayList<>();
    private final List<ElementMatcher.CompiledRecipe> elementExcludeRecipes = new ArrayList<>();
    private final List<Pattern> archiveIncludes = new ArrayList<>();
    private final List<Pattern> archiveExcludes = new ArrayList<>();

    private boolean doNothing;

    @Nullable
    @Override
    public String getExtensionId() {
        return "revapi.filter";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/filter-schema.json"),
                StandardCharsets.UTF_8);
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        JsonNode root = analysisContext.getConfigurationNode();
        if (root.isNull()) {
            doNothing = true;
            return;
        }

        JsonNode elements = root.path("elements");
        if (!elements.isMissingNode()) {
            readComplexFilter(elements, analysisContext.getMatchers(), elementIncludeRecipes, elementExcludeRecipes);
        }

        JsonNode archives = root.path("archives");
        if (!archives.isMissingNode()) {
            readSimpleFilter(archives, archiveIncludes, archiveExcludes);
        }

        doNothing = elementIncludeRecipes.isEmpty() && archiveIncludes.isEmpty() && elementExcludeRecipes.isEmpty()
                && archiveExcludes.isEmpty();
    }

    @Override
    public <E extends Element<E>> Optional<TreeFilter<E>> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
        @Nullable
        TreeFilter<E> excludes = elementExcludeRecipes.isEmpty() ? null : TreeFilter.union(elementExcludeRecipes
                .stream().map(r -> r.filterFor(archiveAnalyzer)).filter(Objects::nonNull).collect(Collectors.toList()));
        @Nullable
        TreeFilter<E> includes = elementIncludeRecipes.isEmpty() ? null : TreeFilter.union(elementIncludeRecipes
                .stream().map(r -> r.filterFor(archiveAnalyzer)).filter(Objects::nonNull).collect(Collectors.toList()));

        return Optional.of(new OverridableIncludeExcludeTreeFilter<E>(includes, excludes) {
            final Set<Archive> excludedArchives = Collections.newSetFromMap(new IdentityHashMap<>());

            @Override
            public FilterStartResult start(E element) {
                if (doNothing) {
                    return FilterStartResult.defaultResult();
                }

                String archive = element.getArchive() == null ? null : element.getArchive().getName();

                if (archive != null && !isIncluded(archive, archiveIncludes, archiveExcludes)) {
                    excludedArchives.add(element.getArchive());
                    return FilterStartResult.doesntMatch();
                }

                return super.start(element);
            }

            @Override
            public FilterFinishResult finish(E element) {
                if (doNothing) {
                    return FilterFinishResult.matches();
                }

                if (excludedArchives.contains(element.getArchive())) {
                    return FilterFinishResult.doesntMatch();
                }

                return super.finish(element);
            }

            @Override
            public Map<E, FilterFinishResult> finish() {
                if (doNothing) {
                    return Collections.emptyMap();
                }

                excludedArchives.clear();

                return super.finish();
            }
        });
    }

    @Override
    public void close() {
    }

    private static void readSimpleFilter(JsonNode root, List<Pattern> include, List<Pattern> exclude) {
        JsonNode includeNode = root.path("include");

        if (includeNode.isArray()) {
            for (JsonNode inc : includeNode) {
                include.add(Pattern.compile(inc.asText()));
            }
        }

        JsonNode excludeNode = root.path("exclude");

        if (excludeNode.isArray()) {
            for (JsonNode exc : excludeNode) {
                exclude.add(Pattern.compile(exc.asText()));
            }
        }
    }

    private static void readComplexFilter(JsonNode root, Map<String, ElementMatcher> availableMatchers,
            List<ElementMatcher.CompiledRecipe> include, List<ElementMatcher.CompiledRecipe> exclude) {
        JsonNode includeNode = root.path("include");

        if (includeNode.isArray()) {
            for (JsonNode inc : includeNode) {
                ElementMatcher.CompiledRecipe filter = parse(inc, availableMatchers);
                include.add(filter);
            }
        }

        JsonNode excludeNode = root.path("exclude");

        if (excludeNode.isArray()) {
            for (JsonNode exc : excludeNode) {
                ElementMatcher.CompiledRecipe filter = parse(exc, availableMatchers);
                exclude.add(filter);
            }
        }
    }

    @Nullable
    private static ElementMatcher.CompiledRecipe parse(JsonNode filterDefinition,
            Map<String, ElementMatcher> availableMatchers) {
        String recipe;
        ElementMatcher matcher;
        if (filterDefinition.isTextual()) {
            recipe = filterDefinition.asText();
            matcher = new RegexElementMatcher();
        } else {
            recipe = filterDefinition.path("match").asText();
            matcher = availableMatchers.get(filterDefinition.path("matcher").asText(null));
        }

        if (matcher == null) {
            throw new IllegalStateException(
                    "Element matcher with id '" + filterDefinition.path("matcher").asText(null) + "' was not found.");
        }

        return matcher.compile(recipe)
                .orElseThrow(() -> new IllegalArgumentException("Failed to compile the match recipe."));
    }

    private static boolean isIncluded(String representation, List<Pattern> includePatterns,
            List<Pattern> excludePatterns) {
        boolean include = true;

        if (!includePatterns.isEmpty()) {
            include = false;
            for (Pattern p : includePatterns) {
                if (p.matcher(representation).matches()) {
                    include = true;
                    break;
                }
            }
        }

        if (include) {
            for (Pattern p : excludePatterns) {
                if (p.matcher(representation).matches()) {
                    include = false;
                    break;
                }
            }
        }

        return include;
    }
}
