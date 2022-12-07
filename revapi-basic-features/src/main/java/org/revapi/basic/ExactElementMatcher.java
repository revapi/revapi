/*
 * Copyright 2014-2022 Lukas Krejci
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

import javax.annotation.Nonnull;

import org.revapi.AnalysisContext;
import org.revapi.ArchiveAnalyzer;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterStartResult;
import org.revapi.Ternary;
import org.revapi.TreeFilter;
import org.revapi.base.IndependentTreeFilter;

/**
 * @author Lukas Krejci
 */
public final class ExactElementMatcher implements ElementMatcher {
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        return Optional.of(new CompiledRecipe() {
            @Override
            public <E extends Element<E>> TreeFilter<E> filterFor(ArchiveAnalyzer<E> archiveAnalyzer) {
                return new StringMatch<>(recipe);
            }
        });
    }

    @Override
    public void close() throws Exception {

    }

    @Override
    public String getMatcherId() {
        return "exact";
    }

    @Override
    public String getExtensionId() {
        return "revapi.matcher.exact";
    }

    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {

    }

    private static final class StringMatch<E extends Element<E>> extends IndependentTreeFilter<E> {
        final String match;

        private StringMatch(String match) {
            this.match = match;
        }

        @Override
        protected FilterStartResult doStart(E element) {
            boolean m = match.equals(element.getFullHumanReadableString());
            Ternary res = Ternary.fromBoolean(m);
            return FilterStartResult.direct(res, res);
        }
    }
}
