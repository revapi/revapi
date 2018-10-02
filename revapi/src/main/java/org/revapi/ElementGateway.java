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
package org.revapi;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;
import org.revapi.query.Filter;

/**
 * An element gateway lets the elements through to the next stage of the analysis pipeline.
 *
 * @author Lukas Krejci
 *
 * @deprecated use {@link FilterProvider} instead
 */
@Deprecated
public interface ElementGateway extends Configurable, AutoCloseable {

    default Filter<Element> asFilter() {
        return new Filter<Element>() {
            private final WeakHashMap<Element, FilterResult> results = new WeakHashMap<>();

            @Override
            public boolean applies(@Nullable Element element) {
                return results.computeIfAbsent(element, e -> filter(AnalysisStage.FOREST_COMPLETE, e)).getMatch() ==
                        FilterMatch.MATCHES;
            }

            @Override
            public boolean shouldDescendInto(@Nullable Object element) {
                if (!(element instanceof Element)) {
                    return false;
                }

                Element e = (Element) element;

                return results.computeIfAbsent(e, ee -> filter(AnalysisStage.FOREST_COMPLETE, ee)).isDescend();
            }
        };
    }

    /**
     * Called before given analysis pipeline stage starts.
     */
    void start(AnalysisStage stage);

    /**
     * Tells the analysis pipeline whether to let the provided element further to the next stage or whether to leave it
     * out. When the {@link FilterMatch#UNDECIDED} is returned, the element will be checked again at the end of the same
     * stage until there are no undecided elements left. Only then is the {@link #end(AnalysisStage)} method called.
     * <p>
     * <p>Implementors therefore should take care not to end up in an infinite loop when returning
     * {@link FilterMatch#UNDECIDED} unconditionally.
     *
     * @param stage   the current analysis pipeline stage
     * @param element the element to be decided about
     * @return whether to let the element pass to the next stage or not or {@link FilterMatch#UNDECIDED} if the decision
     * should be deferred until all other elements have been processed in the given stage.
     *
     * @deprecated - we're gonna mimic what Classif does
     */
    @Deprecated
    FilterResult filter(AnalysisStage stage, Element element);

    default FilterResult start(Element element) {
        // TODO implement
        return FilterResult.doesntMatchAndDescend();
    }

    default FilterMatch finish(Element element) {
        // TODO implement
        return FilterMatch.DOESNT_MATCH;
    }

    default Map<Element, FilterMatch> finish() {
        return Collections.emptyMap();
    }

    /**
     * Called when given pipeline stage finished.
     */
    void end(AnalysisStage stage);

    enum AnalysisStage {
        /**
         * The element forest is currently being constructed. This is when most of the {@link FilterMatch#UNDECIDED}
         * checks will happen.
         */
        FOREST_INCOMPLETE,

        /**
         * The element forest has been fully constructed. The gateway will check all the {@link FilterMatch#UNDECIDED}
         * elements.
         */
        FOREST_COMPLETE
    }
}
