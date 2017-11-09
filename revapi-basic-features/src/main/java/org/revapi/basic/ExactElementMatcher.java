package org.revapi.basic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.util.Optional;

import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;

/**
 * @author Lukas Krejci
 */
public final class ExactElementMatcher implements ElementMatcher {
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        return Optional.of(new StringMatch(recipe));
    }

    @Override
    public void close() throws Exception {

    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "matcher.exact";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {

    }

    private static final class StringMatch implements CompiledRecipe {
        final String match;

        private StringMatch(String match) {
            this.match = match;
        }

        @Override
        public FilterMatch test(Element element) {
            return FilterMatch.fromBoolean(match.equals(element.getFullHumanReadableString()));
        }
    }
}
