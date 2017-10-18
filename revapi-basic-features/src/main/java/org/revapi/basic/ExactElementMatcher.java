package org.revapi.basic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;

import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;
import org.revapi.FilterMatch;

/**
 * @author Lukas Krejci
 */
public final class ExactElementMatcher implements ElementMatcher {
    @Override
    public FilterMatch test(String recipe, Element element) {
        return FilterMatch.fromBoolean(recipe.equals(element.getFullHumanReadableString()));
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
}
