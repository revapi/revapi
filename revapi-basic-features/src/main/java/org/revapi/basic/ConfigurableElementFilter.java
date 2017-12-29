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

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterResult;
import org.revapi.simple.SimpleElementGateway;

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
public class ConfigurableElementFilter extends SimpleElementGateway {
    private final List<ComplexFilter> elementIncludes = new ArrayList<>();
    private final List<ComplexFilter> elementExcludes = new ArrayList<>();
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
            readComplexFilter(elements, analysisContext.getMatchers(), elementIncludes, elementExcludes);
        }

        ModelNode archives = root.get("archives");
        if (archives.isDefined()) {
            readSimpleFilter(archives, archiveIncludes, archiveExcludes);
        }

        doNothing = elementIncludes.isEmpty() && elementExcludes.isEmpty() && archiveIncludes.isEmpty() &&
                archiveExcludes.isEmpty();
    }

    @Override
    public FilterResult filter(AnalysisStage stage, Element element) {
        // TODO copy the behavior from the java annotation filter
        if (doNothing) {
            return FilterResult.matchAndDescend();
        }

        String archive = element.getArchive() == null ? null : element.getArchive().getName();

        if (archive != null && !isIncluded(archive, archiveIncludes, archiveExcludes)) {
            return FilterResult.doesntMatch();
        }

        FilterResult include = elementIncludes.stream()
                .map(cf -> FilterResult.from(cf.recipe.test(element), cf.reevaluateChildren))
                .reduce(FilterResult::or)
                .orElse(FilterResult.matchAndDescend());

        FilterResult exclude = elementExcludes.stream()
                .map(cf -> FilterResult.from(cf.recipe.test(element), cf.reevaluateChildren))
                .reduce(FilterResult::or)
                .orElse(FilterResult.doesntMatch());

        return include.and(exclude.negate());
    }

    @Override
    public void close() {
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
            List<ComplexFilter> include, List<ComplexFilter> exclude) {
        ModelNode includeNode = root.get("include");

        if (includeNode.isDefined()) {
            for (ModelNode inc : includeNode.asList()) {
                ComplexFilter filter = parse(inc, availableMatchers);
                include.add(filter);
            }
        }

        ModelNode excludeNode = root.get("exclude");

        if (excludeNode.isDefined()) {
            for (ModelNode exc : excludeNode.asList()) {
                ComplexFilter filter = parse(exc, availableMatchers);
                exclude.add(filter);
            }
        }
    }

    @Nullable
    private static ComplexFilter parse(ModelNode filterDefinition,
            Map<String, ElementMatcher> availableMatchers) {
        String recipe;
        boolean reevaluateChildren;
        ElementMatcher matcher;
        if (filterDefinition.getType() == ModelType.STRING) {
            recipe = filterDefinition.asString();
            //this is the default
            reevaluateChildren = true;
            matcher = new RegexElementMatcher();
        } else {
            recipe = filterDefinition.get("match").asString();
            ModelNode reevaluateChildrenNode = filterDefinition.get("orChildren");

            //true is the default
            reevaluateChildren = !reevaluateChildrenNode.isDefined() || reevaluateChildrenNode.asBoolean();

            matcher = availableMatchers.get(filterDefinition.get("matcher").asString());
        }

        if (matcher == null) {
            throw new IllegalStateException("Element matcher with id '" + filterDefinition.get("matcher").asString()
                    + "' was not found.");
        }

        return matcher.compile(recipe).map(cr -> new ComplexFilter(cr, reevaluateChildren)).orElse(null);
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

    private static final class ComplexFilter {
        final ElementMatcher.CompiledRecipe recipe;
        final boolean reevaluateChildren;

        private ComplexFilter(ElementMatcher.CompiledRecipe recipe, boolean reevaluateChildren) {
            this.recipe = recipe;
            this.reevaluateChildren = reevaluateChildren;
        }
    }
}
