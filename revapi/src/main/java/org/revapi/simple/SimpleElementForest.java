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
package org.revapi.simple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.Element;
import org.revapi.ElementForest;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;
import org.revapi.query.Filter;

/**
 * A simple element forest of {@link org.revapi.simple.SimpleElement}s.
 *
 * @author Lukas Krejci
 * 
 * @since 0.1
 * 
 * @deprecated use {@link org.revapi.base.BaseElementForest} instead
 */
@Deprecated
public class SimpleElementForest implements ElementForest {
    private SortedSet<? extends SimpleElement> roots;
    private final API api;

    protected SimpleElementForest(@Nonnull API api) {
        this.api = api;
    }

    @Override
    @Nonnull
    public API getApi() {
        return api;
    }

    @Override
    @Nonnull
    public SortedSet<? extends SimpleElement> getRoots() {
        if (roots == null) {
            roots = newRootsInstance();
        }
        return roots;
    }

    @Override
    public Stream stream(Class resultType, boolean recurse, TreeFilter filter, @Nullable Element root) {
        return search(resultType, recurse, filter, root).stream();
    }

    public <T extends Element> void search(@Nonnull List<T> results, @Nonnull Class<T> resultType,
            @Nonnull SortedSet<? extends Element> currentLevel, boolean recurse, @Nullable Filter<? super T> filter) {

        for (Element e : currentLevel) {
            if (resultType.isAssignableFrom(e.getClass())) {
                @SuppressWarnings("unchecked")
                T te = (T) e;

                if (filter == null || filter.applies(te)) {
                    results.add(te);
                }
            }

            if (recurse && (filter == null || filter.shouldDescendInto(e))) {
                search(results, resultType, e.getChildren(), true, filter);
            }
        }
    }

    public <T extends Element> List<T> search(Class<T> resultType, boolean recurse, TreeFilter filter, Element root) {
        List<T> results = new ArrayList<>();
        search(results, resultType, root == null ? getRoots() : root.getChildren(), recurse, filter);
        return results;
    }

    public <T extends Element> void search(List<T> results, Class<T> resultType,
            SortedSet<? extends Element> currentLevel, boolean recurse, TreeFilter filter) {
        search(results, resultType, currentLevel, recurse, filter, true);
    }

    @SuppressWarnings({ "SuspiciousMethodCalls" })
    private <T extends Element> void search(List<T> results, Class<T> resultType,
            SortedSet<? extends Element> currentLevel, boolean recurse, TreeFilter filter, boolean topLevel) {
        for (Element e : currentLevel) {
            FilterStartResult res;
            if (filter == null) {
                res = FilterStartResult.matchAndDescend();
            } else {
                res = filter.start(e);
            }

            boolean added = res.getMatch().toBoolean(true);

            if (added) {
                results.add(resultType.cast(e));
            }

            if (recurse && res.getDescend().toBoolean(true)) {
                search(results, resultType, e.getChildren(), true, filter, false);
            }

            if (filter != null) {
                FilterFinishResult finalMatch = filter.finish(e);
                if (!added && finalMatch.getMatch().toBoolean(true)) {
                    results.add(resultType.cast(e));
                }
            }
        }

        if (topLevel && filter != null) {
            Map<Element, FilterFinishResult> matches = filter.finish();
            for (Map.Entry<Element, FilterFinishResult> e : matches.entrySet()) {
                if (e.getValue().getMatch().toBoolean(true) && !results.contains(e.getKey())) {
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

    protected SortedSet<? extends SimpleElement> newRootsInstance() {
        return new TreeSet<>();
    }

    private void addToString(StringBuilder bld, int indent, SortedSet<? extends Element> elements) {
        for (Element e : elements) {
            bld.append("\n");
            for (int i = 0; i < indent; ++i) {
                bld.append("    ");
            }
            bld.append(e.toString());
            addToString(bld, indent + 1, e.getChildren());
        }
    }
}
