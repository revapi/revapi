/*
 * Copyright 2016 Lukas Krejci
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
 *
 */
package org.revapi.java.filters;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.revapi.java.spi.JavaAnnotationElement;
import org.revapi.java.spi.JavaMethodElement;
import org.revapi.java.spi.JavaModelElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.Util;

/**
 * @author Lukas Krejci
 * @since 0.5.1
 */
public final class AnnotatedElementFilter extends AbstractIncludeExcludeFilter {
    public AnnotatedElementFilter() {
        super("revapi.java.filter.annotated", "/META-INF/annotated-elem-filter-schema.json");
    }

    @Override
    public boolean shouldDescendInto(@Nullable Object element) {
        return doNothing || (element instanceof JavaTypeElement || element instanceof JavaMethodElement);
    }

    @Override
    protected boolean canBeReIncluded(JavaModelElement element) {
        return true;
    }

    @Override
    protected Stream<String> getTestedElementRepresentations(JavaModelElement element) {
        return element.getModelElement().getAnnotationMirrors().stream().map(Util::toHumanReadableString);
    }

    @Override
    protected boolean decideAnnotation(JavaAnnotationElement annotation,
            AbstractIncludeExcludeFilter.InclusionState parentInclusionState) {
        //annotations cannot be annotated, so include this only if there is no explicit inclusion filter
        return parentInclusionState.toBoolean() && includeTest == null;
    }

    @Override
    protected void validateConfiguration(boolean excludes, List<String> fullMatches, List<Pattern> patterns,
            boolean regexes) {
        //XXX probably we should do something here, but the check would be a little bit complex so let's not ;)
    }
}
