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

import static java.util.Collections.emptySet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.revapi.API;
import org.revapi.ApiAnalyzer;
import org.revapi.Archive;
import org.revapi.TreeFilter;

/**
 * A base class for archive analyzers that need to load all the elements in an archive eagerly.
 *
 * @param <E>
 *            the parent type of all elements produced by the API analyzer
 */
public abstract class BaseEagerLoadingArchiveAnalyzer<F extends BaseElementForest<E>, E extends BaseElement<E>>
        extends BaseArchiveAnalyzer<F, E> {

    private final boolean processSupplementaryArchives;

    public BaseEagerLoadingArchiveAnalyzer(ApiAnalyzer<E> apiAnalyzer, API api, boolean processSupplementaryArchives) {
        super(apiAnalyzer, api);
        this.processSupplementaryArchives = processSupplementaryArchives;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation eagerly analyzes all the archives and remembers the results so that the final tree can be
     * computed.
     *
     * @return a context object with the results of the analysis
     */
    @Override
    protected FullForestContext preAnalyze() {
        FullForestContext ctx = new FullForestContext();

        for (Archive archive : getApi().getArchives()) {
            Set<E> archiveRoots = createElements(archive);
            ctx.remember(archiveRoots);
        }

        if (processSupplementaryArchives && getApi().getSupplementaryArchives() != null) {
            for (Archive archive : getApi().getSupplementaryArchives()) {
                Set<E> archiveRoots = createElements(archive);
                ctx.remember(archiveRoots);
            }
        }

        return ctx;
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This method assumes the context is a {@link FullForestContext} and uses it to return the recorded roots.
     * 
     * @param context
     *            the full forest context
     * 
     * @return the recorded roots
     */
    @Override
    protected Stream<E> discoverRoots(Object context) {
        @SuppressWarnings("unchecked")
        FullForestContext ctx = (FullForestContext) context;
        return ctx.roots.stream();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * This method assumes the context is a {@link FullForestContext} and uses it to return the recorded children of the
     * provided element.
     * 
     * @param context
     *            the full forest context
     * 
     * @return the recorded children of the element
     */
    @Override
    protected Stream<E> discoverElements(Object context, E parent) {
        @SuppressWarnings("unchecked")
        FullForestContext ctx = (FullForestContext) context;
        return ctx.originalChildren.getOrDefault(parent, emptySet()).stream();
    }

    /**
     * Scans the archive and returns the elements within. Each of the returned elements is supposed to have a fully
     * realized hierarchy of children.
     *
     * @param archive
     *            the archive to analyze
     * 
     * @return a set of root elements found in the archive (with children initialized, too)
     */
    protected abstract Set<E> createElements(Archive archive);

    /**
     * This context object is used to "remember" all the elements that were created during parsing of an archive.
     * Because these elements can be further filtered during the {@link #analyze(TreeFilter)} call, we don't construct
     * an element forest from these results straight away but rather store the preliminary results in this context
     * object and only pass it to filtering in the {@link #discoverRoots(Object)} and
     * {@link #discoverElements(Object, BaseElement)} methods.
     */
    protected class FullForestContext {
        public Map<E, Set<E>> originalChildren = new HashMap<>();
        public Set<E> roots = new HashSet<>();

        /**
         * This method remembers the children of all the elements in the provided set (recursively) and clears out the
         * children of each of the elements. This is so that we don't end up with elements that don't match the filter
         * in the final results.
         * 
         * @param elements
         *            the elements to remember
         */
        public void remember(Set<E> elements) {
            for (E e : elements) {
                if (originalChildren.containsKey(e)) {
                    continue;
                }

                Set<E> children = e.getChildren();

                originalChildren.computeIfAbsent(e, __ -> new HashSet<>()).addAll(children);

                if (e.getParent() == null) {
                    roots.add(e);
                } else {
                    remember(e.getParent().getChildren());
                }

                remember(children);
                children.clear();
            }
        }
    }
}
