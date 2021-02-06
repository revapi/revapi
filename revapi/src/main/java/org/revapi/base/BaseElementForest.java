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
package org.revapi.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.revapi.API;
import org.revapi.Element;
import org.revapi.ElementForest;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;

/**
 * A convenience base class for element forest implementations.
 *
 * @param <E> the base type of all the elements produced by some API analyzer
 */
public class BaseElementForest<E extends Element<E>> implements ElementForest<E> {
    private final API api;
    private SortedSet<E> roots;

    public BaseElementForest(API api) {
        this.api = api;
    }

    @Override
    public API getApi() {
        return api;
    }

    @Override
    public SortedSet<E> getRoots() {
        if (roots == null) {
            roots = newRootsInstance();
        }

        return roots;
    }

    public <T extends Element<E>> Stream<T> stream(Class<T> resultType, boolean recurse, TreeFilter<E> filter, Element<E> root) {
        List<T> results = new ArrayList<>();
        search(results, resultType, root == null ? getRoots() : root.getChildren(), recurse, filter);
        return results.stream();
    }

    protected <T extends Element<E>> void search(List<T> results, Class<T> resultType,
            SortedSet<E> siblings, boolean recurse, TreeFilter<E> filter) {
        search(results, resultType, siblings, recurse, filter, true);
    }

    @SuppressWarnings({"SuspiciousMethodCalls"})
    private <T extends Element<E>> void search(List<T> results, Class<T> resultType,
            SortedSet<E> siblings, boolean recurse, TreeFilter<E> filter, boolean topLevel) {
        for (E e : siblings) {
            FilterStartResult res;
            if (filter == null) {
                res = FilterStartResult.matchAndDescend();
            } else {
                res = filter.start(e);
            }

            boolean added = res.getMatch().toBoolean(false);

            if (added && resultType.isAssignableFrom(e.getClass())) {
                results.add(resultType.cast(e));
            }

            if (recurse && res.getDescend().toBoolean(false)) {
                search(results, resultType, e.getChildren(), true, filter, false);
            }

            if (filter != null) {
                FilterFinishResult finalMatch = filter.finish(e);
                if (!added && finalMatch.getMatch().toBoolean(false) && resultType.isAssignableFrom(e.getClass())) {
                    results.add(resultType.cast(e));
                }
            }
        }

        if (topLevel && filter != null) {
            Map<E, FilterFinishResult> matches = filter.finish();
            for (Map.Entry<E, FilterFinishResult> e : matches.entrySet()) {
                if (e.getValue().getMatch().toBoolean(false) && !results.contains(e.getKey())) {
                    results.add(resultType.cast(e.getKey()));
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder bld = new StringBuilder(getClass().getSimpleName());

        addToString(bld, 1, getRoots());

        return bld.toString();
    }

    protected SortedSet<E> newRootsInstance() {
        return new TreeSet<>();
    }

    private void addToString(StringBuilder bld, int indent, SortedSet<? extends Element<?>> elements) {
        for (Element<?> e : elements) {
            bld.append("\n");
            for (int i = 0; i < indent; ++i) {
                bld.append("    ");
            }
            bld.append(e.toString());
            addToString(bld, indent + 1, e.getChildren());
        }
    }
}
