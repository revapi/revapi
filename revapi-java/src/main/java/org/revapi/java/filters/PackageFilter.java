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

import javax.annotation.Nullable;
import javax.lang.model.element.TypeElement;

import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.base.IndependentTreeFilter;
import org.revapi.base.OverridableIncludeExcludeTreeFilter;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;

/**
 * This is a solution to the removal of package and class filtering directly in the classpath scanner.
 * We need something that people will be able to use until this functionality is removed for good and only the variant
 * with revapi.filter and the java matcher are available.
 *
 * @deprecated This is deprecated because it is a temporary measure
 */
@Deprecated
public class PackageFilter extends OverridableIncludeExcludeTreeFilter<JavaElement> {
    public PackageFilter(Pattern[] includes, Pattern[] excludes) {
        super(asFilter(includes), asFilter(excludes));
    }

    private static @Nullable TreeFilter<JavaElement> asFilter(Pattern[] patterns) {
        return ClassFilter.asFilter(patterns, PackageFilter::asFilter);
    }

    private static TreeFilter<JavaElement> asFilter(Pattern pattern) {
        return new IndependentTreeFilter<JavaElement>() {
            @Override
            protected FilterStartResult doStart(JavaElement element) {
                String pkg = findPackage(element);
                if (pkg == null) {
                    return FilterStartResult.doesntMatch();
                }

                Ternary match = Ternary.fromBoolean(pattern.matcher(pkg).matches());
                // undecided about the descend so that other filters can make a decisive decision and at the same time
                // we don't force the descend
                return FilterStartResult.direct(match, Ternary.UNDECIDED);
            }
        };
    }


    private static String findPackage(JavaElement el) {
        while (el != null && !(el instanceof JavaTypeElement)) {
            el = el.getParent();
        }

        TypeElement type = el == null ? null : ((JavaTypeElement) el).getDeclaringElement();

        return el == null
                ? null
                : el.getTypeEnvironment().getElementUtils().getPackageOf(type).getQualifiedName().toString();
    }
}
