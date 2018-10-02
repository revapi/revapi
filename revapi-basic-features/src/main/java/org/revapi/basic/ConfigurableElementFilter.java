/*
 * Copyright 2014-2018 Lukas Krejci
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
import java.util.ArrayList;
import java.util.Collections;
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
import org.revapi.FilterMatch;
import org.revapi.FilterResult;
import org.revapi.FilterProvider;
import org.revapi.TreeFilter;
import sun.reflect.generics.tree.Tree;

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
public class ConfigurableElementFilter implements FilterProvider {
    private final List<ElementMatcher.CompiledRecipe> elementIncludeRecipes = new ArrayList<>();
    private final List<ElementMatcher.CompiledRecipe> elementExcludeRecipes = new ArrayList<>();
    private final List<Pattern> archiveIncludes = new ArrayList<>();
    private final List<Pattern> archiveExcludes = new ArrayList<>();
    private final IdentityHashMap<Element, FilterResult> filterResults = new IdentityHashMap<>();

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

        doNothing = elementIncludeRecipes.isEmpty() && elementExcludeRecipes.isEmpty() && archiveIncludes.isEmpty() &&
                archiveExcludes.isEmpty();
    }

    @Nullable
    @Override
    public TreeFilter filterFor(ArchiveAnalyzer archiveAnalyzer) {
        List<TreeFilter> excludes = elementExcludeRecipes.stream()
                .map(r -> r.filterFor(archiveAnalyzer))
                .collect(Collectors.toList());

        List<TreeFilter> includes = elementExcludeRecipes.stream()
                .map(r -> r.filterFor(archiveAnalyzer))
                .collect(Collectors.toList());

        return new TreeFilter() {
            @Override
            public FilterResult start(Element element) {
                if (doNothing) {
                    return FilterResult.matchAndDescend();
                }

                String archive = element.getArchive() == null ? null : element.getArchive().getName();

                if (archive != null && !isIncluded(archive, archiveIncludes, archiveExcludes)) {
                    filterResults.put(element, FilterResult.doesntMatch());
                    return FilterResult.doesntMatch();
                }

                // exploit the fact that parent elements are always filtered before the children
                Element parent = element.getParent();
                FilterResult ret = parent == null ? FilterResult.undecidedAndDescend()
                        : filterResults.get(parent);

                FilterResult exclusion = excludeFilterStart(excludes, element).negateMatch();

                switch (ret.getMatch()) {
                    case MATCHES:
                        // the parent was explicitly included in the results. We therefore only need to check if the current
                        // element should be excluded
                        ret = ret.and(exclusion);
                        break;
                    default:
                        ret = includeFilterStart(includes, element, ret).and(exclusion);
                        break;

                }

                if (parent == null && ret.getMatch() == FilterMatch.UNDECIDED) {
                    ret = FilterResult.from(FilterMatch.MATCHES, ret.isDescend());
                }

                filterResults.put(element, ret);

                return ret;
            }

            @Override
            public FilterMatch finish(Element element) {
                if (doNothing) {
                    return FilterMatch.MATCHES;
                }

                FilterResult currentResult = filterResults.remove(element);
                if (currentResult == null) {
                    return FilterMatch.DOESNT_MATCH;
                }

                FilterMatch ret = currentResult.getMatch();

                if (ret == FilterMatch.UNDECIDED) {
                    // see if the filters changed their mind..
                    ret = includeFilterEnd(includes, element).and(excludeFilterEnd(excludes, element).negate());
                }

                return ret;
            }

            @Override
            public Map<Element, FilterMatch> finish() {
                // TODO implement
                return Collections.emptyMap();
            }
        };
    }

    @Override
    public void close() {
    }

    private FilterResult includeFilterStart(List<TreeFilter> includes, Element element, FilterResult defaultResult) {
        return includes.stream()
                .map(f -> f.start(element))
                .reduce(FilterResult::or)
                .orElse(defaultResult);
    }

    private FilterMatch includeFilterEnd(List<TreeFilter> includes, Element element) {
        return includes.stream()
                .map(f -> f.finish(element))
                .reduce(FilterMatch::or)
                .orElse(FilterMatch.DOESNT_MATCH);
    }

    private FilterResult excludeFilterStart(List<TreeFilter> excludes, Element element) {
        return excludes.stream()
                .map(f -> f.start(element))
                .reduce(FilterResult::or)
                .orElse(FilterResult.doesntMatchAndDescend());
    }

    private FilterMatch excludeFilterEnd(List<TreeFilter> excludes, Element element) {
        return excludes.stream()
                .map(f -> f.finish(element))
                .reduce(FilterMatch::or)
                .orElse(FilterMatch.DOESNT_MATCH);
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
}
