/*
 * Copyright 2014-2019 Lukas Krejci
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

import static org.revapi.FilterMatch.UNDECIDED;
import static org.revapi.FilterStartResult.inherit;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterFinishResult;
import org.revapi.TreeFilterProvider;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;

/**
 * An element filter that can filter out elements based on matching their full human readable representations.
 * Archive filter can filter out elements that belong to specified archives.
 *
 * <p>If no include or exclude filters are defined, everything is included. If at least 1 include filter is defined, only
 * elements matching it are included. Out of the included elements, some may be further excluded by the exclude
 * filters.
 *
 * <p>See {@code META-INF/filter-schema.json} for the schema of the configuration.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class ConfigurableElementFilter implements TreeFilterProvider {
    private final List<ElementMatcher.CompiledRecipe> elementIncludeRecipes = new ArrayList<>();
    private final List<ElementMatcher.CompiledRecipe> elementExcludeRecipes = new ArrayList<>();
    private final List<Pattern> archiveIncludes = new ArrayList<>();
    private final List<Pattern> archiveExcludes = new ArrayList<>();

    private boolean doNothing;
    private boolean includeByDefault;

    @Nullable
    @Override
    public String getExtensionId() {
        return "revapi.filter";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return new InputStreamReader(getClass().getResourceAsStream("/META-INF/filter-schema.json"),
                Charset.forName("UTF-8"));
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
        ModelNode root = analysisContext.getConfiguration();
        if (!root.isDefined()) {
            doNothing = true;
            return;
        }

        ModelNode elements = root.get("elements");
        if (elements.isDefined()) {
            readComplexFilter(elements, analysisContext.getMatchers(), elementIncludeRecipes, elementExcludeRecipes);
        }

        ModelNode archives = root.get("archives");
        if (archives.isDefined()) {
            readSimpleFilter(archives, archiveIncludes, archiveExcludes);
        }

        includeByDefault = elementIncludeRecipes.isEmpty() && archiveIncludes.isEmpty();
        doNothing = includeByDefault && elementExcludeRecipes.isEmpty() && archiveExcludes.isEmpty();
    }

    @Nullable
    @Override
    public TreeFilter filterFor(ArchiveAnalyzer archiveAnalyzer) {
        List<TreeFilter> excludes = elementExcludeRecipes.stream()
                .map(r -> r.filterFor(archiveAnalyzer))
                .collect(Collectors.toList());

        List<TreeFilter> includes = elementIncludeRecipes.stream()
                .map(r -> r.filterFor(archiveAnalyzer))
                .collect(Collectors.toList());

        return new TreeFilter() {
            private final IdentityHashMap<Element, IncludeExcludeResult> filterResults = new IdentityHashMap<>();

            @Override
            public FilterStartResult start(Element element) {
                if (doNothing) {
                    return FilterStartResult.matchAndDescendInherited();
                }

                String archive = element.getArchive() == null ? null : element.getArchive().getName();

                if (archive != null && !isIncluded(archive, archiveIncludes, archiveExcludes)) {
                    return FilterStartResult.doesntMatch();
                }

                // exploit the fact that parent elements are always filtered before the children
                Element parent = element.getParent();
                IncludeExcludeResult parentResults = parent == null ? null : filterResults.get(parent);

                // at this point, we need to invoke both include and exclude filters so that we fulfill the contract
                // of TreeFilter and do the full traversal.
                FilterStartResult inclusion = includeFilterStart(includes, element);
                FilterStartResult exclusion = excludeFilterStart(excludes, element);

                IncludeExcludeResult res = new IncludeExcludeResult(inclusion, exclusion, parentResults);

                filterResults.put(element, res);

                return res.compute();
            }

            @Override
            public FilterFinishResult finish(Element element) {
                if (doNothing) {
                    return FilterFinishResult.matches();
                }

                IncludeExcludeResult currentResult = filterResults.get(element);
                if (currentResult == null) {
                    // this indicates non-matching archive, we didn't invoke start of the filters in that case,
                    // so we're free to return early
                    return FilterFinishResult.doesntMatch();
                }

                // we need to end the filters in any case to conform to the contract of TreeFilter
                FilterFinishResult include = includeFilterEnd(includes, element);
                FilterFinishResult exclude = excludeFilterEnd(excludes, element);

                currentResult.include = currentResult.include == null
                        ? null
                        : include == null ? null : currentResult.include.withMatch(include.getMatch());
                currentResult.exclude = currentResult.exclude == null
                        ? null
                        : exclude == null ? null : currentResult.exclude.withMatch(exclude.getMatch());

                return FilterFinishResult.from(currentResult.compute());
            }

            @Override
            public Map<Element, FilterFinishResult> finish() {
                if (doNothing) {
                    return Collections.emptyMap();
                }

                Map<Element, FilterFinishResult> finalIncludes = new HashMap<>();
                for (TreeFilter f : includes) {
                    finalIncludes.putAll(f.finish());
                }

                Map<Element, FilterFinishResult> finalExcludes = new HashMap<>();
                for (TreeFilter f : excludes) {
                    finalExcludes.putAll(f.finish());
                }

                Map<Element, FilterFinishResult> ret = new HashMap<>();
                for (Map.Entry<Element, IncludeExcludeResult> e : filterResults.entrySet()) {
                    IncludeExcludeResult r = e.getValue();
                    Element el = e.getKey();

                    if (r.compute().getMatch() != UNDECIDED) {
                        continue;
                    }

                    FilterFinishResult im = finalIncludes.get(el);
                    if (im == null) {
                        im = FilterFinishResult.from(r.include);
                    }

                    FilterFinishResult em = finalExcludes.get(el);
                    if (em == null) {
                        em = FilterFinishResult.from(r.exclude);
                    } else {
                        em = em.negateMatch();
                    }

                    ret.put(el, im.and(em));
                }

                filterResults.clear();

                return ret;
            }
        };
    }

    @Override
    public void close() {
    }

    @Nullable
    private static FilterStartResult includeFilterStart(List<TreeFilter> includes, Element element) {
        return includes.stream()
                // we always want to descend, no matter the filter result because of the "include inside an exclude"
                // feature
                .map(f -> f.start(element).withDescend(true))
                .reduce(FilterStartResult::or)
                .orElse(null);
    }

    @Nullable
    private static FilterFinishResult includeFilterEnd(List<TreeFilter> includes, Element element) {
        return includes.stream()
                .map(f -> f.finish(element))
                .reduce(FilterFinishResult::or)
                .orElse(null);
    }

    @Nullable
    private static FilterStartResult excludeFilterStart(List<TreeFilter> excludes, Element element) {
        return excludes.stream()
                // we always want to descend, no matter the filter result because of the "include inside an exclude"
                // feature
                .map(f -> f.start(element).withDescend(true))
                .reduce(FilterStartResult::or)
                .orElse(null);
    }

    @Nullable
    private static FilterFinishResult excludeFilterEnd(List<TreeFilter> excludes, Element element) {
        return excludes.stream()
                .map(f -> f.finish(element))
                .reduce(FilterFinishResult::or)
                .orElse(null);
    }

    private static void readSimpleFilter(ModelNode root, List<Pattern> include, List<Pattern> exclude) {
        ModelNode includeNode = root.get("include");

        if (includeNode.isDefined()) {
            for (ModelNode inc : includeNode.asList()) {
                include.add(Pattern.compile(inc.asString()));
            }
        }

        ModelNode excludeNode = root.get("exclude");

        if (excludeNode.isDefined()) {
            for (ModelNode exc : excludeNode.asList()) {
                exclude.add(Pattern.compile(exc.asString()));
            }
        }
    }

    private static void readComplexFilter(ModelNode root, Map<String, ElementMatcher> availableMatchers,
            List<ElementMatcher.CompiledRecipe> include, List<ElementMatcher.CompiledRecipe> exclude) {
        ModelNode includeNode = root.get("include");

        if (includeNode.isDefined()) {
            for (ModelNode inc : includeNode.asList()) {
                ElementMatcher.CompiledRecipe filter = parse(inc, availableMatchers);
                include.add(filter);
            }
        }

        ModelNode excludeNode = root.get("exclude");

        if (excludeNode.isDefined()) {
            for (ModelNode exc : excludeNode.asList()) {
                ElementMatcher.CompiledRecipe filter = parse(exc, availableMatchers);
                exclude.add(filter);
            }
        }
    }

    @Nullable
    private static ElementMatcher.CompiledRecipe parse(ModelNode filterDefinition,
            Map<String, ElementMatcher> availableMatchers) {
        String recipe;
        ElementMatcher matcher;
        if (filterDefinition.getType() == ModelType.STRING) {
            recipe = filterDefinition.asString();
            matcher = new RegexElementMatcher();
        } else {
            recipe = filterDefinition.get("match").asString();
            matcher = availableMatchers.get(filterDefinition.get("matcher").asString());
        }

        if (matcher == null) {
            throw new IllegalStateException("Element matcher with id '" + filterDefinition.get("matcher").asString()
                    + "' was not found.");
        }

        return matcher.compile(recipe).orElse(null);
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

    private static final class IncludeExcludeResult {
        @Nullable
        FilterStartResult include;
        @Nullable
        FilterStartResult exclude;
        IncludeExcludeResult parent;

        public IncludeExcludeResult(FilterStartResult include, FilterStartResult exclude, IncludeExcludeResult parent) {
            this.include = include;
            this.exclude = exclude;
            this.parent = parent;
        }

        FilterStartResult compute() {
            if (parent == null) {
                if (include == null) {
                    if (exclude == null) {
                        return FilterStartResult.matchAndDescendInherited();
                    } else {
                        // exclude is never authoritative
                        return exclude.negateMatch().withInherited(true);
                    }
                } else {
                    if (exclude == null) {
                        return include;
                    } else {
                        return include.and(exclude.negateMatch());
                    }
                }
            } else {
                FilterStartResult parentResult = parent.compute();
                if (include == null) {
                    if (exclude == null) {
                        return parentResult;
                    } else {
                        return inherit(parentResult).and(exclude.negateMatch());
                    }
                } else {
                    if (exclude == null) {
                        // if a parent is included, all its children are included unless explicitly excluded
                        // if the parent is excluded, explicit include can override that
                        return inherit(parentResult).or(include);
                    } else {
                        // if parent is included, our include match cannot override it, just the excludes
                        // if parent is not included, our include can determine whether this element is included or not
                        if (parentResult.getMatch().toBoolean(true)) {
                            return inherit(parentResult).and(exclude.negateMatch());
                        } else {
                            return include.and(exclude.negateMatch());
                        }
                    }
                }
            }
        }
    }
}
