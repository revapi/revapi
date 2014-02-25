/*
 * Copyright 2014 Lukas Krejci
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
 * limitations under the License
 */

package org.revapi.java.model;

import javax.annotation.Nonnull;

import org.revapi.Element;
import org.revapi.java.compilation.ProbingEnvironment;

/**
 * TODO incorporate this into ClassTreeInitializer, Util.* and, element analyzer, etc.
 *
 * @author Lukas Krejci
 * @since 0.1
 */
public class PackageElement extends JavaElementBase<javax.lang.model.element.PackageElement> {

    public PackageElement(ProbingEnvironment env,
        javax.lang.model.element.PackageElement element) {
        super(env, element);
    }

    @Nonnull
    @Override
    protected String getHumanReadableElementType() {
        return "package";
    }

    @Override
    public int compareTo(@Nonnull Element o) {
        //TODO implement
        return 0;
    }
}
