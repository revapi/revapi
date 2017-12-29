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

import static java.util.stream.Collectors.toMap;

import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;

/**
 * A helper class to {@link org.revapi.basic.AbstractDifferenceReferringTransform} that defines the match of
 * a configuration element and a difference.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public abstract class DifferenceMatchRecipe {
    final ModelNode config;
    final boolean regex;
    final String code;
    final Pattern codeRegex;
    final ElementMatcher.CompiledRecipe oldElement;
    final ElementMatcher.CompiledRecipe newElement;
    final Map<String, String> attachments;
    final Map<String, Pattern> attachmentRegexes;

    protected DifferenceMatchRecipe(Map<String, ElementMatcher> matchers, ModelNode config,
            String... additionalReservedProperties) {
        if (!config.has("code")) {
            throw new IllegalArgumentException("Difference code has to be specified.");
        }

        Set<String> reservedProperties = new HashSet<>(4 + additionalReservedProperties.length);
        reservedProperties.add("regex");
        reservedProperties.add("code");
        reservedProperties.add("old");
        reservedProperties.add("new");
        for (String p : additionalReservedProperties) {
            reservedProperties.add(p);
        }

        regex = config.has("regex") && config.get("regex").asBoolean();
        code = config.get("code").asString();
        codeRegex = regex ? Pattern.compile(code) : null;
        oldElement = getElement(regex, config.get("old"), matchers);
        newElement = getElement(regex, config.get("new"), matchers);
        attachments = getAttachments(config, reservedProperties);
        if (regex) {
            attachmentRegexes = attachments.entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, e -> Pattern.compile(e.getValue())));
        } else {
            attachmentRegexes = null;
        }
        this.config = config;
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

        FilterMatch oldMatch = this.oldElement == null
                ? FilterMatch.MATCHES
                : this.oldElement.test(oldElement);
        FilterMatch newMatch = this.newElement == null
                ? FilterMatch.MATCHES
                : this.newElement.test(newElement);

        boolean elementsMatch = oldMatch.and(newMatch).toBoolean(false);

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

    public abstract Difference transformMatching(Difference difference, Element oldElement,
        Element newElement);

    private static ElementMatcher.CompiledRecipe getElement(boolean regex, ModelNode elementRoot, Map<String, ElementMatcher> matchers) {
        if (!elementRoot.isDefined()) {
            return null;
        }

        if (elementRoot.getType() == ModelType.STRING) {
            String recipe = elementRoot.asString();
            return (regex ? new RegexElementMatcher() : new ExactElementMatcher()).compile(recipe).orElse(null);
        } else {
            String matcherId = elementRoot.get("matcher").asString();
            String recipe = elementRoot.get("match").asString();

            ElementMatcher matcher = matchers.get(matcherId);
            return matcher == null
                    ? __ -> FilterMatch.DOESNT_MATCH
                    : matcher.compile(recipe).orElse(null);
        }
    }

    private static Map<String, String> getAttachments(ModelNode elementRoot, Set<String> reservedProperties) {
        if (!elementRoot.isDefined()) {
            return Collections.emptyMap();
        }

        if (elementRoot.getType() != ModelType.OBJECT) {
            return Collections.emptyMap();
        } else {
            Set<String> keys = elementRoot.keys();
            Map<String, String> ret = new HashMap<>(keys.size());
            for (String key: keys) {
                if (!reservedProperties.contains(key)) {
                    ret.put(key, elementRoot.get(key).asString());
                }
            }

            return ret;
        }
    }
}
