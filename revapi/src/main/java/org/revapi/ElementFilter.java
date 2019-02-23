/*
 * Copyright 2014-2019 Lukas Krejci
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

import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;
import org.revapi.query.Filter;
import org.revapi.simple.RepeatingTreeFilter;

/**
 * An element filter is a type of extension that can serve as an input filter on the element forest.
 *
 * <p>Once the {@link org.revapi.ElementForest} is produced by an {@link org.revapi.ArchiveAnalyzer}, the
 * registered element filters will be called to potentially leave out certain elements from the API analysis.
 *
 * <p>An example of this might be leaving out certain packages from the analysis of java archives.
 *
 * <p>The {@link #close()} is not called if there is no prior call to {@link #initialize(AnalysisContext)}. Do all your
 * resource acquisition in initialize, not during the construction of the object.
 *
 * @author Lukas Krejci
 * @since 0.1
 *
 * @deprecated use {@link TreeFilterProvider} instead
 */
@Deprecated
public interface ElementFilter extends TreeFilterProvider, Filter<Element>, AutoCloseable, Configurable {
    @Nullable
    @Override
    default TreeFilter filterFor(ArchiveAnalyzer archiveAnalyzer) {
        return new RepeatingTreeFilter() {
            @Override
            public FilterStartResult doStart(Element element) {
                boolean applies = applies(element);
                boolean descends = shouldDescendInto(element);

                FilterMatch res = FilterMatch.fromBoolean(applies);

                return FilterStartResult.direct(res, descends);
            }

            @Override
            public Map<Element, FilterFinishResult> finish() {
                return Collections.emptyMap();
            }
        };
    }
}
