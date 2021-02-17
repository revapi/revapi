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

import java.util.Map;
import java.util.SortedSet;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.revapi.API;
import org.revapi.ApiAnalyzer;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementForest;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;

/**
 * A convenience base class for API analyzers. This class tries to simplify the element filtering performed during the
 * analysis ({@link #analyze(TreeFilter)}) by requiring the subclasses to implement a more fine-grained "discovery" of
 * elements ({@link #preAnalyze()}, {@link #discoverRoots(Object)}, {@link #discoverElements(Object, Element)} and
 * {@link #postAnalyze(Object)}).
 *
 * @param <E> the parent type of all elements produced by the API analyzer
 *
 * @see BaseEagerLoadingArchiveAnalyzer
 * @see ZipArchiveAnalyzer
 */
public abstract class BaseArchiveAnalyzer<F extends BaseElementForest<E>, E extends Element<E>>
        implements ArchiveAnalyzer<E> {

    private final ApiAnalyzer<E> apiAnalyzer;
    private final API api;

    public BaseArchiveAnalyzer(ApiAnalyzer<E> apiAnalyzer, API api) {
        this.apiAnalyzer = apiAnalyzer;
        this.api = api;
    }

    @Override
    public ApiAnalyzer<E> getApiAnalyzer() {
        return apiAnalyzer;
    }

    @Override
    public API getApi() {
        return api;
    }

    @Override
    public F analyze(TreeFilter<E> filter) {
        Object context = preAnalyze();

        try {
            F forest = newElementForest();

            discoverRoots(context).forEach(r -> addTo(context, filter, forest.getRoots(), r));

            for (Map.Entry<E, FilterFinishResult> e : filter.finish().entrySet()) {
                if (!e.getValue().getMatch().toBoolean(true)) {
                    E parent = e.getKey().getParent();
                    if (parent == null) {
                        forest.getRoots().remove(e.getKey());
                    } else {
                        parent.getChildren().remove(e.getKey());
                    }
                }
            }

            return forest;
        } finally {
            postAnalyze(context);
        }
   }

    @Override
    public void prune(ElementForest<E> forest) {
    }

    /**
     * Creates a new empty element forest of given type.
     */
    protected abstract F newElementForest();

    /**
     * Called as the first thing in {@link #analyze(TreeFilter)}. Can be used by the subclasses to initialize themselves
     * for the analysis.
     *
     * @return a context object that can be used by the other methods or null if no such thing is needed.
     *
     * @see #postAnalyze(Object)
     */
    @Nullable
    protected Object preAnalyze() {
        return null;
    }

    /**
     * Called as the last thing in {@link #analyze(TreeFilter)}. Can be used by the subclasses to clean up after the
     * analysis.
     *
     * @param context the context object created in the {@link #preAnalyze()}
     *
     * @see #preAnalyze()
     */
    protected void postAnalyze(@Nullable Object context) {
    }

    /**
     * Discovers all the root elements in the relevant archives of the API. What is a relevant archive is
     * determined by the implementor.
     *
     * This is called after {@link #preAnalyze()} and before all {@link #discoverElements(Object, Element)} calls.
     *
     * @param context the optional context obtained from the {@link #preAnalyze()} method
     * @return a stream of elements
     */
    protected abstract Stream<E> discoverRoots(@Nullable Object context);

    /**
     * Discovers new elements under the given parent element.
     *
     * @param parent the parent to discover children of
     * @return a stream of elements
     */
    protected abstract Stream<E> discoverElements(@Nullable Object context, E parent);

    /**
     * Adds an element to the set of its potential siblings. This assumes the siblings are part of a
     * {@link BaseElementForest} and therefore automatically update the parent-child relationships.
     *
     * <p>This method can be used to establish the right hierarchy of elements in the element forest.
     *
     * @param filter the filter that the element needs to match to be added to the siblings
     * @param siblings the set of siblings
     * @param element the element to potentially add to siblings
     */
    protected final void addTo(@Nullable Object context, TreeFilter<E> filter, SortedSet<E> siblings, E element) {
        FilterStartResult startRes = filter.start(element);
        if (startRes.getMatch().toBoolean(true)) {
            siblings.add(element);
        }

        if (startRes.getDescend().toBoolean(true)) {
            discoverElements(context, element).forEach(e -> addTo(context, filter, element.getChildren(), e));
        }

        FilterFinishResult endRes = filter.finish(element);
        if (endRes.getMatch() != startRes.getMatch() && endRes.getMatch().toBoolean(true)) {
            siblings.add(element);
        }
    }
}
