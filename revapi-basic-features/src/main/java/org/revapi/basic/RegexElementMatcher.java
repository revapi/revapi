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
package org.revapi.basic;

import java.io.Reader;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;
import org.revapi.FilterStartResult;
import org.revapi.TreeFilter;
import org.revapi.base.IndependentTreeFilter;

/**
 * @author Lukas Krejci
 */
public final class RegexElementMatcher implements ElementMatcher {
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        try {
            return Optional.of(new CompiledRecipe() {
                @Override
                public <E extends Element<E>> TreeFilter<E> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
                    return new PatternMatch<>(Pattern.compile(recipe));
                }
            });
        } catch (PatternSyntaxException __) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Override
    public String getExtensionId() {
        return "regex";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
    }

    private static final class PatternMatch<E extends Element<E>> extends IndependentTreeFilter<E> {
        final Pattern match;

        private PatternMatch(Pattern match) {
            this.match = match;
        }

        @Override
        protected FilterStartResult doStart(E element) {
            boolean m = match.matcher(element.getFullHumanReadableString()).matches();
            return FilterStartResult.direct(FilterMatch.fromBoolean(m), m);
        }
    }
}
