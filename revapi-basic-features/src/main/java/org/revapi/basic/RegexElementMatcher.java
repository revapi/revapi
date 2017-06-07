package org.revapi.basic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.revapi.AnalysisContext;
import org.revapi.Element;
import org.revapi.ElementMatcher;

/**
 * @author Lukas Krejci
 */
public final class RegexElementMatcher implements ElementMatcher {
    private final Map<String, Pattern> patternCache = new HashMap<>();

    @Override
    public boolean matches(String recipe, Element element) {
        Pattern pattern = patternCache.computeIfAbsent(recipe, __ -> Pattern.compile(recipe));
        return pattern.matcher(element.getFullHumanReadableString()).matches();
    }

    @Override
    public void close() throws Exception {
        patternCache.clear();
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
        patternCache.clear();
    }
}
