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

import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;

/**
 * A difference transform may elect to transform certain kinds of differences into other kinds. This comes useful
 * in custom extensions that want to "modify the behavior" of other extensions by consuming and transforming the
 * differences found by the other extensions into something else.
 *
 * <p>The {@link #close()} is not called if there is no prior call to {@link #initialize(AnalysisContext)}. Do all your
 * resource acquisition in initialize, not during the construction of the object.
 *
 * @param <T> the type of the element expected in the {@code transform} method. Note that you need to be careful about
 *            this type because the types of the elements passed to {@code transform} depend on the differences that the
 *            transform is interested in. Thus you may end up with {@code ClassCastException}s if you're not careful.
 *            This type needs to be cast-able to the type of all possible elements that the handled differences can
 *            apply to. If in doubt, just use {@link org.revapi.Element} which is guaranteed to work.
 * @author Lukas Krejci
 * @since 0.1
 */
public interface DifferenceTransform<T extends Element> extends AutoCloseable, Configurable {

    /**
     * @return The list of regexes to match the difference codes this transform can handle.
     */
    @Nonnull
    Pattern[] getDifferenceCodePatterns();

    /**
     * Returns a transformed version of the difference. If this method returns null, the difference is
     * discarded and not reported. Therefore, if you don't want to transform a difference, just return it.
     *
     * <p>The code of the supplied difference will match at least one of the regexes returned from the {@link
     * #getDifferenceCodePatterns()} method.
     *
     * @param oldElement the old differing element
     * @param newElement the new differing element
     * @param difference the difference description
     *
     * @return the transformed difference or the passed in difference if no transformation necessary or null if the
     * difference should be discarded
     */
    @Nullable
    Difference transform(@Nullable T oldElement, @Nullable T newElement, @Nonnull Difference difference);

    /**
     * Some difference transforms may need to initialize themselves first through a walk of the element forests before
     * they can start transforming. If they need to do so, this method needs to return a visitor that will be walked
     * through the forest before the transformations will start.
     *
     * @return null if no need to initialize or a visitor instance used for the transform initialization
     */
    @Nullable
    default ElementForest.Visitor getStructuralInitializer() {
        return null;
    }
}
