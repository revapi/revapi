/*
 * Copyright 2015 Lukas Krejci
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.revapi;

import org.revapi.configuration.Configurable;
import org.revapi.query.Filter;

/**
 * An element filter is a type of extension that can serve as an input filter on the element forest.
 *
 * <p>Once the {@link org.revapi.ElementForest} is produced by an {@link org.revapi.ArchiveAnalyzer}, the
 * registered element filters will be called to potentially leave out certain elements from the API analysis.
 *
 * <p>An example of this might be leaving out certain packages from the analysis of java archives.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface ElementFilter extends Filter<Element>, AutoCloseable, Configurable {
}
