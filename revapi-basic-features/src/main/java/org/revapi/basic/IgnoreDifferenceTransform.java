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

package org.revapi.basic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.Element;

/**
 * A generic difference transform that can ignore differences based on the difference code ({@link
 * org.revapi.Difference#code}) and on the old or new elements' full human representations
 * ({@link org.revapi.Element#getFullHumanReadableString()}).
 * <p/>
 * The transform is configured using properties in the general form of:
 * <pre><code>
 * revapi.ignore.1.regex=false
 * revapi.ignore.1.code=PROBLEM_CODE;
 * revapi.ignore.1.old=FULL_REPRESENTATION_OF_THE_OLD_ELEMENT
 * revapi.ignore.1.new=FULL_REPRESENTATION_OF_THE_NEW_ELEMENT
 * revapi.ignore.2.code=PROBLEM_CODE
 * ...
 * </code></pre>
 * The {@code code} is mandatory (obviously). The {@code old} and {@code new} properties are optional and the rule will
 * match when all the specified properties of it match. If regex attribute is "true" (defaults to "false"), all the
 * code, old and new are understood as regexes.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class IgnoreDifferenceTransform
    extends AbstractDifferenceReferringTransform<IgnoreDifferenceTransform.IgnoreRecipe, Void> {
    public static final String CONFIG_PROPERTY_PREFIX = "revapi.ignore";

    public static class IgnoreRecipe extends DifferenceMatchRecipe {
        @Override
        public Difference transformMatching(Difference difference, Element oldElement,
            Element newElement) {

            //we ignore the matching elements, so null is the correct return value.
            return null;
        }
    }

    public IgnoreDifferenceTransform() {
        super(CONFIG_PROPERTY_PREFIX);
    }

    @Nullable
    @Override
    protected Void initConfiguration() {
        return null;
    }

    @Nonnull
    @Override
    protected IgnoreRecipe newRecipe(Void context) {
        return new IgnoreRecipe();
    }

    @Override
    public void close() {
    }
}
