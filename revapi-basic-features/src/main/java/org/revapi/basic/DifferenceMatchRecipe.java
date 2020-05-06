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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.revapi.Difference;
import org.revapi.Element;

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
    final String oldElement;
    final Pattern oldElementRegex;
    final String newElement;
    final Pattern newElementRegex;
    final Map<String, String> attachments;
    final Map<String, Pattern> attachmentRegexes;

    protected DifferenceMatchRecipe(ModelNode config, String... additionalReservedProperties) {
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
        oldElement = getElement(config.get("old"));
        oldElementRegex = regex && oldElement != null ? Pattern.compile(oldElement) : null;
        newElement = getElement(config.get("new"));
        newElementRegex = regex && newElement != null ? Pattern.compile(newElement) : null;
        attachments = getAttachments(config, reservedProperties);
        if (regex) {
            attachmentRegexes = attachments.entrySet().stream()
                    .collect(toMap(Map.Entry::getKey, e -> Pattern.compile(e.getValue())));
        } else {
            attachmentRegexes = null;
        }
        this.config = config;
    }

    public boolean matches(Difference difference, @Nullable Element oldElement, @Nullable Element newElement) {
        if (regex) {
            boolean baseMatch = codeRegex.matcher(difference.code).matches()
                    && regexMatches(oldElementRegex, oldElement)
                    && regexMatches(newElementRegex, newElement);

            if (!baseMatch) {
                return false;
            } else {
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
            }
        } else {
            boolean baseMatch = code.equals(difference.code)
                    && equalMatches(this.oldElement, oldElement)
                    && equalMatches(this.newElement, newElement);

            if (!baseMatch) {
                return false;
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
    }

    public abstract Difference transformMatching(Difference difference, Element oldElement,
        Element newElement);

    private static String getElement(ModelNode elementRoot) {
        if (!elementRoot.isDefined()) {
            return null;
        }

        return elementRoot.getType() == ModelType.STRING ? elementRoot.asString() : null;
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

    private boolean regexMatches(@Nullable Pattern regex, @Nullable Element element) {
        return regex == null || (element != null && regex.matcher(element.getFullHumanReadableString()).matches());
    }

    private boolean equalMatches(@Nullable String blueprint, @Nullable Element element) {
        return blueprint == null || (element != null && blueprint.equals(element.getFullHumanReadableString()));
    }
}
