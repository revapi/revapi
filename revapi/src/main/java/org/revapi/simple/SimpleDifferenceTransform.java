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
package org.revapi.simple;

import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.Element;

/**
 * @author Lukas Krejci
 * 
 * @since 0.4.0
 * 
 * @deprecated use {@link org.revapi.base.BaseDifferenceTransform} instead
 */
@Deprecated
public abstract class SimpleDifferenceTransform<T extends Element<T>> extends SimpleConfigurable
        implements DifferenceTransform<T> {
    @Override
    public @Nonnull Pattern[] getDifferenceCodePatterns() {
        return new Pattern[0];
    }

    @Override
    public @Nullable Difference transform(@Nullable T oldElement, @Nullable T newElement,
            @Nonnull Difference difference) {
        return difference;
    }
}
