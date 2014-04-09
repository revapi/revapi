/*
 * Copyright 2014 Lukas Krejci
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

import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.configuration.Configurable;

/**
 * A difference transform may elect to transform certain kinds of differences into other kinds. This comes useful
 * in custom extensions that want to "modify the behavior" of other extensions by consuming and transforming the
 * differences found by the other extensions into something else.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public interface DifferenceTransform extends AutoCloseable, Configurable {

    /**
     * The list of regexes to match the difference codes this transform can handle.
     */
    @Nonnull
    Pattern[] getDifferenceCodePatterns();

    /**
     * Returns a transformed version of the difference. If this method returns null, the difference is
     * discarded and not reported. Therefore, if you don't want to transform a difference, just return it.
     * <p/>
     * The code of the supplied difference will match at least one of the regexes returned from the {@link
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
    Difference transform(@Nullable Element oldElement, @Nullable Element newElement,
        @Nonnull Difference difference);
}
