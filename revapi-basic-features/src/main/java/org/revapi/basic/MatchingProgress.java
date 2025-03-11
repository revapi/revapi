/*
 * Copyright 2014-2025 Lukas Krejci
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;

public abstract class MatchingProgress<E extends Element<E>> implements DifferenceTransform.TraversalTracker<E> {
    protected final boolean regex;
    protected final String code;
    protected final Pattern codeRegex;
    protected final TreeFilter<E> oldFilter;
    protected final TreeFilter<E> newFilter;
    protected final Map<String, String> attachments;
    protected final Map<String, Pattern> attachmentRegexes;

    protected final Map<E, Set<E>> decidedlyMatchingElementPairs = new HashMap<>();
    protected final Map<E, Set<E>> undecidedElementPairs = new HashMap<>();

    public MatchingProgress(boolean regex, String code, Pattern codeRegex, TreeFilter<E> oldFilter,
            TreeFilter<E> newFilter, Map<String, String> attachments, Map<String, Pattern> attachmentRegexes) {
        this.regex = regex;
        this.code = code;
        this.codeRegex = codeRegex;
        this.oldFilter = oldFilter;
        this.newFilter = newFilter;
        this.attachments = attachments;
        this.attachmentRegexes = attachmentRegexes;
    }

    public boolean startElements(@Nullable E oldElement, @Nullable E newElement) {
        FilterStartResult oldRes = oldElement == null
                ? (oldFilter == null ? FilterStartResult.matchAndDescend() : FilterStartResult.doesntMatch())
                : (oldFilter == null ? FilterStartResult.matchAndDescend() : oldFilter.start(oldElement));

        FilterStartResult newRes = newElement == null
                ? (newFilter == null ? FilterStartResult.matchAndDescend() : FilterStartResult.doesntMatch())
                : (newFilter == null ? FilterStartResult.matchAndDescend() : newFilter.start(newElement));

        if (oldRes.getMatch().toBoolean(false) && newRes.getMatch().toBoolean(false)) {
            decidedlyMatchingElementPairs.computeIfAbsent(oldElement, __ -> new HashSet<>()).add(newElement);
        } else if (oldRes.getMatch() == Ternary.UNDECIDED || newRes.getMatch() == Ternary.UNDECIDED) {
            undecidedElementPairs.computeIfAbsent(oldElement, __ -> new HashSet<>()).add(newElement);
        }

        return true;
    }

    public void endElements(@Nullable E oldElement, @Nullable E newElement) {
        Ternary oldMatch = oldElement == null ? (oldFilter == null ? Ternary.TRUE : Ternary.FALSE)
                : (oldFilter == null ? Ternary.TRUE : oldFilter.finish(oldElement).getMatch());

        Ternary newMatch = newElement == null ? (newFilter == null ? Ternary.TRUE : Ternary.FALSE)
                : (newFilter == null ? Ternary.TRUE : newFilter.finish(newElement).getMatch());

        if (oldMatch.toBoolean(false) && newMatch.toBoolean(false)) {
            decidedlyMatchingElementPairs.computeIfAbsent(oldElement, __ -> new HashSet<>()).add(newElement);
            Set<E> undecidedNews = undecidedElementPairs.get(oldElement);
            if (undecidedNews != null) {
                undecidedNews.remove(newElement);
            }
        }
    }

    @Override
    public void endTraversal() {
        Map<E, Set<E>> decided = new HashMap<>();
        if (oldFilter != null) {
            for (Map.Entry<E, FilterFinishResult> e : oldFilter.finish().entrySet()) {
                if (!e.getValue().getMatch().toBoolean(false)) {
                    continue;
                }

                for (Map.Entry<E, Set<E>> undecidedE : undecidedElementPairs.entrySet()) {
                    E oldEl = undecidedE.getKey();
                    if (oldEl == null || oldEl.equals(e.getKey())) {
                        decided.computeIfAbsent(oldEl, __ -> new HashSet<>()).addAll(undecidedE.getValue());
                    }
                }
            }
        }

        if (newFilter != null) {
            for (Map.Entry<E, FilterFinishResult> e : newFilter.finish().entrySet()) {
                if (!e.getValue().getMatch().toBoolean(false)) {
                    for (Set<E> news : decided.values()) {
                        news.remove(e.getKey());
                    }
                }
            }
        }

        decided.forEach((o, ns) -> decidedlyMatchingElementPairs.computeIfAbsent(o, __ -> new HashSet<>()).addAll(ns));
    }

    public boolean matches(Difference difference, Element<?> oldElement, Element<?> newElement) {
        // transforms are called after the complete element forests are constructed and analyzed, so we don't
        // expect any UNDECIDED matches from the matchers. If there is one, just consider it a false.

        boolean codeMatch = regex ? codeRegex.matcher(difference.code).matches() : code.equals(difference.code);

        if (!codeMatch) {
            return false;
        }

        Set<E> news = decidedlyMatchingElementPairs.get(oldElement);
        boolean elementsMatch = news != null && news.contains(newElement);

        while (!elementsMatch) {
            oldElement = oldElement == null ? null : oldElement.getParent();
            newElement = newElement == null ? null : newElement.getParent();

            if (oldElement == null && newElement == null) {
                break;
            }

            news = decidedlyMatchingElementPairs.get(oldElement);
            elementsMatch = news != null && news.contains(newElement);
        }

        if (!elementsMatch) {
            return false;
        }

        if (regex) {
            // regexes empty | attachments empty | allMatched
            // 0 | 0 | each regex matches
            // 0 | 1 | false
            // 1 | 0 | true
            // 1 | 1 | true
            boolean allMatched = attachmentRegexes.isEmpty() || !difference.attachments.isEmpty();
            for (Map.Entry<String, String> e : difference.attachments.entrySet()) {
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

    public abstract @Nullable Difference transformMatching(Difference difference, Element<?> oldElement,
            Element<?> newElement);
}
