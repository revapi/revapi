package org.revapi.java.transforms;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Reader;
import java.util.regex.Pattern;

import org.revapi.AnalysisContext;
import org.revapi.Difference;
import org.revapi.DifferenceTransform;
import org.revapi.java.spi.JavaModelElement;

/**
 * @author Lukas Krejci
 */
public final class DeprecatedElement implements DifferenceTransform<JavaModelElement> {
    @Nonnull
    @Override
    public Pattern[] getDifferenceCodePatterns() {
        return new Pattern[0];
    }

    @Nullable
    @Override
    public Difference transform(@Nullable JavaModelElement oldElement, @Nullable JavaModelElement newElement, @Nonnull Difference difference) {
        return null;
    }

    @Override
    public void close() throws Exception {

    }

    @Nullable
    @Override
    public String getExtensionId() {
        return "deprecated-elements-reporting";
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
