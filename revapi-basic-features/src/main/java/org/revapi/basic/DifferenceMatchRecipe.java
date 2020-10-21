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
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.ElementPair;
import org.revapi.FilterFinishResult;
import org.revapi.FilterMatch;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;
import org.revapi.configuration.JSONUtil;

/**
 * A helper class to {@link org.revapi.basic.AbstractDifferenceReferringTransform} that defines the match of
 * a configuration element and a difference.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class DifferenceMatchRecipe {
    final JsonNode config;
    final boolean regex;
    final String code;
    final Pattern codeRegex;
    final ElementMatcher.CompiledRecipe oldRecipe;
    final ElementMatcher.CompiledRecipe newRecipe;
    TreeFilter oldFilter;
    TreeFilter newFilter;
    final Map<String, String> attachments;
    final Map<String, Pattern> attachmentRegexes;

    Set<ElementPair> decidedlyMatchingElementPairs;
    Set<ElementPair> undecidedElementPairs;

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
        oldRecipe = getElement(regex, config.path("old"), matchers);
        newRecipe = getElement(regex, config.path("new"), matchers);
        attachments = getAttachments(config, reservedProperties);
        if (regex) {
            attachmentRegexes = attachments.entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, e -> Pattern.compile(e.getValue())));
        } else {
            attachmentRegexes = null;
        }
        this.config = config;
    }

    public boolean startWithAnalyzers(ArchiveAnalyzer oldAnalyzer, ArchiveAnalyzer newAnalyzer) {
        oldFilter = oldRecipe == null ? null : oldRecipe.filterFor(oldAnalyzer);
        newFilter = newRecipe == null ? null : newRecipe.filterFor(newAnalyzer);
        decidedlyMatchingElementPairs = new HashSet<>();
        undecidedElementPairs = new HashSet<>();
        return true;
    }


    public boolean startElements(@Nullable Element oldElement, @Nullable Element newElement) {
        FilterStartResult oldRes = oldElement == null
                ? (oldFilter == null ? FilterStartResult.matchAndDescend() : FilterStartResult.doesntMatch())
                : (oldFilter == null ? FilterStartResult.matchAndDescend() : oldFilter.start(oldElement));

        FilterStartResult newRes = newElement == null
                ? (newFilter == null ? FilterStartResult.matchAndDescend() : FilterStartResult.doesntMatch())
                : (newFilter == null ? FilterStartResult.matchAndDescend() : newFilter.start(newElement));

        if (oldRes.getMatch().toBoolean(false) && newRes.getMatch().toBoolean(false)) {
            decidedlyMatchingElementPairs.add(new ElementPair(oldElement, newElement));
        } else if (oldRes.getMatch() == FilterMatch.UNDECIDED || newRes.getMatch() == FilterMatch.UNDECIDED) {
            undecidedElementPairs.add(new ElementPair(oldElement, newElement));
        }

        return true;
    }

    public void endElements(@Nullable Element oldElement, @Nullable Element newElement) {
        FilterMatch oldMatch = oldElement == null
                ? (oldFilter == null ? FilterMatch.MATCHES : FilterMatch.DOESNT_MATCH)
                : (oldFilter == null ? FilterMatch.MATCHES : oldFilter.finish(oldElement).getMatch());

        FilterMatch newMatch = newElement == null
                ? (newFilter == null ? FilterMatch.MATCHES : FilterMatch.DOESNT_MATCH)
                : (newFilter == null ? FilterMatch.MATCHES : newFilter.finish(newElement).getMatch());

        if (oldMatch.toBoolean(false) && newMatch.toBoolean(false)) {
            ElementPair pair = new ElementPair(oldElement, newElement);
            decidedlyMatchingElementPairs.add(pair);
            undecidedElementPairs.remove(pair);
        }
    }

    public void finishMatching() {
        Set<ElementPair> decided = new HashSet<>();
        if (oldFilter != null) {
            for (Map.Entry<Element, FilterFinishResult> e : oldFilter.finish().entrySet()) {
                if (!e.getValue().getMatch().toBoolean(false)) {
                    continue;
                }

                for (ElementPair p : undecidedElementPairs) {
                    if (p.getOldElement() == null || p.getOldElement().equals(e.getKey())) {
                        decided.add(p);
                    }
                }
            }
        }

        if (newFilter != null) {
            for (Map.Entry<Element, FilterFinishResult> e : newFilter.finish().entrySet()) {
                if (!e.getValue().getMatch().toBoolean(false)) {
                    continue;
                }

                for (ElementPair p : decided) {
                    if (p.getNewElement() == null || p.getNewElement().equals(e.getKey())) {
                        decided.add(p);
                    }
                }
            }
        }

        decidedlyMatchingElementPairs.addAll(decided);
    }

    public void cleanup() {
        oldFilter = null;
        newFilter = null;
        decidedlyMatchingElementPairs = null;
        undecidedElementPairs = null;
    }

    public boolean matches(Difference difference, Element oldElement, Element newElement) {
        //transforms are called after the complete element forests are constructed and analyzed, so we don't
        //expect any UNDECIDED matches from the matchers. If there is one, just consider it a false.

        boolean codeMatch = regex
                ? codeRegex.matcher(difference.code).matches()
                : code.equals(difference.code);

        if (!codeMatch) {
            return false;
        }

        boolean elementsMatch = decidedlyMatchingElementPairs.contains(new ElementPair(oldElement, newElement));

        if (!elementsMatch) {
            return false;
        }

        if (regex) {
            //regexes empty | attachments empty | allMatched
            //            0 |                 0 | each regex matches
            //            0 |                 1 | false
            //            1 |                 0 | true
            //            1 |                 1 | true
            boolean allMatched = attachmentRegexes.isEmpty() || !difference.attachments.isEmpty();
            for (Map.Entry<String, String> e: difference.attachments.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();

                Pattern match = attachmentRegexes.get(key);
                if (match != null && !match.matcher(val).matches()) {
                    return false;
                } else {
                    allMatched = true;
                }
            }

            return allMatched;
        } else {
            boolean allMatched = attachments.isEmpty() || !difference.attachments.isEmpty();
            for (Map.Entry<String, String> e : difference.attachments.entrySet()) {
                String key = e.getKey();
                String val = e.getValue();

                String match = attachments.get(key);
                if (match != null && !match.equals(val)) {
                    return false;
                } else {
                    allMatched = true;
                }
            }

            return allMatched;
        }
    }

    public abstract @Nullable Difference transformMatching(Difference difference, Element oldElement,
        Element newElement);

    private static ElementMatcher.CompiledRecipe getElement(boolean regex, JsonNode elementRoot, Map<String, ElementMatcher> matchers) {
        if (elementRoot.isMissingNode()) {
            return null;
        }

        if (elementRoot.isTextual()) {
            String recipe = elementRoot.asText();
            return (regex ? new RegexElementMatcher() : new ExactElementMatcher()).compile(recipe).orElse(null);
        } else {
            String matcherId = elementRoot.path("matcher").asText();
            String recipe = elementRoot.path("match").asText();

            ElementMatcher matcher = matchers.get(matcherId);

            if (matcher == null) {
                throw new IllegalArgumentException("Matcher called '" + matcherId + "' not found.");
            }

            return matcher.compile(recipe).orElse(null);
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
