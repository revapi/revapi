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
package org.revapi.java.filters;

import java.util.regex.Pattern;

import org.revapi.FilterMatch;
import org.revapi.FilterStartResult;
import org.revapi.base.IndependentTreeFilter;
import org.revapi.java.spi.JavaElement;

/**
 *
 * @deprecated This is a temporary measure for a couple of releases until we can definitely remove it in favor of
 * {@code revapi.filter} and java matcher.
 */
@Deprecated
abstract class IncludeExcludeFilter extends IndependentTreeFilter<JavaElement> {
    private final Pattern[] includes;
    private final Pattern[] excludes;

    protected IncludeExcludeFilter(Pattern[] includes, Pattern[] excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    @Override
    protected FilterStartResult doStart(JavaElement element) {
        if (includes.length == 0 && excludes.length == 0) {
            return FilterStartResult.matchAndDescend();
        }

        String str = getMatchableRepresentation(element);

        boolean matches = includes(str) && !excludes(str);

        return FilterStartResult.direct(FilterMatch.fromBoolean(matches), matches);
    }

    protected abstract String getMatchableRepresentation(JavaElement el);

    private boolean includes(String str) {
        if (includes.length == 0) {
            return true;
        } else {
            for (Pattern p : includes) {
                if (p.matcher(str).matches()) {
                    return true;
                }
            }

            return false;
        }
    }

    private boolean excludes(String str) {
        if (excludes.length != 0) {
            for (Pattern p : excludes) {
                if (p.matcher(str).matches()) {
                    return true;
                }
            }
        }
        return false;
    }
}
