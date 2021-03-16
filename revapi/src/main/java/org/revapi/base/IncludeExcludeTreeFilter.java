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

import static org.revapi.FilterStartResult.inherit;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.revapi.Element;
import org.revapi.FilterFinishResult;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;

/**
 * A tree filter that uses a combination of an include and exclude filters to arrive at the filtering decisions. If
 * there is no include filter defined, everything is included. If there is an exclude filter defined, it excludes from
 * the included elements.
 *
 * @param <E>
 */
public class IncludeExcludeTreeFilter<E extends Element<E>> extends BaseTreeFilter<E> {

    private final @Nullable TreeFilter<E> include;
    private final @Nullable TreeFilter<E> exclude;
    private final IdentityHashMap<E, IncludeExcludeResult> progress = new IdentityHashMap<>();

    /**
     * A lack of any include or exclude filter needs to be expressed as a null value.
     *
     * @param include
     *            the include filter or null if there is no include filter
     * @param exclude
     *            the exclude filter or null if there is no exclude filter
     */
    public IncludeExcludeTreeFilter(@Nullable TreeFilter<E> include, @Nullable TreeFilter<E> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    @Override
    public FilterStartResult start(E element) {
        return doStart(element).compute();
    }

    protected IncludeExcludeResult doStart(E element) {
        FilterStartResult inclusion = processIncludeStart(include == null ? null : include.start(element));
        FilterStartResult exclusion = processExcludeStart(exclude == null ? null : exclude.start(element));

        E parent = element.getParent();
        IncludeExcludeResult parentResult = parent == null ? null : progress.get(parent);

        IncludeExcludeResult res = constructResult(inclusion, exclusion, parentResult);
        progress.put(element, res);

        return res;
    }

    protected IncludeExcludeResult constructResult(@Nullable FilterStartResult inclusion,
            @Nullable FilterStartResult exclusion, @Nullable IncludeExcludeResult parent) {
        return new IncludeExcludeResult(inclusion, exclusion, parent);
    }

    protected @Nullable FilterStartResult processIncludeStart(@Nullable FilterStartResult result) {
        return result;
    }

    protected @Nullable FilterStartResult processExcludeStart(@Nullable FilterStartResult result) {
        return result;
    }

    @Override
    public FilterFinishResult finish(E element) {

        FilterFinishResult inclusion = include == null ? null : include.finish(element);
        FilterFinishResult exclusion = exclude == null ? null : exclude.finish(element);

        IncludeExcludeResult currentResult;
        // TODO we can't remove the result from the progress because revapi-java does out-of-order filtering of methods
        // revapi-java needs to be fixed because that breaks the contract of the TreeFilter... But let's just not at
        // this moment, because there are big changes planeed for revapi-java anyway.
        // if ((inclusion == null || inclusion.getMatch() != Ternary.UNDECIDED) &&
        // (exclusion == null || exclusion.getMatch() != Ternary.UNDECIDED)) {
        // currentResult = progress.remove(element);
        // } else {
        // currentResult = progress.get(element);
        // }
        currentResult = progress.get(element);

        if (currentResult == null) {
            throw new IllegalStateException("Unbalanced start/finish calls.");
        }

        if (inclusion != null) {
            if (currentResult.include == null) {
                currentResult.include = FilterStartResult.from(inclusion, Ternary.TRUE);
            } else {
                currentResult.include = currentResult.include.withMatch(inclusion.getMatch());
            }
        }

        if (exclusion != null) {
            if (currentResult.exclude == null) {
                currentResult.exclude = FilterStartResult.from(exclusion, Ternary.TRUE);
            } else {
                currentResult.exclude = currentResult.exclude.withMatch(exclusion.getMatch());
            }
        }

        return FilterFinishResult.from(currentResult.compute());
    }

    @Override
    public Map<E, FilterFinishResult> finish() {
        Map<E, FilterFinishResult> finalIncludes = new HashMap<>();
        if (include != null) {
            finalIncludes.putAll(include.finish());
        }

        Map<E, FilterFinishResult> finalExcludes = new HashMap<>();
        if (exclude != null) {
            finalExcludes.putAll(exclude.finish());
        }

        Map<E, FilterFinishResult> ret = new HashMap<>();
        for (Map.Entry<E, IncludeExcludeResult> e : progress.entrySet()) {
            IncludeExcludeResult r = e.getValue();
            E el = e.getKey();

            FilterFinishResult im = finalIncludes.get(el);
            if (im != null) {
                r.include = FilterStartResult.from(im, Ternary.TRUE);
            }

            FilterFinishResult em = finalExcludes.get(el);
            if (em != null) {
                r.exclude = FilterStartResult.from(em, Ternary.TRUE);
            }

            ret.put(el, FilterFinishResult.from(r.compute()));
        }

        progress.clear();

        return ret;
    }

    protected static class IncludeExcludeResult {
        public @Nullable FilterStartResult include;
        public @Nullable FilterStartResult exclude;
        public @Nullable IncludeExcludeResult parent;

        public IncludeExcludeResult(@Nullable FilterStartResult include, @Nullable FilterStartResult exclude,
                @Nullable IncludeExcludeResult parent) {
            this.include = include;
            this.exclude = exclude;
            this.parent = parent;
        }

        FilterStartResult compute() {
            if (parent == null) {
                if (include == null) {
                    if (exclude == null) {
                        return FilterStartResult.defaultResult();
                    } else {
                        // exclude is never authoritative
                        return exclude.negateMatch().withInherited(true);
                    }
                } else {
                    if (exclude == null) {
                        return include;
                    } else {
                        return include.and(exclude.negateMatch());
                    }
                }
            } else {
                FilterStartResult parentResult = parent.compute();
                if (include == null) {
                    if (exclude == null) {
                        return inherit(parentResult);
                    } else {
                        return inherit(parentResult).and(exclude.negateMatch());
                    }
                } else {
                    if (exclude == null) {
                        // if a parent is included, all its children are included unless explicitly excluded
                        // if the parent is excluded, explicit include can override that
                        return inherit(parentResult).or(include);
                    } else {
                        // if parent is included, our include match cannot override it, just the excludes
                        // if parent is not included, our include can determine whether this element is included or not
                        if (parentResult.getMatch().toBoolean(true)) {
                            return inherit(parentResult).and(exclude.negateMatch());
                        } else {
                            return include.and(exclude.negateMatch());
                        }
                    }
                }
            }
        }
    }
}
