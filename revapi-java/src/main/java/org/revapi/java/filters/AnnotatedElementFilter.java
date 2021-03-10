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

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * 
 * @since 0.5.1
 * 
 * @deprecated the generic filtering together with matcher.java can fulfil the same purpose as this
 */
@Deprecated
public final class AnnotatedElementFilter extends AbstractIncludeExcludeFilter {
    public AnnotatedElementFilter() {
        super("revapi.java.filter.annotated", "/META-INF/annotated-elem-filter-schema.json");
    }

    @Override
    protected boolean canBeReIncluded(JavaModelElement element) {
        return true;
    }

    @Override
    protected Stream<String> getTestedElementRepresentations(JavaModelElement element) {
        return element.getDeclaringElement().getAnnotationMirrors().stream().map(Util::toHumanReadableString);
    }

    @Override
    protected void validateConfiguration(boolean excludes, List<String> fullMatches, List<Pattern> patterns,
            boolean regexes) {
        // XXX probably we should do something here, but the check would be a little bit complex so let's not ;)
    }
}
