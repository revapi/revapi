package org.revapi.basic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;

/**
 * @author Lukas Krejci
 */
public final class RegexElementMatcher implements ElementMatcher {
    @Override
    public Optional<CompiledRecipe> compile(String recipe) {
        try {
            return Optional.of(new PatternMatch(Pattern.compile(recipe)));
        } catch (PatternSyntaxException __) {
            return Optional.empty();
        }
    }

    @Override
    public void close() throws Exception {
    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "matcher.regex";
    }

    @Nullable
    @Override
    public Reader getJSONSchema() {
        return null;
    }

    @Override
    public void initialize(@Nonnull AnalysisContext analysisContext) {
    }

    private static final class PatternMatch implements CompiledRecipe {
        final Pattern match;

        private PatternMatch(Pattern match) {
            this.match = match;
        }

        @Override
        public FilterMatch test(Element element) {
            return FilterMatch.fromBoolean(match.matcher(element.getFullHumanReadableString()).matches());
        }
    }
}
