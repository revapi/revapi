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

import static java.util.stream.Collectors.toList;

import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.base.IndependentTreeFilter;
import org.revapi.base.OverridableIncludeExcludeTreeFilter;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * This is a solution to the removal of package and class filtering directly in the classpath scanner. We need something
 * that people will be able to use until this functionality is removed for good and only the variant with revapi.filter
 * and the java matcher are available.
 *
 * @deprecated This is deprecated because it is a temporary measure
 */
@Deprecated
public class ClassFilter extends OverridableIncludeExcludeTreeFilter<JavaElement> {
    public ClassFilter(Pattern[] includes, Pattern[] excludes) {
        super(asFilter(includes), asFilter(excludes));
    }

    private static @Nullable TreeFilter<JavaElement> asFilter(Pattern[] patterns) {
        return asFilter(patterns, ClassFilter::asFilter);
    }

    // package private so that we can reuse this in the PackageFilter
    static @Nullable TreeFilter<JavaElement> asFilter(Pattern[] patterns,
            Function<Pattern, TreeFilter<JavaElement>> toFilter) {
        if (patterns == null || patterns.length == 0) {
            return null;
        }

        return TreeFilter.union(Stream.of(patterns).map(toFilter).collect(toList()));
    }

    private static TreeFilter<JavaElement> asFilter(Pattern pattern) {
        return new IndependentTreeFilter<JavaElement>() {
            @Override
            protected FilterStartResult doStart(JavaElement element) {
                JavaTypeElement typeEl = findType(element);
                String el = typeEl == null ? "" : Util.toHumanReadableString(typeEl.getDeclaringElement());

                Ternary match = Ternary.fromBoolean(pattern.matcher(el).matches());
                // undecided about the descend so that other filters can make a decisive decision and at the same time
                // we don't force the descend
                return FilterStartResult.direct(match, Ternary.UNDECIDED);
            }
        };
    }

    private static JavaTypeElement findType(JavaElement el) {
        while (el != null && !(el instanceof JavaTypeElement)) {
            el = el.getParent();
        }

        return el == null ? null : (JavaTypeElement) el;
    }
}
